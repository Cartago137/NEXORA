# GB and GBC in the first NEXORA release

## Product decision

The first NEXORA release targets all three systems:

- Game Boy Advance (GBA)
- Game Boy (GB)
- Game Boy Color (GBC)

GB and GBC use mGBA's existing shared GB core. They do not require a second emulator library. In mGBA, `mPLATFORM_GB` represents both original Game Boy and Game Boy Color cartridges; the cartridge header and mGBA's model detection select the appropriate hardware behavior.

## Integration approach

- Compile mGBA with both `M_CORE_GBA=ON` and `M_CORE_GB=ON`.
- Identify the console from the ROM header with `mCoreIsCompatible`, not from the filename extension alone.
- Create the matching `mPLATFORM_GBA` or `mPLATFORM_GB` core.
- Keep the JNI boundary platform-neutral and expose the active frame width, height, and platform to Kotlin.
- Allocate a maximum backing video buffer large enough for GBA and GB-family output, then pack only the active frame dimensions for Android.
- Preserve the native aspect ratio: GBA 240x160 (3:2), GB/GBC normally 160x144 (10:9).
- Keep the existing A/B, D-pad, SELECT, and START input bit positions. Hide GBA-only L/R controls in the GB-family player.
- Continue using per-game battery-save files, with the same per-ROM identity separation used for GBA.

## Current implementation in this branch

The branch enables the mGBA GB core, detects GBA vs GB-family ROMs from their headers, exposes native frame dimensions/platform through JNI, accepts `.gb` and `.gbc` files in the library, and sizes the player image to the active console. These are source changes only; they are not yet considered complete or released.

## Validation required before release

1. Build Debug and Release APKs for the supported Android ABIs.
2. Load a known-good original GB ROM and verify gameplay, sound, controls, and 160x144 rendering.
3. Load a known-good GBC ROM and verify Color mode, gameplay, sound, controls, and 160x144 rendering.
4. Confirm SELECT/START and all four directions work; confirm A/B work; confirm no GBA L/R buttons are shown for GB/GBC.
5. Save progress inside a GB game, close NEXORA completely, reopen it, and confirm the save persists.
6. Repeat the save-persistence test for GBC.
7. Re-test at least one GBA game, including video, audio, input, and battery-save persistence, to catch regressions.
8. Test ROM identification when a file's extension does not match its actual cartridge header.
9. Test malformed and unsupported files; failures must not crash the app or leave a partially active core.

## Release gate

Do not advertise GB/GBC as working in a release build until the Android build passes and the GB, GBC, and GBA regression checks above have been completed on a physical Android device. Build success alone does not establish emulator compatibility.
