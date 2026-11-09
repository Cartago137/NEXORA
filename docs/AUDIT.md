# NEXORA — Technical Audit

**Audit date:** 2026-11-08  
**Repository:** `Cartago137/NEXORA`  
**Audited branch:** `foundation/android-app-shell`  
**Scope:** all tracked application, native, build, CI, localization, documentation, and third-party notice files in the branch, plus the open draft pull request and GitHub Actions history.

## Executive result

The repository is structurally buildable, but it is not yet a verified playable emulator. Earlier APKs closing immediately after the player screen appeared is consistent with an incomplete mGBA startup sequence. The native integration follows the upstream frontend setup order: initialize the core configuration, apply defaults, set the video buffer before loading the ROM, load the ROM, and reset the core before the first frame. A further critical BIOS-free boot issue was found: `useBios` was false but `skipBios` was not enabled, leaving the CPU to enter the BIOS path without a BIOS image. The frontend now explicitly skips BIOS and starts the cartridge directly. Native logs also identify each startup stage and the first frame.

The branch also had independent input and safety issues. Those have been corrected in source. The remaining release blockers are real-device validation, restoring audio, and implementing battery-backed saves. A green CI build alone cannot prove that a game is playable on the user's phone.

## Findings and resolutions

| ID | Severity | Finding | Resolution / next action | Status |
|---|---|---|---|---|
| NEX-01 | Critical | The native bridge loaded a ROM and started calling `runFrame` without following mGBA's full frontend initialization sequence. It also configured the video buffer after ROM loading and never explicitly reset the core. | Initialize mGBA config, apply default options, configure video before `loadROM`, then call `core->reset` before publishing the active core. Added native log markers around startup and the first frame. | Fixed in source; awaiting device validation |
| NEX-02 | High | The app trusted file extensions. A file named `.gba` could contain arbitrary bytes; mGBA's low-level `loadROM` maps data but is not itself the frontend's ROM-identification step. | Call the core's `isROM` check before loading. Reject files without a valid GBA ROM signature. | Fixed in source |
| NEX-03 | High | Touch controls replaced the complete key mask on every press and released every key when any one control was lifted. This prevented reliable combinations such as holding a direction and pressing A. | Added native add-key and clear-key calls; each touch control now adds and releases only its own bit. | Fixed in source |
| NEX-04 | High | ROM files were copied to cache without an upper bound. A very large or malformed file could consume excessive storage. | Limit cache copies to 64 MiB and independently reject ROMs outside the native size bounds. | Fixed in source |
| NEX-05 | High | The gameplay frame loop could continue running while the app was in the background. | Pause frame execution when the Activity is not resumed and clear held keys on pause. | Fixed in source; device lifecycle test pending |
| NEX-06 | High | An orientation change could recreate the Activity, lose the player-screen state, and orphan the native session/cache-file reference. | Handle orientation/screen-size configuration changes without recreating the Activity so the existing session remains owned by the same Activity. | Fixed in source; rotation test pending |
| NEX-07 | Medium | The native status check created and initialized a core but did not release it on success. | Deinitialize the config and core after the status check. The active-game cleanup path also releases config resources in the same order used by mGBA's frontend. | Fixed in source |
| NEX-08 | Medium | Native crashes were difficult to localize because the JNI boundary had no stage markers. | Added Android log markers for successful ROM/core setup and the first frame before and after execution. | Fixed in source; log capture still needed if a device crash persists |
| NEX-09 | Medium | The app originally allocated a new Java pixel array and Android bitmap for every frame. This created avoidable garbage-collection pressure. | Changed JNI to write into a reusable Java pixel array and update a reusable Android bitmap. | Fixed in source; performance test pending |
| NEX-10 | High | Audio was part of the previous player startup path and may have contributed to the observed crash. | Audio is deliberately disabled in the current diagnostic build. Restore it only after the video-only core path is confirmed stable; initialize the mGBA audio buffer and Android audio output defensively. | Open / intentionally disabled |
| NEX-11 | High | Battery-backed cartridge saves were absent from the first player implementation. | Added a per-game private `.sav` path and connected it to mGBA's `mCoreLoadSaveFile`; mGBA owns the mapped save file and closes it when the ROM/core is unloaded. Verify real SRAM/Flash/EEPROM writes survive app exit and restart. | Implemented in source; persistence test pending |
| NEX-12 | Medium | Save states are not implemented. | Add save/load state support after the battery-save path is reliable; test corrupt and incompatible state files. | Open / later milestone |
| NEX-13 | High | The frame loop did not expose a useful UI error if JNI returned no frame; the screen could remain on its startup placeholder. | Add an explicit player error state and actionable message for a failed frame/native session. Native SIGSEGVs still require Logcat because Kotlin exception handling cannot catch a process-level native fault. | Open |
| NEX-14 | High | CI previously compiled the APK but did not test emulator runtime behavior or a physical device. | Keep CI as a compile gate, then validate installation, ROM load, first frame, controls, pause/resume, rotation, and long-session stability on the target ARM64 phone. | Open / release blocker |
| NEX-15 | Medium | Enabling both push and pull-request events for the same feature branch created duplicate CI runs. | Use pull-request events for feature-branch validation and push events for main only; share a branch-name concurrency key so repeated PR updates replace older runs. | Fixed in workflow; verify on the next PR update |
| NEX-16 | Critical | The frontend disabled BIOS loading but left `skipBios` false. With no BIOS image available, mGBA could enter the BIOS boot path instead of starting the cartridge directly. | Explicitly set `mCoreOptions.skipBios = true` for the BIOS-free frontend. Add detailed native log markers for ROM setup, save loading, and the first frame. | Fixed in source; awaiting device validation |

