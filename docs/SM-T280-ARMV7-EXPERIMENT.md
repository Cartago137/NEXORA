# NEXORA ARMv7 experimental build

This branch explores compatibility with the Samsung Galaxy Tab A 7.0 Wi-Fi (SM-T280), whose target environment is Android 7.1.2 (API 25) on ARMv7 32-bit.

## Scope

- Branch: `experiment/sm-t280-armv7`
- Sets `minSdk = 25` for Android 7.1.2 and adds `armeabi-v7a` to the APK ABI filters without changing `main`.
- Adds a dedicated GitHub Actions workflow that compiles the Android app and native CMake targets, then checks that both `libnexora_native.so` and `libmgba.so` are packaged for ARMv7.
- Uploads the debug APK as a short-lived CI artifact for inspection.

## What a green build proves

A green CI run proves that the pinned source/toolchain can produce and package the ARMv7 native libraries. It does **not** prove successful installation or runtime compatibility on the SM-T280. Device validation must use Android 7.1.2 (API 25), not merely a newer Android ARM32 device.

## Acceptance gates

1. Gradle and CMake build complete successfully for `armeabi-v7a`.
2. APK contains both expected ARMv7 shared libraries.
3. APK metadata confirms `minSdkVersion=25` and ELF inspection confirms 32-bit ARM objects with no accidental 64-bit-only dependency.
4. APK installs and starts on the physical SM-T280 running Android 7.1.2.
5. Emulator core, input, audio, and battery save/load pass on the physical tablet.
6. Any dependency or API incompatibility with Android 7.1.2 is fixed and the full CI build is rerun.

Do not merge this branch into `main` or replace the regular APK until all applicable gates are met.
