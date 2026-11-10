# APK Audit and Source Recovery Record

## Scope

This record documents a static inspection of the user-provided NEXORA test APK. It is intended to preserve technical observations and prevent the project from being incorrectly treated as a blank slate.

## Observed package contents

The inspected APK contained:
- Android DEX files, including application classes under `com.nexora.app`.
- Kotlin-related application classes such as `MainActivityKt` and `NativeBridge`.
- Compose-generated player-screen classes.
- `lib/arm64-v8a/libnexora_native.so`.
- `lib/x86_64/libnexora_native.so`.
- An mGBA MPL-2.0 license notice and additional third-party license notices.

## Native interface observations

JNI exports found in the native library:

- `Java_com_nexora_app_NativeBridge_nativeStatus`
- `Java_com_nexora_app_NativeBridge_nativeLoadRom`
- `Java_com_nexora_app_NativeBridge_nativeRunFrame`
- `Java_com_nexora_app_NativeBridge_nativeReadAudio`
- `Java_com_nexora_app_NativeBridge_nativeSetKeys`
- `Java_com_nexora_app_NativeBridge_nativeUnloadRom`

mGBA-related native symbols were also present, including symbols associated with GBA core creation and ROM loading.

## User-confirmed runtime behavior

The user confirmed that in-game save data written by GBA games remains available after completely closing NEXORA and opening it again. This is a validated regression requirement for future builds.

## Known issue requiring runtime diagnosis

Pokémon FireRed was previously reported to show a white/small image after part of the intro and fail to proceed normally. The static inspection did not identify the cause and did not test the APK dynamically.

The issue may involve rendering dimensions, pixel format, frame-buffer handling, synchronization, emulation state, or another runtime path. No single cause is established.

## What this audit does not prove

- It does not recover original Kotlin, Compose, C/C++, or CMake source.
- It does not establish the exact mGBA commit or build flags.
- It does not prove all game compatibility, audio, multiplayer, sensor, or rumble behavior.
- It does not establish that the APK can be reproduced from the current GitHub repository.
- It does not constitute a physical-device test performed during this audit.

## Source recovery strategy

1. Treat the tested APK as an immutable reference.
2. Recover original source files if they remain available from the original development workspace or backups.
3. Otherwise reconstruct source incrementally, using bytecode and native symbols only as supporting evidence.
4. Keep the JNI contract compatible only after exact method signatures are verified.
5. Compare each reconstructed APK against the reference on the target device.
6. Add regression tests for save persistence and the reported rendering issue.

## Important repository distinction

At the time of this record, the GitHub repository contains documentation and workflow configuration but not the Android source project. The compiled APK and its native libraries are not the same thing as a maintainable source tree.
