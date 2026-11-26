# NEXORA Architecture

## 1. Goals

NEXORA is an Android-first handheld emulator targeting GBA, GB and GBC, with a custom premium interface and a modular foundation for future systems. Preserve ROM loading, battery saves, audio, rendering and input behavior while expanding the product.

## 2. Confirmed technology direction

- Android application/UI: Kotlin + Jetpack Compose.
- Native emulation: C/C++ with Android NDK and CMake.
- Emulator core: mGBA pinned to `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.
- App/native boundary: JNI.
- Build source of truth: GitHub repository and GitHub Actions.

## 3. Layers

```text
Presentation (Kotlin / Compose)
  ├── Home and featured game
  ├── Unified Library (All / GBA / GB / GBC), folders and search
  ├── Emulator screen and touch controls
  └── Gameplay function menu, settings and localization

Application (Kotlin)
  ├── Game library and metadata
  ├── Save-data and session coordination
  ├── Input mapping and speed control
  └── Link session / Wi-Fi transport coordination

Emulation abstraction (JNI / C++)
  ├── Active platform detection
  ├── Dynamic video dimensions and audio
  ├── Save State / Load State
  └── Native input and lifecycle

System modules (C/C++)
  ├── mGBA GBA core
  └── mGBA shared GB/GBC core

Android platform services
  ├── File access and app-private state storage
  ├── Audio, vibration and sensors
  └── Local networking
```

The JNI bridge remains narrow. Kotlin UI code must not depend directly on mGBA types or internal functions.

## 4. Platform selection and video contract

- Detect the actual platform from the cartridge header with `mCoreIsCompatible`; do not trust the filename extension as the source of truth.
- `mPLATFORM_GBA` selects GBA; `mPLATFORM_GB` selects the shared original GB/GBC core.
- The native framebuffer uses a maximum-size backing stride, then packs only the active frame dimensions into the Kotlin-owned buffer.
- GBA output is normally 240x160 (3:2); GB/GBC output is normally 160x144 (10:9). The player preserves the active aspect ratio.
- The unified Library console filter currently classifies by file extension; actual core selection on load uses the ROM header. A future metadata pass should persist the detected platform so unusual extensions are categorized accurately.

## 5. Emulator capabilities

The app should expose capabilities rather than assume every system supports the same hardware:
- save states;
- link cable / multiplayer;
- motion/tilt sensors;
- rumble;
- future peripherals.

The GBA Link Cable action is disabled for GB/GBC. Save States are disabled while a Link session is attached because restoring one side alone would desynchronize the pair.

## 6. Save data

- Native in-game battery saves remain separate from Save States.
- Save States use mGBA's named-state API with `SAVESTATE_ALL`.
- State files are stored in app-private storage and keyed by a SHA-256 identifier derived from the imported ROM URI.
- Writes go to a temporary file and replace the previous state only after mGBA completes serialization.
- State save/load must be tested by saving, advancing, restoring, restarting the app and testing missing/corrupt files.

## 7. Multiplayer direction and current limits

The first milestone is two GBA devices on one local Wi-Fi network:
1. Host/join room and handshake with session ID, game code and ROM CRC32.
2. Bounded NXLP v1 serial messages over TCP.
3. JNI queues connect transport to mGBA's GBA MULTI SIO driver.
4. Native callbacks must never perform socket I/O or block the Android UI thread.

The current direct remote SIO implementation uses non-blocking polling at the host timing callback. It is not yet proven to provide deterministic distributed lockstep. Cable Club behavior and an actual Pokémon trade must be tested on two physical devices. Do not claim universal Link Cable compatibility.

## 8. Quality requirements

- Keep core emulation work off the UI thread.
- Bound network queues and validate packet lengths, versions and transfer IDs.
- Handle disconnect, timeout, unload and malformed ROMs safely.
- Preserve input combinations and clear held keys on cancel, pause and exit.
- Test GBA, GB and GBC independently.
- Record CI results and distinguish build success from physical-device validation.
- Retain third-party licenses and notices.


## 9. Touch-control customization

- Touch layout values are stored separately in app preferences and do not touch ROM metadata, battery saves or save states.
- The settings editor exposes D-pad, A/B, L/R and system-button sizes; grouped position controls for D-pad, A/B, L/R and MENU/SELECT/START; and overall control opacity.
- The same saved values are consumed by portrait and landscape player layouts.
- Keep the approved control atlas and sprite mapping unchanged; visual assets are independent from layout geometry.
- The editor is an incremental foundation: future iterations can move from grouped position controls to per-button drag placement and user-defined layouts/profiles.

## 10. Visual touch-layout editor (0.4.6)

- The editor reuses the approved NEXORA control atlas; it does not generate or replace artwork.
- D-pad, A, B, L, R, MENU, SELECT and START each have independent size and X/Y offset preferences.
- One-finger drag changes the selected control position; two-finger pinch changes its size.
- Touch-layout preferences remain in the dedicated `nexora_touch_controls` SharedPreferences store and are isolated from ROM files, battery saves and Save States.
- The current editor writes changes immediately and offers a reset-to-default action. Named profiles and independent portrait/landscape profiles remain future work.
- Library and History use explicit bounded/scrollable content in compact landscape layouts; physical-device acceptance remains required.


## 11. Gameplay-style touch editor (0.4.7)

- The editor preview uses the same portrait/landscape control grouping and anchor arrangement as the player screen.
- D-pad, A, B, L, R, MENU, SELECT and START remain individually draggable and resizable.
- Overall opacity is configured separately in Settings > Controls.
- The approved control atlas and isolated touch-preferences store remain unchanged; ROMs and saves are not modified.
- Physical-device acceptance on the Motorola G54 remains required in both orientations.


## 12. Minimal gameplay-like control editor (0.4.8)

The control editor is a full-screen gameplay-like surface. It omits the editor title, in-preview NEXORA branding, reset action, and selection/instruction captions. The central game viewport is plain black. System back navigation exits the editor; touch layout preferences continue to save automatically.
