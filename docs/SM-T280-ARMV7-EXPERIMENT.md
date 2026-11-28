# NEXORA ARMv7 experimental build

This branch explores compatibility with the Samsung Galaxy Tab A 7.0 Wi-Fi (SM-T280), whose target environment is Android 7.1.2 (API 25) on ARMv7 32-bit.

## Scope

- Branch: `experiment/sm-t280-armv7`
- Sets `minSdk = 25` for Android 7.1.2 and adds `armeabi-v7a` to the APK ABI filters without changing `main`.
- Uses Android SDK 37 to satisfy the current AndroidX Compose dependency metadata; compile SDK does not set the minimum Android version required at runtime.
- Builds the Android app and native CMake targets, then verifies the packaged NEXORA JNI library is 32-bit ARM and checks its shared-library dependencies.
- Uploads the debug APK as a short-lived CI artifact for inspection.

## What a green build proves

A green CI run proves that the pinned source/toolchain can produce an APK containing the ARMv7 native library and declaring API 25 as its minimum. It does **not** prove successful installation or runtime compatibility on the SM-T280. Device validation must use Android 7.1.2 (API 25), not merely a newer Android ARM32 device.

## Acceptance gates

1. Gradle and CMake build complete successfully for `armeabi-v7a`.
2. APK contains `lib/armeabi-v7a/libnexora_native.so`.
3. ELF inspection confirms a 32-bit ARM binary and all non-system shared-library dependencies are packaged.
4. APK metadata confirms `minSdkVersion=25`.
5. APK installs and starts on the physical SM-T280 running Android 7.1.2.
6. Emulator core, input, audio, and battery save/load pass on the physical tablet.
7. Any dependency or API incompatibility with Android 7.1.2 is fixed and the full CI build is rerun.

Do not merge this branch into `main` or replace the regular APK until all applicable gates are met.
