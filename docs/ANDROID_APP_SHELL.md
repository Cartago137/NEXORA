# Android App Shell — Milestone 1

## What is included

- Kotlin Android application module.
- Jetpack Compose starter screen using NEXORA's dark purple visual direction.
- Android NDK + CMake native shared-library target.
- A minimal JNI bridge so the Android layer can verify that the native library loads.
- ARM64 Android and x86_64 emulator build targets.

## What is not included yet

- The mGBA source code is not vendored or linked yet.
- No ROM loading, emulation loop, graphics output, audio, saves, or controller input is implemented.
- This is a starter shell, not a playable emulator.

## Next engineering milestone

Integrate a pinned mGBA release into the native build. Upstream mGBA uses CMake, but Android is not listed among its officially supported platforms. We must therefore validate the Android build configuration and dependencies instead of assuming the desktop build works unchanged.

## Build environment

Open the repository root in a compatible Android Studio installation. Install the Android SDK platform 36, Android NDK, and CMake 3.22.1 through SDK Manager. The Gradle wrapper must be generated/verified by the Android Studio/Gradle environment before a first build if it is not present in the repository.

The initial project configuration targets Java 17, Android API 26+, and Android API 36.
