# NEXORA Roadmap

This roadmap distinguishes confirmed behavior, repository work, and future goals. A phase is complete only after its acceptance checks pass.

## Phase 0 — Product and prototype baseline

- [x] Establish the NEXORA repository and project documentation.
- [x] Define modular architecture principles.
- [x] Select Android as the first platform and GBA as the first system.
- [x] Select mGBA as the GBA core and C/C++ for native integration.
- [x] Produce a test APK containing Kotlin/Compose-related app classes and native mGBA-related code.
- [x] Confirm on the user's device that native in-game GBA saves persist after a full app restart.
- [ ] Recover or reconstruct the source corresponding to the tested APK.
- [ ] Record exact Android/Gradle/NDK and mGBA versions from source/build metadata.
- [ ] Establish reproducible CI builds from this repository.

**Exit check:** a maintainable source project that can produce a reproducible APK and preserves the validated save behavior.

## Phase 1 — Source recovery and reproducible Android build

- [ ] Recover original Kotlin, Compose, Gradle, C/C++, and CMake sources if available.
- [ ] If original source is unavailable, reconstruct the application incrementally against the reference APK.
- [ ] Restore the JNI bridge with verified signatures and lifecycle behavior.
- [ ] Integrate the mGBA source and document the exact upstream revision and license notices.
- [ ] Add GitHub Actions for debug APK builds and automated tests.
- [ ] Keep release signing credentials separate from ordinary CI.

**Exit check:** GitHub Actions can build the source project without private signing secrets, and the generated APK installs on the target ARM64 device.

## Phase 2 — Emulator stability and compatibility

- [ ] Verify ROM loading and unloading.
- [ ] Verify frame execution, frame dimensions, pixel format, and rendering.
- [ ] Investigate the previously reported Pokémon FireRed white/small-screen issue using logs and reproducible tests.
- [ ] Validate input mapping and virtual controls.
- [ ] Validate audio independently from video.
- [x] Preserve native in-game save persistence as a regression requirement.
- [ ] Test pause/resume, app backgrounding, and process recreation.

**Exit check:** multiple user-provided GBA test ROMs run reliably on real Android devices without UI freezes or corrupted saves.

## Phase 3 — Game library

- [ ] Import user-provided GBA files through Android's document picker.
- [ ] Build searchable library and metadata handling.
- [ ] Add custom folders, favorites, and pinned items.
- [ ] Add multi-select and batch actions.
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