## Build and repository observations

- The mGBA source is pinned to commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (mGBA 0.10.5); the build does not float to an unreviewed upstream revision.
- Android targets `arm64-v8a` and `x86_64`, with min SDK 26, target SDK 36, Java 17, NDK 27.2.12479018, and CMake 3.22.1.
- The GitHub Actions workflow builds a debug APK. A successful build verifies compilation/linking for the configured ABIs, not runtime correctness.
- The CI log for the previous successful diagnostic build contained an Android SDK package-path warning and upstream action Node-runtime deprecation warnings. They did not fail that build, but the workflow should be refreshed when the core runtime is stable.
- Portuguese and English resource files exist. Spanish is not part of the agreed first-release language scope.
- The library uses Android's Storage Access Framework and persists document URI grants. Actual provider-specific permission and deletion behavior still needs device testing.
- The draft PR remains open and unmerged. Its description has been corrected to reflect the current diagnostic state.

## Features that are not defects in this milestone

Search, custom folders, favorites, pinned games, multi-select, full play history, configurable themes/skins, motion sensors, rumble, link cable, and Wi-Fi multiplayer are planned features, not promised features in the current app shell. They should not be marked complete until separately implemented and tested. Upstream mGBA documents GBA rumble support in compatible titles, but a frontend must wire the hardware interface; network multiplayer and sensor behavior require separate NEXORA engineering.

## Acceptance checklist for a genuinely playable build

1. CI completes successfully for the exact final commit.
2. Install the APK on the target ARM64 phone.
3. Import a known-good, legally user-supplied GBA ROM and verify header validation accepts it.
4. Start the game and confirm the first rendered frame appears without closing the app.
5. Confirm D-pad and A/B can be held simultaneously and buttons release correctly.
6. Run for at least 10–15 minutes without crash, stuck input, or severe slowdown.
7. Background and resume the app; confirm the game pauses and resumes cleanly.
8. Rotate the phone during play; confirm the same session remains active.
9. Restore audio and confirm stable playback without crackling or crashes.
10. Save progress in a game, exit NEXORA, reopen it, and confirm the battery save is restored.

**Release rule:** do not describe the APK as a finished playable emulator until these checks pass on a physical device. The current diagnostic build is an engineering checkpoint, not the final release.
