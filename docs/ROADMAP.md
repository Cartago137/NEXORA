# NEXORA Roadmap

This roadmap distinguishes confirmed behavior, repository work, and future goals. A phase is complete only after its acceptance checks pass.

## Phase 0 — Product and prototype baseline

- [x] Establish the NEXORA repository and project documentation.
- [x] Define modular architecture principles.
- [x] Select Android as the first platform and GBA as the first system.
- [x] Select mGBA as the GBA core and C/C++ for native integration.
- [x] Produce a test APK containing Kotlin/Compose-related app classes and native mGBA-related code.
- [x] Confirm on the user's device that native in-game GBA saves persist after a full app restart.
- [x] Locate the exact source revision embedded in the APK (`17bcb65bad01e23790605ef4ec32fbdb173e637a`) in the preserved GitHub branches.
- [x] Restore the Kotlin/Compose app, JNI/C++ bridge, CMake integration, Gradle files, and license assets in a recovery branch.
- [x] Identify the pinned mGBA source commit and Android/Gradle/NDK build versions.
- [x] Confirm a successful Android debug build on the later `feature/audio-saves-orientation` branch.
- [x] Merge the source restoration into `main`.
- [ ] Confirm the mainline build workflow completes and uploads the APK.
- [ ] Install the new APK and validate it on the target ARM64 phone.

**Exit check:** a maintainable source project that can produce a reproducible APK and preserves the validated save behavior.

## Phase 1 — Source recovery and reproducible Android build

- [x] Recover the original source revision and preserve the reference APK's source history.
- [x] Restore Kotlin/Compose, Gradle, C/C++, JNI, and CMake source from the later CI-verified development branch.
- [x] Restore the mGBA integration pinned to upstream commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (project version label 0.10.5).
- [x] Restore the GitHub Actions debug/release build workflow and keep release signing separate from ordinary CI.
- [ ] Pass the build workflow on the source-restoration pull request and merge it into `main`.

**Exit check:** GitHub Actions can build the source project without private signing secrets, and the generated APK installs on the target ARM64 device.

## Phase 2 — Emulator stability and compatibility

- [ ] Verify ROM loading and unloading.
- [x] Restore the reusable frame buffer, frame pacing, and native color-channel conversion correction from the preserved rendering-fix branch.
- [ ] Verify frame execution, frame dimensions, pixel format, and rendering on the target phone.
- [ ] Confirm whether the rendering correction resolves the previously reported Pokémon FireRed white/small-screen issue.
- [ ] Validate input mapping and virtual controls.
- [x] Restore the Android AudioTrack pipeline and native audio-buffer handling.
- [x] Restore per-game battery-save file loading and persistence integration.
- [x] Preserve native in-game save persistence as a regression requirement.
- [ ] Revalidate audio and save persistence on the new APK.
- [ ] Test pause/resume, app backgrounding, and process recreation.

**Exit check:** multiple user-provided GBA test ROMs run reliably on real Android devices without UI freezes or corrupted saves.

## Phase 3 — Game library

- [x] Import multiple user-provided GBA files through Android's document picker (initial multi-import implementation).
- [ ] Expand the library with custom folders, favorites, pinned items, search, and batch management.
- [ ] Build searchable library and metadata handling.
- [ ] Implement play history.
- [ ] Ask before deleting an original file when removing a library entry.
- [ ] Ensure deleting a folder does not delete its games.

**Exit check:** library behavior is tested, including file-safety and batch-action cases.

## Phase 4 — Premium play experience

- [ ] Refine the dark NEXORA design system and navigation.
- [ ] Complete English and Spanish localization.
- [ ] Build landscape GBA-inspired and portrait GBA SP-inspired layouts.
- [ ] Add configurable colors and user-provided body skins.
- [ ] Add the featured game's subtle floating 3D box presentation.
- [ ] Add responsive controls, haptics, and accessibility options.
- [ ] Verify orientation changes without losing selection or emulation state.

**Exit check:** both orientations work reliably and customization persists.

## Phase 5 — Advanced hardware and connectivity

- [ ] Investigate motion/tilt sensor support for compatible games.
- [ ] Investigate rumble-to-device-vibration mapping.
- [ ] Prototype local link functionality.
- [ ] Prototype device-to-device Wi-Fi multiplayer.
- [ ] Test synchronization, latency, disconnect recovery, and compatibility.
- [ ] Document unsupported games and device limitations.

**Exit check:** each advertised capability has a tested compatibility list and clear limitations.

## Phase 6 — Expansion readiness

- [ ] Validate the emulator abstraction with a second system prototype.
- [ ] Keep system-specific assets, input profiles, and capabilities modular.
- [ ] Add regression tests to prevent changes for one system from breaking another.
- [ ] Review performance, security, privacy, and third-party licenses.

**Exit check:** a second system can be added without rewriting the application foundation.

## Release discipline

Do not mark a feature complete because a screen or placeholder exists. Require a working implementation, tests on supported devices, documented limitations, and a review of relevant licenses.
