# NEXORA

NEXORA is an Android-first handheld emulator project targeting Game Boy Advance (GBA), Game Boy (GB), and Game Boy Color (GBC) in its first release, using the mGBA core with a premium console-inspired experience and a modular architecture for future systems.

> **Project status:** a test APK has already been produced and tested on a physical Android device. Static inspection of that APK confirms an Android application, Kotlin/Compose-related app classes, a native NEXORA library for ARM64 and x86_64, JNI entry points, and mGBA symbols. The application and native source project have been restored to `main`; ongoing work is now focused on first-release system support, validation, and product features.

## Verified continuity

The previously tested APK provides a concrete reference implementation; this project is not being treated as a blank-slate product.

Evidence from the APK inspection:
- Android package containing Kotlin application classes, including `MainActivityKt`, `NativeBridge`, and Compose-generated player-screen classes.
- Native shared library `libnexora_native.so` for `arm64-v8a` and `x86_64`.
- JNI entry points for status, ROM loading, frame execution, audio reads, key input, and ROM unloading.
- mGBA-related native symbols and an included mGBA MPL-2.0 license notice.
- The user confirmed that in-game GBA saves remain available after fully closing and reopening the app.

These findings establish useful compatibility targets, but they do not recover the original Kotlin/C/C++ source or prove that every feature is complete. The APK is a reference artifact, not a substitute for source code.

See [APK audit and source recovery](docs/APK_AUDIT_AND_SOURCE_RECOVERY.md).

## Product direction

- **Platform:** Android first.
- **First-release systems:** Game Boy Advance (GBA), Game Boy (GB), and Game Boy Color (GBC), using mGBA.
- **Application layer:** Kotlin and Jetpack Compose, as indicated by the inspected APK.
- **Native layer:** C/C++ with Android NDK and JNI.
- **Future growth:** modular architecture that can accommodate additional console cores without rebuilding the application from scratch.
- **Languages:** English and Spanish.
- **Visual identity:** dark, premium interface inspired by deep Gengar-like purples, with restrained violet and pink/red accents.
- **Orientation-aware experience:** the player preserves each system's native aspect ratio, while the overall landscape/portrait layouts remain responsive.
- **Personalization:** selectable color themes and user-provided console-body skins.
- **Game library:** searchable collection with an **All games** view and separate **GBA**, **GB**, and **GBC** categories, plus custom folders, favorites, pinned items, multi-select actions, and play history.
- **Featured game presentation:** a subtle floating 3D game box, not a flat cover or cartridge.
- **Special hardware features:** support for compatible motion/tilt sensors and rumble is a product goal that still requires implementation and per-game/device validation.
- **Connectivity:** multiplayer/link functionality is a core product goal, including local Wi-Fi connectivity where technically and legally feasible.

## Engineering principles

1. Keep the user interface, emulator core, platform services, and system-specific integrations separated.
2. Define stable interfaces between the app and each emulator core.
3. Keep console-specific behavior inside system modules rather than scattering it throughout the app.
4. Document decisions, limitations, licensing, and test coverage.
5. Prefer incremental, testable changes over a large unverified implementation.
6. Preserve tested behavior when reconstructing the source project; do not replace the existing native core with a placeholder and call it equivalent.
7. Respect software licenses and users' rights: NEXORA will not include copyrighted game ROMs or BIOS files.

## Repository status

The Android source project has been restored to `main` from preserved development branches in this repository. The APK's embedded Git revision identifies the exact launch-shell snapshot (`17bcb65bad01e23790605ef4ec32fbdb173e637a`). The restored mainline source uses the later CI-verified `feature/audio-saves-orientation` development line, which includes the Kotlin/Compose application, JNI/C++ bridge, pinned mGBA CMake integration, Gradle configuration, license notices, multi-ROM import, reusable video buffers, corrected native color conversion, audio, battery-save loading, and orientation handling. GB/GBC first-release integration is being developed separately and must pass Android build and device validation before being described as released.

The `feature/audio-saves-orientation` branch's debug APK build completed successfully in GitHub Actions (run 42711690785). The restored `main` branch now triggers the Android build workflow. The generated APK still requires physical-device validation before it is treated as a release candidate. A successful compile does not replace physical-device testing.

## Development sequence

1. Preserve the tested APK and its exact source revision as a compatibility reference.
2. Restore the later CI-verified Android and native source line into `main`.
3. Run the Android/NDK build from the restoration pull request and keep the pipeline in GitHub Actions.
4. Install the new APK on the target phone and revalidate rendering, audio, controls, orientation, and save persistence.
5. Continue feature work only from the restored, reproducible source project.

## Legal and preservation note

NEXORA is intended to run user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files. Any third-party emulator core or library must be reviewed for license compatibility before integration.
