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

## Source revision recovered from APK metadata

The APK contains `META-INF/version-control-info.textproto` with:

- Git revision: `17bcb65bad01e23790605ef4ec32fbdb173e637a`.
- Android Gradle Plugin version: `9.3.1`.

The revision resolves to the preserved `recovery/stable-launch-shell` branch. Its Git tree includes `app/build.gradle.kts`, `MainActivity.kt`, `NativeBridge.kt`, `native_bridge.cpp`, `CMakeLists.txt`, Gradle configuration, Android resources, and third-party license assets. This means the source was recovered from Git history, not decompiled or recreated from the APK.

The later `feature/audio-saves-orientation` branch contains the next development line and passed Android debug build run `42711690785` at commit `89b104099543e1e07be26c62fb33a09576b641b1`. Its ancestry includes the rendering-fix commit `a7613bd6f67e7e42c59f9d8f31f90802c8efb581` (native GBA color-channel conversion), plus reusable frame rendering, audio, save, multi-ROM import, and orientation work.

The CMake integration pins mGBA to upstream commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` and labels the integration as version `0.10.5`.

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

- The original source revision was located in Git history; however, the APK alone does not prove that every later development-branch change was included in that specific binary.
- The recovered source branch's CI build does not prove runtime compatibility on every Android device.

- It does not prove all game compatibility, audio, multiplayer, sensor, or rumble behavior.
- It does not establish that the APK can be reproduced from the current GitHub repository.
- It does not constitute a physical-device test performed during this audit.

## Source recovery result

1. Preserve the tested APK as an immutable reference.
2. Restore the source from the identified Git history rather than reverse-engineering the binary.
3. Use the later CI-verified development branch as the current source candidate, while retaining the exact APK-generating commit in history.
4. Keep JNI signatures aligned with the recovered Kotlin/native source.
5. Run the restored workflow on the current mainline and compare the resulting APK with the reference.
6. Add regression checks for save persistence, rendering, audio, and lifecycle behavior.

## Remaining validation

The source is available and has been restored to `main`. The native color-channel correction is present in the later development line, but the reported Pokémon FireRed white/small-screen issue is not considered fixed until the new APK is tested on the user's phone. The successful CI run verifies compilation only, not gameplay.
