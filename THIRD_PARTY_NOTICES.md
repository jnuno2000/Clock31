# Third-party notices

Clock 31 itself is licensed under the GNU General Public License v3 (see
[`LICENSE`](LICENSE)). This file lists third-party assets bundled with the app
and the licenses they are distributed under. The full license texts also ship
inside the APK next to the fonts, in `app/src/main/assets/fonts/`.

## Fonts

### Google Sans (Regular and Medium)

- Files: `app/src/main/assets/fonts/google_sans_regular.ttf`,
  `app/src/main/assets/fonts/google_sans_medium.ttf`
- Used for: the calendar event blocks (title and time).
- Copyright 2025 The Google Sans Project Authors
  (<https://github.com/googlefonts/googlesans>).
- License: **SIL Open Font License, Version 1.1** — see
  [`app/src/main/assets/fonts/OFL.txt`](app/src/main/assets/fonts/OFL.txt) and
  <https://openfontlicense.org>.
- Google released Google Sans under the OFL in December 2025, which permits
  embedding and redistributing the font as part of software such as this app.
  The fonts are bundled unmodified; the file names differ but the font itself
  is unchanged.

### MiSans Latin (Regular)

- File: `app/src/main/assets/fonts/mi_sans.ttf`
- Used for: the clock and the date/next-alarm line (matching the HyperOS
  lock-screen look).
- Copyright 2020-2024 Beijing Xiaomi Mobile Software Co., Ltd. & Hanyi Fonts.
- License: **MiSans Font License** — free to use in applications; see
  [`app/src/main/assets/fonts/MiSans-LICENSE.txt`](app/src/main/assets/fonts/MiSans-LICENSE.txt)
  and <https://hyperos.mi.com/font/en/>.
- Xiaomi's license permits distributing a work (such as this app) created with
  MiSans, and requires that software note MiSans is used — this file and the
  bundled notice satisfy that. The font must not be resold on its own or
  modified; the file bundled here is the unmodified original.

## Wallpaper / Material You color

The wallpaper-derived color used for the clock and date on Android 12+ comes
from the Android platform's own `@android:color/system_*` dynamic-color
resources. Nothing is bundled for this; the system provides the colors at
runtime.
