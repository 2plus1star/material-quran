#!/usr/bin/env python3
"""Regenerate docs/ from the repository's own PRIVACY.md.

Run after editing PRIVACY.md, then push the docs/ output to the gh-pages
branch. The published policy is generated from the file in this repo rather
than maintained separately, because the two drifted once already: the app
stopped using Google Play services' fused location provider while the
published page still described it. A privacy policy that misdescribes the
app is a Play "Misrepresentation" problem, not a typo.

    python3 tools/build_site.py

Requires: pip install markdown
"""
import pathlib
import subprocess
import sys

try:
    import markdown
except ImportError:
    sys.exit("pip install markdown")

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body { margin:0 auto; padding:2.5rem 1.25rem 5rem; max-width:46rem;
  font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
  color:#1b1b1b; background:#fff; }
@media (prefers-color-scheme: dark){ body{color:#e6e6e6;background:#121212;} a{color:#7fd8cf;} }
h1{font-size:1.9rem;line-height:1.2;margin:0 0 .4rem;}
h2{font-size:1.25rem;margin:2.2rem 0 .6rem;}
h3{font-size:1.05rem;margin:1.6rem 0 .4rem;}
a{color:#00696b;}
hr{border:0;border-top:1px solid #8883;margin:2rem 0;}
table{border-collapse:collapse;width:100%;margin:1rem 0;}
th,td{border:1px solid #8884;padding:.5rem .6rem;text-align:left;vertical-align:top;}
code{background:#8881;padding:.1rem .3rem;border-radius:3px;font-size:.9em;}
img{max-width:100%;height:auto;border-radius:12px;}
footer{margin-top:3rem;font-size:.85rem;opacity:.7;}
"""


def page(md_text, title):
    body = markdown.markdown(md_text, extensions=["tables", "fenced_code", "sane_lists"])
    return (
        '<!DOCTYPE html>\n<html lang="en"><head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        f"<title>{title}</title>\n<style>{CSS}</style>\n</head><body>\n{body}\n"
        "<footer>Published by threestar. Contact: twoplusonestar@gmail.com</footer>\n"
        "</body></html>\n"
    )


def main():
    name = (ROOT / "README.md").read_text().splitlines()[0].lstrip("# ").strip()

    privacy = (ROOT / "PRIVACY.md").read_text()
    (DOCS / "privacy").mkdir(parents=True, exist_ok=True)
    (DOCS / "privacy" / "index.html").write_text(page(privacy, f"Privacy Policy — {name}"))

    landing = (
        f"# {name}\n\n"
        "- [Privacy policy](privacy/)\n"
        f"- [Source code](https://github.com/2plus1star/{ROOT.name})\n"
        "- Support: twoplusonestar@gmail.com\n\n"
        "Published by threestar.\n"
    )
    (DOCS / "index.html").write_text(page(landing, name))
    (DOCS / ".nojekyll").touch()

    # Guard: the published page must not describe behaviour the app no longer has.
    stale = [s for s in ("fused location provider",) if s in privacy]
    if stale:
        print(f"  warning: PRIVACY.md still mentions {stale}", file=sys.stderr)

    rev = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
                         capture_output=True, text=True).stdout.strip()
    print(f"  built docs/ from PRIVACY.md at {rev or 'working tree'}")


if __name__ == "__main__":
    main()
