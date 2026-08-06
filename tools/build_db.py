#!/usr/bin/env python3
"""Builds app/src/main/assets/quran.db from the sources in tools/data/.

Arabic text: Tanzil Project (tanzil.net), CC BY 3.0 — shipped verbatim, as its
licence requires ("changing it is not allowed"). The copyright blocks are copied
into app/src/main/assets/NOTICE.txt because read_pipe() strips them here.

Translation: QuranEnc.com `english_saheeh` — the Saheeh translation as issued by
Noor International Center. This replaced Tanzil's en.sahih dump, which carried NO
licence and which Tanzil itself restricts: "The translations provided at this
page are for non-commercial purposes only. If used otherwise, you need to obtain
necessary permission from the translator or the publisher." QuranEnc publishes
terms that do permit republication, conditional on the text being unmodified and
the publisher and source being credited — which is why the footnote markers
("[2]") are kept inline rather than stripped, and the footnotes themselves are
carried in their own column.

The app reads this database with raw SQLiteDatabase, not Room; PRAGMA
user_version is what QuranDb checks, so bump it whenever this file changes.
"""
import re
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).parent
DATA = HERE / "data"
OUT = HERE.parent / "app" / "src" / "main" / "assets" / "quran.db"
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.unlink(missing_ok=True)

LINE = re.compile(r"^(\d+)\|(\d+)\|(.+)$")

def read_pipe(path):
    out = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        m = LINE.match(raw.strip())
        if m:
            out[(int(m.group(1)), int(m.group(2)))] = m.group(3)
    return out

text_min = read_pipe(DATA / "quran-uthmani-min.txt")
text_full = read_pipe(DATA / "quran-uthmani.txt")
trans = read_pipe(DATA / "en.quranenc-saheeh.txt")
notes = read_pipe(DATA / "en.quranenc-saheeh-footnotes.txt")
assert len(text_min) == 6236 and len(text_full) == 6236 and len(trans) == 6236, (
    len(text_min), len(text_full), len(trans))
print(f"footnotes: {len(notes)}")

meta = ET.parse(DATA / "quran-data.xml").getroot()

suras = []
for s in meta.find("suras").findall("sura"):
    suras.append({
        "id": int(s.get("index")),
        "ayas": int(s.get("ayas")),
        "start": int(s.get("start")),  # cumulative ayahs before this surah
        "name_ar": s.get("name"),
        "tname": s.get("tname"),
        "ename": s.get("ename"),
        "type": s.get("type"),  # Meccan / Medinan
    })
assert len(suras) == 114

def boundaries(tag, inner):
    pts = []
    for el in meta.find(tag).findall(inner):
        pts.append((int(el.get("sura")), int(el.get("aya")), int(el.get("index"))))
    return pts

juz_starts = boundaries("juzs", "juz")            # 30
quarter_starts = boundaries("hizbs", "quarter")   # 240
page_starts = boundaries("pages", "page")         # 604
assert len(juz_starts) == 30 and len(quarter_starts) == 240 and len(page_starts) == 604

sajdas = set()
for el in meta.find("sajdas").findall("sajda"):
    sajdas.add((int(el.get("sura")), int(el.get("aya"))))
assert len(sajdas) == 15

# Global ayah ordering
keys = []
for s in suras:
    for a in range(1, s["ayas"] + 1):
        keys.append((s["id"], a))
assert len(keys) == 6236
key_to_gid = {k: i + 1 for i, k in enumerate(keys)}

def assign(starts):
    """key -> 1-based bucket index for sorted boundary starts."""
    marks = sorted((key_to_gid[(su, ay)], idx) for su, ay, idx in starts)
    out, cur, mi = {}, 0, 0
    for gid in range(1, 6237):
        while mi < len(marks) and marks[mi][0] == gid:
            cur = marks[mi][1]
            mi += 1
        out[gid] = cur
    return out

juz_of = assign(juz_starts)
quarter_of = assign(quarter_starts)
page_of = assign(page_starts)

