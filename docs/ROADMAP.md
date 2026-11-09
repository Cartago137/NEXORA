# NEXORA Roadmap

This roadmap separates product goals from verified implementation. A phase is complete only after its acceptance checks pass.

## Phase 0 — Foundation

- [x] Create repository documentation.
- [x] Define modular architecture principles.
- [x] Record major product goals and open decisions.
- [x] Choose Kotlin + Jetpack Compose for Android.
- [x] Choose mGBA as the GBA core and record its licensing/integration constraints.
- [x] Define the Android build baseline (min SDK 26, ARM64 and x86_64 build targets).

**Exit check:** a documented technical plan and a minimal buildable application.

## Phase 1 — Application shell

- [x] Create the Android project and repeatable CI build.
- [x] Establish navigation and the dark NEXORA design system.
- [x] Add Portuguese (default) and English localization resources.
- [x] Hide the Android status bar for an immersive app window.
- [ ] Implement orientation-aware layouts.
- [ ] Add functional settings and theme selection.

**Exit check:** the app builds and navigates reliably in portrait and landscape, with no system status bar covering the interface.

## Phase 2 — Game library

- [x] Import user-provided GBA files through Android's document picker.
- [x] Persist library entries and document access permissions across restarts.
- [x] Show imported game names and file sizes.
- [x] Ask whether to delete the original document when removing a library entry.
- [ ] Validate ROM content and show game metadata/artwork.
- [ ] Add library search.
- [ ] Add custom folders, favorites, and pinned items.
- [ ] Add multi-select and batch actions.
- [ ] Implement play history.
- [ ] Test file permissions and deletion behavior across Android document providers.

**Exit check:** library behavior is tested, including file-safety, persistence, and batch-action cases.

## Phase 3 — First playable GBA integration

- [x] Pin and compile mGBA 0.10.5 as a separate Android shared library.
- [ ] Verify native GBA core initialization on a physical Android device.
- [ ] Wrap the core behind the system-independent emulator abstraction.
- [ ] Load and unload user-provided games through the native core.
- [ ] Render video and play audio.
- [ ] Map touch controls and physical controllers where supported.
- [ ] Persist save data safely.
- [ ] Test performance and compatibility on real Android devices.

**Exit check:** a legally obtained test game runs reliably, with repeatable save/load behavior.

## Phase 4 — Premium play experience

- [ ] Build the landscape GBA-inspired console layout.
- [ ] Build the portrait GBA SP-inspired console layout.
- [ ] Add configurable colors and user-provided body skins.
- [ ] Add the featured game's subtle floating 3D box presentation.
- [ ] Add responsive controls, haptics, and accessibility options.

**Exit check:** both orientations work without losing session state, and customization persists.

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

## Current implementation checkpoint — Library foundation

**Status:** implemented on `foundation/android-app-shell`; CI and physical-device validation are required.

Implemented in this checkpoint:
- Full-screen window with the Android status bar hidden.
- Portuguese default and English localized resources.
- Android document picker for `.gba`, `.agb`, and `.bin` files.
- Persisted document access and a local library index.
- Duplicate URI protection.
- Separate actions to remove a library entry or remove it and request deletion of the original document.

Not yet implemented:
- Actual mGBA emulation, game execution, video/audio, controls, and save states.
- Search, folders, favorites, pins, multi-select, metadata, artwork, and play history.
- Verified behavior on a physical Android device and across document-provider apps.

Next engineering milestone: verify the linked mGBA core on a physical Android device, then implement ROM loading from the stored document URI and a dedicated emulation thread with video, audio, and input.
