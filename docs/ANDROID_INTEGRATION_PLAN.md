# Android and mGBA Integration Plan

## Purpose

Define the first technical milestone for bringing the selected mGBA core into NEXORA on Android without coupling the GBA implementation to the app's future systems.

## Confirmed project decisions

- First platform: Android.
- First emulated system: Game Boy Advance.
- GBA core: mGBA.
- Native emulation/integration code: C/C++.
- Architecture: system-independent application layer with system-specific core modules.

## Proposed Android bridge

Use the Android NDK to compile the native emulation layer as a shared library. Expose a small, stable interface to the Android application through JNI. Keep JNI calls thin: validate and translate requests, then delegate to the native core adapter.

The official Android documentation describes this model: C/C++ is built into native shared libraries and Java/Kotlin calls it through JNI. CMake is supported by Android Studio/Gradle for native builds.

## Proposed module boundaries

```text
android-app/
  app/                         # Android app and user interface
  native/
    CMakeLists.txt             # Native build entry point
    include/nexora/core/       # System-independent core contracts
    src/                       # Native bridge and shared implementation
    systems/
      gba/
        mgba/                  # mGBA integration, isolated from app UI
```

This is a proposed layout, not yet a generated or build-tested Android project.

## First prototype milestone

Build a minimal Android test app that can:

1. Compile the native layer for Android ARM64 using NDK + CMake.
2. Load the native shared library through JNI.
3. Initialize and shut down an mGBA-backed GBA core.
4. Load a user-selected GBA ROM from an app-provided file path/descriptor.
5. Advance emulation away from the UI thread.
6. Return a video frame and audio samples to the Android side.
7. Send button press/release events into the core.
8. Report errors and supported capabilities to the app.

Only after this vertical slice works should we build the full NEXORA library and visual interface around it.

## Important mGBA findings and constraints

- The upstream mGBA project is primarily a native C codebase and uses CMake.
- mGBA is distributed under the Mozilla Public License 2.0 (MPL-2.0). Before shipping, include the required license/notice material and review obligations for any modified MPL-covered files.
- Upstream lists local link cable support for two instances on the same computer. Its README lists networked multiplayer link cable and Wireless Adapter support as planned features. Therefore, NEXORA's phone-to-phone Wi-Fi multiplayer must be treated as a separate engineering task and proven with a prototype; it must not be advertised as already supported by upstream mGBA.
- mGBA supports cartridges with motion sensors and rumble when used with game controllers. Mapping these features to Android sensors and phone vibration requires NEXORA-side integration and per-game/device validation.
- Upstream currently lists desktop and several console platforms; Android is not listed as an officially supported platform. NEXORA must own and test its Android build/integration rather than assuming an official Android frontend exists.

## Open decision: Android application/UI layer

The native mGBA integration does not determine the language used for the rest of the Android app. Recommended direction:

- Kotlin + Jetpack Compose for Android screens, navigation, storage access, lifecycle, and settings.
- C/C++ + Android NDK + CMake for mGBA and performance-sensitive emulation.
- JNI as the narrow boundary between the Android app and native layer.

This keeps the emulator native while using Android's standard application tooling for the library and polished interface. Confirm this UI-layer choice before generating the full Android project.

## Acceptance criteria

- The Android app builds and installs on a physical ARM64 device.
- The native library loads without linker errors.
- A legally user-supplied test ROM starts and runs.
- Video, audio, and input work without blocking the UI.
- Pause/resume and activity lifecycle transitions do not corrupt the emulation session.
- The mGBA license and third-party notices are included in the project.
- No claim of Wi-Fi multiplayer, motion, or rumble support is made until each is independently tested.
