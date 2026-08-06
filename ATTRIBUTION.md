# Attribution

Every asset bundled in the APK/AAB, with its source and licence. This file is
the evidence trail if a licence is ever questioned. The same text ships inside
the app at `app/src/main/assets/NOTICE.txt` and is shown under Settings >
Licences and notices, which is what Tanzil's terms and the OFL actually require.

```
NOTICES — Material Quran
========================================================================

QURAN TEXT
------------------------------------------------------------------------
The Arabic text is redistributed verbatim. Tanzil's terms require these
copyright blocks to travel with it; build_db.py strips them when parsing,
so they are reproduced here in full.

# PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK
#====================================================================
#
#  Tanzil Quran Text (Uthmani, Version 1.1)
#  Copyright (C) 2007-2026 Tanzil Project
#  License: Creative Commons Attribution 3.0
#
#  This copy of the Quran text is carefully produced, highly 
#  verified and continuously monitored by a group of specialists 
#  at Tanzil Project.
#
#  TERMS OF USE:
#
#  - Permission is granted to copy and distribute verbatim copies 
#    of this text, but CHANGING IT IS NOT ALLOWED.
#
#  - This Quran text can be used in any website or application, 
#    provided that its source (Tanzil Project) is clearly indicated, 
#    and a link is made to tanzil.net to enable users to keep
#    track of changes.
#
#  - This copyright notice shall be included in all verbatim copies 
#    of the text, and shall be reproduced appropriately in all files 
#    derived from or containing substantial portion of this text.
#
#  Please check updates at: http://tanzil.net/updates/
#
#====================================================================

# PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK
#====================================================================
#
#  Tanzil Quran Text (Uthmani Minimal, Version 1.1)
#  Copyright (C) 2007-2026 Tanzil Project
#  License: Creative Commons Attribution 3.0
#
#  This copy of the Quran text is carefully produced, highly 
#  verified and continuously monitored by a group of specialists 
#  at Tanzil Project.
#
#  TERMS OF USE:
#
#  - Permission is granted to copy and distribute verbatim copies 
#    of this text, but CHANGING IT IS NOT ALLOWED.
#
#  - This Quran text can be used in any website or application, 
#    provided that its source (Tanzil Project) is clearly indicated, 
#    and a link is made to tanzil.net to enable users to keep
#    track of changes.
#
#  - This copyright notice shall be included in all verbatim copies 
#    of the text, and shall be reproduced appropriately in all files 
#    derived from or containing substantial portion of this text.
#
#  Please check updates at: http://tanzil.net/updates/
#
#====================================================================

Tajweed base text:
# PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK
#====================================================================
#
#  Tanzil Quran Text (Uthmani, version 1.0.2)
#  Copyright (C) 2008-2010 Tanzil.net
#  License: Creative Commons Attribution 3.0
#
#  This copy of quran text is carefully produced, highly 
#  verified and continuously monitored by a group of specialists 
#  at Tanzil project.
#
#  TERMS OF USE:
#
#  - Permission is granted to copy and distribute verbatim copies 
#    of this text, but CHANGING IT IS NOT ALLOWED.
#
#  - This quran text can be used in any website or application, 
#    provided its source (Tanzil.net) is clearly indicated, and 
#    a link is made to http://tanzil.net to enable users to keep
#    track of changes.
#
#  - This copyright notice shall be included in all verbatim copies 
#    of the text, and shall be reproduced appropriately in all files 
#    derived from or containing substantial portion of this text.
#
#  Please check updates at: http://tanzil.net/updates/
# 
#====================================================================

Source: Tanzil Project — https://tanzil.net

TRANSLATION
------------------------------------------------------------------------
English Translation — Noor International Center (Saheeh).
Source: QuranEnc.com, the Noble Qur'an Encyclopedia — https://quranenc.com
Republished unmodified under QuranEnc.com's terms. Footnote markers such as
"[2]" are part of the published text and are deliberately preserved; the
footnotes themselves are shown beneath each verse.

TAJWEED ANNOTATIONS
------------------------------------------------------------------------
quran-tajweed by cpfair — https://github.com/cpfair/quran-tajweed
Licensed under CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/
Provided without warranties.
Modified: spans re-indexed onto the pinned Tanzil Uthmani text, rule names
mapped to integers, spans failing validation dropped, and offsets re-based
where a leading Basmala is drawn as a heading.

FONTS
------------------------------------------------------------------------
Noto Sans Arabic — Copyright 2022 The Noto Project Authors
  (https://github.com/notofonts/arabic) — SIL Open Font License 1.1.
  Noto is a trademark of Google LLC.
Baloo Bhaijaan 2 — Copyright 2019 The Baloo 2 Project Authors
  (https://github.com/EkType/Baloo2), Ek Type — SIL Open Font License 1.1.
Full licence text: https://openfontlicense.org/

RECITATIONS
------------------------------------------------------------------------
Recitation audio is not included in this app. It is downloaded at your
request from everyayah.com, a third party we do not operate. Each recording
remains the property of its reciter and producer.
Rights queries: twoplusonestar@gmail.com

LIBRARIES
------------------------------------------------------------------------
AndroidX, Jetpack Compose, Media3, WorkManager, DataStore
  Copyright The Android Open Source Project — Apache License 2.0
Kotlin standard library, kotlinx.coroutines, kotlinx.serialization
  Copyright 2010-2026 JetBrains s.r.o. — Apache License 2.0
Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
```

## Notes for future changes

**The translation is the item to be careful with.** It was originally taken from
Tanzil's `en.sahih` dump, which carries no licence, and Tanzil restricts its
translations page: "The translations provided at this page are for
non-commercial purposes only. If used otherwise, you need to obtain necessary
permission from the translator or the publisher." It was re-sourced from
QuranEnc.com's `english_saheeh` (Noor International edition), which publishes
terms that do permit republication. Those terms require the text be unmodified,
so the inline footnote markers such as "[2]" are kept and the footnotes are
carried in their own database column. Do not strip them.

**The Arabic text has no non-commercial limit.** Tanzil's Quran text is CC BY
3.0 and its terms say it "can be used in any website or application, provided
that its source (Tanzil Project) is clearly indicated, and a link is made to
tanzil.net". Only the translations page is non-commercial. The link requirement
is why the notices screen renders tanzil.net as a tappable link rather than
plain text.

**Recitation audio is not bundled** and must not become bundled without checking
rights. Recorded recitations carry sound-recording and performer's rights. The
app downloads from everyayah.com on demand; that site publishes no terms of use,
so there is no grant and no prohibition.

**Fonts** self-declare SIL OFL 1.1 in their own name tables (nameID 13 and 14).
If either is ever replaced, read the replacement's name table before shipping it:
`python3 -c "from fontTools.ttLib import TTFont; f=TTFont('x.ttf'); [print(r.nameID, r) for r in f['name'].names if r.nameID in (0,7,13,14)]"`
