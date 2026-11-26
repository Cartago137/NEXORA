# Third-party notices

NEXORA integrates the upstream mGBA project as a separate shared native library.

## mGBA 0.10.5

- Project: https://github.com/mgba-emu/mgba
- Pinned upstream commit: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`
- License: Mozilla Public License 2.0 (MPL-2.0)
- The full license text is included in `app/src/main/assets/licenses/mgba-MPL-2.0.txt`.

## blip_buf

- Included by the upstream mGBA build.
- License: GNU Lesser General Public License 2.1 (LGPL-2.1).
- The full license text is included in `app/src/main/assets/licenses/blip-buf-LGPL-2.1.txt`.

## inih

- Included by the upstream mGBA build.
- License: New BSD License.
- The full license text is included in `app/src/main/assets/licenses/inih-BSD.txt`.

The Android build is configured to keep mGBA as a separate shared library rather than statically folding it into NEXORA's JNI bridge. Before publishing a release, review the exact upstream source bundle, all transitive notices, and the applicable source/relinking obligations for the selected distribution format.

## Game cover artwork references

NEXORA can fetch optional GBA box-art images on demand for the following known titles. These images are hosted by third parties and remain copyrighted by their respective game publishers / rights holders; NEXORA does not claim ownership or bundle them as original assets.

- Pokémon FireRed: image hosted by Bulbagarden Archives (`FireRed_EN_boxart.png`).
- Mario & Luigi: Superstar Saga: image hosted by My Brain on Games (WordPress media).
- Mega Man Battle Network 2: image hosted by GamesFirst.
- Metroid: Zero Mission: image hosted by MobyGames.

The app caches retrieved artwork privately on the user's device. Review source-host terms, availability, and rights before distributing NEXORA publicly with automatic artwork fetching enabled.
