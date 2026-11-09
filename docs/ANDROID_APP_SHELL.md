# Android App Shell — Foundation Milestone

## What is included

- Kotlin Android application module with Jetpack Compose.
- NEXORA's dark purple visual identity and four navigation destinations.
- Portuguese default and English translations, selected through Android locale resources.
- Full-screen activity window that hides the Android status bar while keeping system navigation available.
- Android document picker for user-selected `.gba`, `.agb`, and `.bin` files.
- Persistent library index with stored document URIs and persisted access permissions.
- Separate removal actions: remove only from the library, or request deletion of the original document.
- Android NDK + CMake native shared-library target.
- mGBA 0.10.5 pinned to upstream commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`, compiled as a separate shared library for Android.
- JNI integration for mGBA core initialization, ROM loading/unloading, frame execution, video-buffer delivery, and GBA key input.
- An experimental Compose gameplay screen that runs the GBA frame loop and displays the 240×160 framebuffer.
- On-screen GBA controls with independent key press/release handling for simultaneous touch input.
- A temporary diagnostic mode with audio playback disabled while the previous runtime crash is investigated.
- A 64 MiB upper bound on ROM cache copies to avoid unbounded storage use.
- ARM64 Android and x86_64 emulator build targets.
- Third-party license texts for mGBA, blip_buf, and inih included in app assets.

## Verified in CI

The GitHub Actions workflow successfully assembled a debug APK with the pinned mGBA source compiled and linked. This verifies the Android native build pipeline for the configured ABIs.

## What is not included yet

- Audio playback (currently disabled in the diagnostic build), save states, and physical controller mapping.
- Physical-device verification of battery-save persistence (the per-game `.sav` file path is now connected to mGBA).
- Verified pause/resume, background behavior, and orientation changes during an active session.
- Search, folders, favorites, pinned games, batch actions, and play history.
- Physical-device validation of full-screen behavior, persisted file access, and runtime loading of the mGBA shared library.

The native bridge now attempts to run frames and display the GBA framebuffer. The core setup sequence has been corrected to configure video before ROM loading and reset the core after loading. However, this is still an experimental diagnostic milestone: audio is temporarily disabled, battery saves are not implemented, and successful CI compilation does not prove stable gameplay on a physical phone. Do not label this a finished playable release until the test ROM runs reliably on-device.

## Build environment

The repeatable build is currently provided by GitHub Actions. The project configuration uses Java 17, Android Gradle Plugin 9.3.1, Gradle 9.5.0, compile SDK 37, target SDK 36, minimum SDK 26, Android NDK 27.2.12479018, and CMake 3.22.1.

The Android app targets ARM64 devices and x86_64 emulators. A successful CI build is not a substitute for testing on the user's physical device.
