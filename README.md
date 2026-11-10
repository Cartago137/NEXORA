# NEXORA

NEXORA is an Android-first Game Boy Advance emulator project with a premium console-inspired experience and a modular architecture intended to support additional systems in the future.

> **Project status:** a test APK has already been produced and tested on a physical Android device. Static inspection of that APK confirms an Android application, Kotlin/Compose-related app classes, a native NEXORA library for ARM64 and x86_64, JNI entry points, and mGBA symbols. The current GitHub repository does **not yet contain the corresponding application/native source project**. The immediate engineering task is to reconcile the tested binary with a maintainable, reproducible source tree.

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
- **Initial system:** Game Boy Advance (GBA), using mGBA.
- **Application layer:** Kotlin and Jetpack Compose, as indicated by the inspected APK.
- **Native layer:** C/C++ with Android NDK and JNI.
- **Future growth:** modular architecture that can accommodate additional console cores without rebuilding the application from scratch.
- **Languages:** English and Spanish.
- **Visual identity:** dark, premium interface inspired by deep Gengar-like purples, with restrained violet and pink/red accents.
- **Orientation-aware experience:** landscape mode evokes the Game Boy Advance; portrait mode evokes the Game Boy Advance SP.
- **Personalization:** selectable color themes and user-provided console-body skins.
- **Game library:** searchable collection, custom folders, favorites, pinned items, multi-select actions, and play history.
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

The repository currently contains project documentation and a signing-key setup workflow. The Android application source, Gradle project, C/C++ source, and reproducible build workflow still need to be brought into this repository.

The uploaded/tested APK is being used as a technical reference. Reconstructing source from a binary is necessarily incomplete; source files must be recovered from their original development environment or recreated and validated against the APK's observable behavior.

## Development sequence

1. Preserve and document the tested APK as a reference.
2. Recover or reconstruct the Kotlin/Compose app and native mGBA integration in source form.
3. Establish a reproducible Android/NDK build in GitHub Actions.
4. Run automated checks and compare generated APK behavior with the reference.
5. Continue feature work only on top of the reproducible source project.

## Legal and preservation note

NEXORA is intended to run user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files. Any third-party emulator core or library must be reviewed for license compatibility before integration.
