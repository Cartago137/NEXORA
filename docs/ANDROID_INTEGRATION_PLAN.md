# Android and mGBA Integration — Recovery and Build Plan

## Purpose

Bring the already-tested NEXORA Android application and its mGBA-backed native core into a reproducible, maintainable GitHub source project. This document supersedes the earlier assumption that no application prototype exists.

## What has already been observed

Static inspection of the user-provided test APK found:

- Kotlin application classes, including `com.nexora.app.MainActivityKt` and `NativeBridge`.
- Compose-generated player-screen classes.
- Native libraries named `libnexora_native.so` for `arm64-v8a` and `x86_64`.
- JNI methods corresponding to status, ROM load, frame execution, audio read, key input, and ROM unload.
- mGBA-related symbols, including core creation and ROM-loading functions.
- An included mGBA MPL-2.0 license file.

The user also confirmed that native in-game GBA saves persist after the app is fully closed and reopened.

The APK's `META-INF/version-control-info.textproto` contains Git revision `17bcb65bad01e23790605ef4ec32fbdb173e637a`. That commit exists in this repository on `recovery/stable-launch-shell`, and its Git tree contains the original Android source, Gradle files, native bridge, CMake integration, and license assets. Later preserved branches contain additional development work; this recovery uses the CI-verified `feature/audio-saves-orientation` line as the mainline source candidate.

## Confirmed technical direction

- Android application/UI: Kotlin + Jetpack Compose.
- Native emulation/integration: C/C++ using Android NDK and CMake.
- GBA core: mGBA, isolated behind NEXORA's system-independent emulator contract.
- Boundary: thin JNI bridge; UI and Android lifecycle code must not call mGBA internals directly.
- Build and source of truth: GitHub repository and GitHub Actions.
- Supported build ABI baseline observed in the APK: `arm64-v8a` and `x86_64`. Other ABIs are not assumed until tested.

## Existing JNI compatibility surface

The inspected binary exposes JNI methods with these responsibilities:

| Native bridge responsibility | Expected behavior |
| --- | --- |
| `nativeStatus` | Report native core availability/state |
| `nativeLoadRom` | Load a user-selected ROM |
| `nativeRunFrame` | Advance emulation and produce video output |
| `nativeReadAudio` | Read generated audio samples |
| `nativeSetKeys` | Apply GBA key state |
| `nativeUnloadRom` | Release the active game/core resources |

These names are reverse-engineering observations. Exact Kotlin signatures, parameter types, return types, and threading requirements must be verified from source or bytecode before implementing a replacement bridge.

## Source recovery status

- [x] Located the exact Git revision embedded in the reference APK: `17bcb65bad01e23790605ef4ec32fbdb173e637a`.
- [x] Confirmed that the revision maps to the preserved `recovery/stable-launch-shell` branch and contains the Android source project.
- [x] Identified the later `feature/audio-saves-orientation` source line, which includes the frame-rendering/color-conversion fix, audio pipeline, per-game battery-save loading, multi-ROM import, and orientation handling.
- [x] Confirmed that the latest commit on that line (`89b104099543e1e07be26c62fb33a09576b641b1`) passed the Android debug build in GitHub Actions (run 42711690785).
- [ ] Restore the source tree to `main` and pass the build workflow on the restoration pull request.
- [ ] Install the resulting APK on the target phone and validate runtime behavior.

Keep the uploaded APK unchanged as a reference. Do not replace the mGBA implementation with a stub. Preserve the user's validated save behavior as a regression requirement.

## Repository implementation layout

Restored Android source layout:

```text
NEXORA/
├── app/                         # Kotlin/Compose Android application
├── native/
│   ├── CMakeLists.txt
│   ├── include/nexora/           # System-independent native contracts
│   ├── src/                      # JNI bridge and shared native code
│   └── systems/gba/               # Isolated mGBA adapter
├── gradle/
├── .github/workflows/            # Reproducible checks and APK builds
├── docs/
└── README.md
```

The Android app, Gradle configuration, JNI bridge, and CMake entry point are restored in the recovery branch. The native mGBA source itself is fetched by CMake from the pinned upstream commit.

## Build pipeline requirements

The GitHub Actions build must:
- use a pinned, documented JDK, Gradle, Android SDK, and NDK setup;
- build the debug APK without requiring signing secrets;
- run Kotlin/unit tests and native compilation checks where available;
- upload the generated APK as a workflow artifact;
- keep release signing separate from debug/CI builds;
- never print signing passwords or commit keystores, ROMs, BIOS files, or generated build outputs.

## Rendering and stability investigation

The previously reported Pokémon FireRed issue (image becoming white/small after the intro) is not diagnosed by static APK inspection. The next runtime investigation must capture:
- frame dimensions, pixel format, stride, and orientation;
- buffer lifetime and synchronization between native emulation and Compose rendering;
- frame pacing and UI-thread blocking;
- ROM load and reset state;
- device logs and native error/status output.

The `fix/frame-rendering-performance` ancestor includes a native pixel-channel conversion correction and reusable framebuffer/render-pacing changes. These are relevant candidate fixes, but they are not yet confirmed to resolve the exact white/small Pokémon FireRed screen reported on the user's phone. Do not mark that issue fixed until the restored APK is tested on-device.

## Acceptance criteria

- The `feature/audio-saves-orientation` source line has a successful GitHub Actions debug build; the restoration pull request must pass the same workflow on `main`.
- The native library loads on the target ARM64 Android device.
- A user-provided GBA test ROM loads, advances frames, and renders at the correct size.
- Input, pause/resume, and activity lifecycle transitions are stable.
- Audio is tested independently from video.
- In-game saves survive app restart, matching the already-confirmed behavior.
- The mGBA license and all required notices are included.
- Multiplayer, motion, and rumble are advertised only after independent validation.
