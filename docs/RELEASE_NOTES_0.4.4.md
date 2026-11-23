# NEXORA 0.4.4

## Touch controls and landscape menu

- Increased the D-pad hit/render area from 104 dp to 128 dp in portrait and 136 dp in landscape.
- Widened the landscape control wells to give the larger D-pad enough room without crowding the centered game image.
- Made the in-game functions menu vertically scrollable on short displays.
- Moved **Exit game** to the top of the functions menu so it remains immediately reachable in landscape.
- Kept the existing approved sprite atlas, directional gesture handling, button mappings, native emulation flow, and save paths unchanged.
- Added one-time diagnostic logging for successful or failed decoding of the official control atlas, and disabled density scaling while decoding it.

## Validation

- Automated JVM tests and Android APK build: pending CI.
- Physical-device validation on the Motorola G54: pending.
- This release does not modify ROM files, battery saves, or save-state files.
