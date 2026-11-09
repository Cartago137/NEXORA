# Android and mGBA Integration Plan

## Purpose

Bring the selected mGBA core into NEXORA on Android without coupling the GBA implementation to the app's future systems.

## Confirmed project decisions

- First platform: Android.
- First emulated system: Game Boy Advance.
- GBA core: mGBA.
- Native emulation/integration code: C/C++.
- Android application layer: Kotlin + Jetpack Compose.
- Native build: Android NDK + CMake.
- Kotlin/native boundary: a small JNI API.
- Architecture: system-independent application layer with system-specific core modules.

## Android application foundation

The current app has a Compose shell, four navigation destinations, Portuguese and English resources, and a full-screen window that hides the Android status bar.

The library foundation now uses Android's Storage Access Framework document picker. It stores the selected document URI, display name, file size, and persistable read permission. The original file is not copied or moved. Removal from the library is separate from the optional request to delete the original document.

Supported file extensions at the import layer: `.gba`, `.agb`, and `.bin`. An extension check is only a file-selection filter; it does not prove that the file contains a valid GBA ROM. ROM header/content validation belongs in the emulator integration milestone.

## Proposed native module boundaries

```text
app/
  src/main/
    java/com/nexora/app/
      MainActivity.kt
      GameLibrary.kt
      NativeBridge.kt
    cpp/
      CMakeLists.txt
      native_bridge.cpp
      systems/
        gba/
          mgba/              # pinned upstream source or controlled submodule
```

Keep the JNI layer thin. It should validate handles and arguments, then delegate to a GBA adapter that implements a system-independent emulator contract.

## First playable prototype milestone

Build a vertical slice that can:

1. Compile the native layer for Android ARM64 using NDK + CMake.
2. Load the native shared library through JNI.
3. Initialize and shut down an mGBA-backed GBA core.
4. Open a user-selected ROM through its Android content URI/file descriptor.
5. Advance emulation on a dedicated worker thread, never on the Compose UI thread.
6. Return video frames through a safe buffer/Surface strategy.
7. Stream audio through Android's low-latency audio APIs.
8. Send button press/release events into the core.
9. Persist battery saves and restore them safely.
10. Report errors and supported capabilities to the app.

Only after this vertical slice works should we mark the GBA core as integrated or advertise the app as playable.

## Important mGBA findings and constraints

- The upstream mGBA project is primarily a native C codebase and uses CMake.
- mGBA is distributed under the Mozilla Public License 2.0 (MPL-2.0). Before shipping, include required license/notice material and review obligations for modified MPL-covered files.
- Upstream lists local link cable support for two instances on the same computer. Networked multiplayer link cable and Wireless Adapter support must be treated as separate engineering work; do not assume NEXORA phone-to-phone Wi-Fi is supplied automatically by upstream.
- Mapping cartridge motion sensors and rumble behavior to Android sensors and phone vibration requires NEXORA-side integration and per-game/device validation.
- Android is not listed as an officially supported upstream mGBA frontend/platform. NEXORA must own and test its Android build/integration rather than assuming an official Android frontend exists.

## Acceptance criteria

- The Android app builds and installs on a physical ARM64 device.
- The native library loads without linker errors.
- A legally user-supplied test ROM starts and runs.
- Video, audio, and input work without blocking the UI.
- Pause/resume and activity lifecycle transitions do not corrupt the emulation session.
- Battery saves survive app restarts.
- The mGBA license and third-party notices are included.
- No claim of Wi-Fi multiplayer, motion, or rumble support is made until each is independently tested.
