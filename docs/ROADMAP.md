# NEXORA Roadmap

This roadmap separates product goals from verified implementation. A phase is complete only after its acceptance checks pass.

## Phase 0 — Foundation

- [x] Create repository documentation.
- [x] Define modular architecture principles.
- [x] Record major product goals and open decisions.
- [ ] Choose the application framework and language.
- [ ] Choose and review a GBA core and its license.
- [ ] Define supported Android versions and a test-device baseline.

**Exit check:** a documented technical plan with license review and a minimal buildable application.

## Phase 1 — Application shell

- [ ] Create the Android project and repeatable build instructions.
- [ ] Establish navigation and the dark NEXORA design system.
- [ ] Add English and Spanish localization foundations.
- [ ] Implement orientation-aware layouts.
- [ ] Add settings and theme selection foundations.

**Exit check:** the app builds and navigates reliably in portrait and landscape.

## Phase 2 — Game library

- [ ] Import user-provided GBA files through Android's document picker.
- [ ] Build searchable library and metadata handling.
- [ ] Add custom folders, favorites, and pinned items.
- [ ] Add multi-select and batch actions.
- [ ] Implement play history.
- [ ] Ask before deleting an original file when removing a library entry.
- [ ] Ensure deleting a folder does not delete its games.

**Exit check:** library behavior is tested, including file-safety and batch-action cases.

## Phase 3 — First playable GBA integration

- [ ] Integrate a license-compatible GBA core behind the emulator abstraction.
- [ ] Load and unload user-provided games.
- [ ] Render video and play audio.
- [ ] Map touch controls and physical controllers where supported.
- [ ] Persist save data safely.
- [ ] Test performance and compatibility on real Android devices.

**Exit check:** a legally obtained test game runs reliably, with repeatable save/load behavior.

## Phase 4 — Premium play experience

- [ ] Build the landscape GBA-inspired console layout.
- [ ] Build the portrait GBA SP-inspired layout.
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
