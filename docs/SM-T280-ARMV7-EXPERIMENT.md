# NEXORA ARMv7 experimental build

This branch adds an isolated ARMv7 build target for the Samsung Galaxy Tab A 7.0 Wi-Fi (SM-T280, ARMv7 32-bit).

## Scope

- Branch: `experiment/sm-t280-armv7`
- Adds `armeabi-v7a` to the APK ABI filters without changing `main`.
- Adds a dedicated GitHub Actions workflow that compiles the Android app and native CMake targets, then checks that both `libnexora_native.so` and `libmgba.so` are packaged for ARMv7.
- Uploads the debug APK as a short-lived CI artifact for inspection.

## What a green build proves

A green CI run proves that the pinned source/toolchain can produce and package the ARMv7 native libraries. It does **not** prove successful installation or runtime compatibility on the SM-T280. That still requires checking ELF architecture and dependencies, installing on an Android 10 ARM32 device, launching the app, loading a legally obtained test ROM, and validating input, audio, and save/load.

## Acceptance gates

1. Gradle and CMake build complete successfully for `armeabi-v7a`.
2. APK contains both expected ARMv7 shared libraries.
3. ELF inspection confirms 32-bit ARM objects and no accidental 64-bit-only dependency.
4. APK installs and starts on Android 10 ARM32.
5. Emulator core, input, audio, and battery save/load pass on physical hardware.

Do not merge this branch into `main` or replace the regular APK until all applicable gates are met.
