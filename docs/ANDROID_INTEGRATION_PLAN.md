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

This is evidence about the compiled artifact, not access to its original source. The APK inspection does not establish the exact source revision, Gradle configuration, mGBA commit, rendering implementation, or all runtime behavior.

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

## Source recovery rules

1. Search for the original Kotlin, Gradle, C/C++, CMake, and mGBA integration source before recreating files.
2. Keep the supplied APK unchanged as a reference artifact; do not silently overwrite it with a new build.
3. Do not treat extracted shared libraries as maintainable source code.
4. Do not replace a working mGBA implementation with a fake/stub core and describe it as equivalent.
5. If source cannot be recovered, reconstruct one vertical slice at a time and compare its behavior with the reference APK.
6. Preserve the user's validated save behavior as a regression requirement.

## Repository implementation layout

Target layout, to be introduced incrementally:

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

This is the target structure, not a claim that these source files already exist.

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

Do not change rendering behavior speculatively. Reproduce and isolate the failure first.

## Acceptance criteria

- The source project builds reproducibly from GitHub Actions.
- The native library loads on the target ARM64 Android device.
- A user-provided GBA test ROM loads, advances frames, and renders at the correct size.
- Input, pause/resume, and activity lifecycle transitions are stable.
- Audio is tested independently from video.
- In-game saves survive app restart, matching the already-confirmed behavior.
- The mGBA license and all required notices are included.
- Multiplayer, motion, and rumble are advertised only after independent validation.
