# NEXORA Roadmap

A phase is complete only after implementation, review, automated tests and the required physical-device acceptance checks. Code present in a draft PR is not automatically a released feature.

## Phase 0 — Product and source baseline

- [x] Restore the Kotlin/Compose app, JNI/C++ bridge, Gradle/CMake project and pinned mGBA integration.
- [x] Preserve the tested native GBA battery-save behavior as a regression requirement.
- [x] Establish GitHub Actions Android build workflow.
- [x] Define a modular architecture and no-merge-without-approval rule.

## Phase 1 — Unified first-release systems

- [x] Enable mGBA GBA core.
- [x] Enable shared GB/GBC core in CMake.
- [x] Detect GBA vs GB-family ROM from cartridge header.
- [x] Expose active video dimensions/platform through JNI.
- [x] Use dynamic frame buffers and preserve 3:2 GBA / 10:9 GB-family aspect ratios.
- [x] Accept .gb and .gbc ROM files.
- [x] Add All/GBA/GB/GBC Library filter and compact system labels.
- [ ] Verify GB, GBC and GBA gameplay, audio, input and battery saves on the Motorola G54.

**Exit check:** known-good GBA, GB and GBC ROMs run correctly on physical Android hardware and preserve their native saves.

## Phase 2 — Library and identity

- [x] Library folders, search, favorites, pinned games/folders, multi-select and safe folder deletion.
- [x] Unified console filter.
- [x] Original hand-drawn cat mascot in launcher, splash and header.
- [x] Known cover art for selected GBA games, with aspect-ratio preservation and private cache.
- [ ] Validate the Library interactions on device.
- [ ] Persist detected platform from ROM headers rather than relying on extensions for the console filter.
- [ ] Expand artwork catalog after reviewing source stability and rights.

## Phase 3 — Player controls and gameplay tools

- [x] Runtime speed choices 1.0x, 1.5x, 2.0x and 3.0x.
- [x] Frame pacing and AudioTrack playback-rate scaling.
- [x] Continuous D-pad drag transitions and diagonals without lifting.
- [x] One compact hamburger function menu; speed selector moved out of the permanent HUD.
- [x] Native Save State / Load State through mGBA named-state API.
- [ ] Verify Save State round-trip, atomic replacement, missing/corrupt files and app restart.
- [ ] Verify speed switching and audio stability on device.
- [ ] Verify simultaneous D-pad and A/B/L/R/START/SELECT input on device.
- [ ] Implement the approved landscape navigation rail on the RIGHT edge while preserving the portrait bottom bar.

## Phase 4 — Pokémon Link Cable / local multiplayer

- [x] NXLP v1 bounded protocol and incremental decoder.
- [x] TCP host/join on local Wi-Fi with room code and IP.
- [x] Game code + ROM CRC32 compatibility handshake.
- [x] JNI incoming/outgoing queues and mGBA GBA MULTI SIO driver.
- [x] Replace blocking SIO callback wait with bounded non-blocking polling.
- [x] Integrate Link room entry into the in-game function menu.
- [ ] Review native SIO sequence, BUSY/READY, player IDs, IRQ and timeout behavior.
- [ ] Add native/integration tests for queue overflow, disconnect during transfer and active unload.
- [ ] Test two physical devices on the same LAN with the exact same Pokémon FireRed ROM/revision.
- [ ] Complete a real Cable Club Pokémon trade and verify both battery saves.
- [ ] Move to distributed mGBA lockstep if the direct remote-SIO timing proves insufficient.

**Exit check:** actual two-device trade completed without desynchronization or corrupted saves. Protocol/build tests alone do not satisfy this gate.

## Phase 5 — Stability and release candidate

- [x] Android CI: 17 JVM tests, debug APK and signed release APK passed on NEXORA 0.4.0 source commit `f9659941cb297757e26e35b2349533f0957a8606` (run #519).
- [ ] Physical regression: GBA, GB, GBC, audio, frame rendering, native saves and Save States.
- [ ] Physical regression: controls, speed, orientation, Library and mascot.
- [ ] Two-device Link Cable acceptance test.
- [ ] Review permissions, privacy, security, third-party notices and artwork licensing.
- [ ] Update continuity documentation and release notes with exact commit, artifacts and validation results.

## Release discipline

Keep PR #33 as a draft until all applicable automated and physical acceptance tests are complete. Never merge into `main` without the user's explicit approval.


## Phase 3A — Personalizable touch controls and settings (0.4.5)

- [x] Persist touch-control preferences independently from ROM and save data.
- [x] Expose D-pad, A/B, L/R size controls.
- [x] Expose grouped position controls for D-pad, A/B, L/R and MENU/SELECT/START.
- [x] Expose MENU/SELECT/START button sizing.
- [x] Expose overall control opacity and reset-to-default.
- [x] Apply the same preferences in portrait and landscape.
- [ ] Physical acceptance on Motorola G54: verify D-pad size, touch hit area, layout bounds and saved preferences after app restart.
- [ ] Extend grouped positioning into free per-button drag placement and multiple named layouts.
- [ ] Validate Library, History and Settings navigation/scrolling in short landscape viewports.

## Phase 3B — Visual touch-layout editor and settings organization (0.4.6–0.4.7)

- [x] Add independent size and position preferences for D-pad, A, B, L, R, MENU, SELECT and START.
- [x] Add a visual editor that reuses the approved control atlas.
- [x] Support one-finger drag and two-finger pinch resizing in the editor.
- [x] Keep touch preferences isolated from ROM metadata, battery saves and Save States.
- [x] Split Settings into focused category screens and move touch customization into its own editor screen.
- [x] Add vertical scrolling to Library and History for compact landscape layouts.
- [x] Rebuild the editor preview using the same portrait/landscape control grouping as the player screen.
- [x] Make the control editor a clean, full-screen gameplay-like surface without a title, brand mark, reset action, or instructional/status captions.
- [x] Move opacity into Settings > Controls, separate from position and size.
- [ ] Validate every control's editor-to-game position and size on the Motorola G54 in portrait and landscape.
- [ ] Validate Library and History scrolling with empty, filtered and populated content in landscape.
- [ ] Add named profiles and separate portrait/landscape layouts.
- [ ] Run automated Android build and regression tests before release.