db = sqlite3.connect(OUT)
c = db.cursor()
# Room-canonical DDL (INTEGER/TEXT NOT NULL, PRIMARY KEY(`id`)).
c.execute(
    "CREATE TABLE `surah` (`id` INTEGER NOT NULL, `nameAr` TEXT NOT NULL, "
    "`tname` TEXT NOT NULL, `ename` TEXT NOT NULL, `revelation` TEXT NOT NULL, "
    "`ayahCount` INTEGER NOT NULL, `startAyahId` INTEGER NOT NULL, "
    "`startPage` INTEGER NOT NULL, PRIMARY KEY(`id`))"
)
c.execute(
    "CREATE TABLE `ayah` (`id` INTEGER NOT NULL, `surah` INTEGER NOT NULL, "
    "`num` INTEGER NOT NULL, `text` TEXT NOT NULL, `textFull` TEXT NOT NULL, "
    "`translation` TEXT NOT NULL, `footnotes` TEXT NOT NULL, "
    "`page` INTEGER NOT NULL, `juz` INTEGER NOT NULL, "
    "`hizb` INTEGER NOT NULL, `quarter` INTEGER NOT NULL, `sajdah` INTEGER NOT NULL, "
    "PRIMARY KEY(`id`))"
)
c.execute("CREATE INDEX `index_ayah_surah_num` ON `ayah` (`surah`, `num`)")
c.execute("CREATE INDEX `index_ayah_page` ON `ayah` (`page`)")
c.execute("CREATE INDEX `index_ayah_juz` ON `ayah` (`juz`)")
c.execute("CREATE INDEX `index_ayah_hizb` ON `ayah` (`hizb`)")

for s in suras:
    first_gid = key_to_gid[(s["id"], 1)]
    c.execute(
        "INSERT INTO surah VALUES (?,?,?,?,?,?,?,?)",
        (s["id"], s["name_ar"], s["tname"], s["ename"], s["type"],
         s["ayas"], first_gid, page_of[first_gid]),
    )

for (su, ay), gid in key_to_gid.items():
    q = quarter_of[gid]
    c.execute(
        "INSERT INTO ayah VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        (gid, su, ay, text_min[(su, ay)], text_full[(su, ay)], trans[(su, ay)],
         notes.get((su, ay), ""),
         page_of[gid], juz_of[gid], (q - 1) // 4 + 1, q,
         1 if (su, ay) in sajdas else 0),
    )

db.commit()

# --- Tajweed (cpfair/quran-tajweed, CC-BY 4.0): spans over its PINNED Tanzil
# Uthmani snapshot, stored as a separate display text used only in tajweed mode.
import json
taj_text = read_pipe(DATA / "quran-uthmani-tajweed-base.txt")
taj = json.loads((DATA / "tajweed.json").read_text(encoding="utf-8"))
assert len(taj_text) == 6236 and len(taj) == 6236

RULES = [
    "ghunnah", "idghaam_ghunnah", "idghaam_no_ghunnah", "idghaam_mutajaanisain",
    "idghaam_mutaqaaribain", "idghaam_shafawi", "ikhfa", "ikhfa_shafawi", "iqlab",
    "madd_2", "madd_246", "madd_muttasil", "madd_munfasil", "madd_6", "qalqalah",
    "hamzat_wasl", "lam_shamsiyyah", "silent",
]
rule_id = {r: i for i, r in enumerate(RULES)}

c.execute(
    "CREATE TABLE `tajweed_text` (`ayahId` INTEGER NOT NULL, `text` TEXT NOT NULL, "
    "PRIMARY KEY(`ayahId`))"
)
c.execute(
    "CREATE TABLE `tajweed_span` (`ayahId` INTEGER NOT NULL, `rule` INTEGER NOT NULL, "
    "`start` INTEGER NOT NULL, `end` INTEGER NOT NULL)"
)
c.execute("CREATE INDEX `index_tajweed_span_ayah` ON `tajweed_span` (`ayahId`)")

bad = 0
for entry in taj:
    key = (entry["surah"], entry["ayah"])
    gid = key_to_gid[key]
    text = taj_text[key]
    c.execute("INSERT INTO tajweed_text VALUES (?,?)", (gid, text))
    # Offsets are codepoint indices into this exact text; all chars are BMP so
    # they match UTF-16/Kotlin indices. Validate bounds defensively.
    n = len(text)
    for a in entry["annotations"]:
        s, e = a["start"], a["end"]
        r = rule_id.get(a["rule"])
        if r is None or not (0 <= s < e <= n):
            bad += 1
            continue
        c.execute("INSERT INTO tajweed_span VALUES (?,?,?,?)", (gid, r, s, e))
print("tajweed spans skipped:", bad)

db.commit()
c.execute("PRAGMA user_version = 2")
db.commit()
c.execute("VACUUM")
db.commit()
db.close()

size = OUT.stat().st_size / 1024 / 1024
print(f"OK {OUT} — {size:.1f} MB")
