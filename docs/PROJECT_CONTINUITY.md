# NEXORA — Project Continuity and Engineering State

Last updated: 2026-11-16 (unified multiplayer + gameplay-tools integration)

This document is the durable handoff point for work across ChatGPT conversations. Update it whenever a meaningful implementation, decision, test result, or blocker changes. Do not rely on chat summaries as the only record.

## Product baseline

- Android-first GBA emulator using Kotlin/Jetpack Compose, JNI/C++, and mGBA.
- Keep UI, native emulator core, Android services, and future console integrations separated.
- Preserve existing ROM loading, battery saves, audio, rendering, and input behavior while adding features.
- Never merge a pull request or replace the user's accepted baseline without explicit approval.
- Build success is not the same as physical-device validation.

## Current source-control baseline

- Repository: `Cartago137/NEXORA`
- Unified working branch: `feature/nexora-unified-next-release`
- Unified draft PR: [#33 — Unify NEXORA Link Cable, gameplay tools and latest interface](https://github.com/Cartago137/NEXORA/pull/33)
- PR #33 is based on the latest UI/library/speed/D-pad branch and adds the existing Wi-Fi Link Cable/SIO work, native Save States, and the compact gameplay menu.
- Last confirmed integration commit at this handoff: `b538e6cc9ae300531abaebe61c5805b7261646ab` (CI workflow now runs JVM unit tests and watches the unified branch). Further commits may advance the head.
- Prior branches and draft PRs are intentionally preserved: #22 protocol foundation, #23 SIO investigation, #24 Wi-Fi room + first SIO driver, #29 Library folders, and #30 speed + continuous D-pad.
- Temporary mergeability-check PR #32 was closed after confirming the two old branches conflict; no source was merged through it.
- Do not merge PR #33 or any other work into `main` without the user's explicit approval.
- Build success is not the same as physical-device validation; no two-device Pokémon trade has yet been physically confirmed.

## Main navigation — landscape layout (latest user direction)

The user has now explicitly requested changing the previously discussed landscape navigation placement: the earlier left-side rail is superseded by a right-side vertical navigation rail.

- In portrait orientation, keep the primary navigation in its established bottom-bar arrangement.
- In landscape orientation, move the primary navigation (Principal/Home, Biblioteca, Configurações) to a vertical rail attached to the RIGHT edge of the display.
- The rail should extend along a substantial portion of the screen height, with its destinations stacked vertically and comfortably tappable. Keep its visual footprint organized and consistent; do not turn it into a large opaque panel that competes with the game artwork.
- The main content area occupies the remaining wider space to the LEFT of the rail. Give game covers, featured games, and library content visual priority and enough room; adapt grids and spacing to the available width.
- Preserve the current selected destination, navigation state, and scroll position when rotating between portrait and landscape.
- Apply the same responsive navigation behavior across Principal/Home, Biblioteca, and Configurações; avoid duplicating navigation controls.
- This is a product/UI requirement recorded for implementation; do not claim it is already implemented or physically validated unless code, build, and device checks confirm it.

## Previously implemented in the baseline (not yet confirmed by the user on-device)

- Portrait and landscape touch layouts.
- GBA A/B, D-pad, L/R, SELECT and START controls.
- Library folders, search, unfiled filter, multi-select, bulk move, favorites, pinned games/folders, and safe folder deletion.
- Responsive Library grid: three columns in portrait and five in landscape.
- Folder metadata is persisted separately from ROM files.
- Portuguese, English, and Spanish string resources.

The latest recorded build for PR #29 was GitHub Actions run `43524860366`, with release and debug APK artifacts reported as available. The user still needs to validate the build on the Motorola G54. Do not describe the build as device-tested.

## Current requested player-control corrections

### A. Runtime emulation speed

Required selectable speeds:
- 1.0x (normal/default)
- 1.5x
- 2.0x
- 3.0x

Requirements:
- Change speed while a game is running; no restart or ROM reload.
- 1.0x remains the default for every new session unless a persistent preference is later approved.
- Coordinate frame pacing and audio consumption with the selected speed. Avoid simply running extra frames while leaving audio playback at normal rate, which can create growing audio latency/buffering.
- Keep rendering responsive and do not regress save behavior or input.
- Add localized labels for supported app languages.
- Test switching repeatedly between all speeds and returning to 1.0x.

Implementation investigation:
- Current Kotlin player loop targets approximately 59.7275 GBA frames per second using `targetFrameNanos = 16_742_706L`.
- Current audio is drained by a background coroutine into Android `AudioTrack`.
- Current native bridge exposes `nativeRunFrame`, audio read/sample-rate methods, and key input methods.
- Choose and document a speed strategy that keeps frame pacing and audio queue behavior coherent. Verify the exact mGBA API before changing native timing.

### B. Continuous D-pad drag recognition

Required behavior:
- A finger may start on RIGHT and drag directly to LEFT (or any other direction) without lifting.
- The currently touched direction must replace the previous direction immediately.
- Support all four directions and diagonals when the touch lies between direction regions.
- Releasing/canceling the gesture clears the D-pad input.
- Do not release unrelated held buttons (A/B/L/R/START/SELECT) when changing D-pad direction.
- Avoid stuck keys when the app loses focus, a gesture is canceled, or the player exits.
- Preserve the approved D-pad appearance and current button mapping.

Implementation investigation:
- Current D-pad is composed of four separate `GbaControl` composables using `detectTapGestures`/press-release behavior.
- A continuous gesture needs one gesture owner for the whole D-pad surface, with the active direction recalculated from the finger's current coordinates.
- Update only the direction bits when the finger moves; preserve non-D-pad key bits already held by other controls.

## Gameplay tools menu — approved interaction model

The user reconfirmed on 2026-11-14 that optional gameplay actions must be grouped behind one compact menu button instead of being displayed as separate permanent controls.

- Add one dedicated function/menu button, visually represented by three horizontal lines (hamburger icon). This is one button, not separate speed buttons and not a row of extra gameplay controls.
- Tapping it opens a compact overlay/panel titled `NEXORA — MENU DE FUNÇÕES` over the running game.
- The panel contains optional actions:
  - Save State
  - Load State
  - Link Cable / Pokémon trade room
  - Fast Forward / emulation speed selector (1.0x, 1.5x, 2.0x, 3.0x)
  - Exit game
  - Future optional gameplay tools can be added in this same panel rather than placed directly over the game.
- Selecting an action should close the panel where appropriate; tapping outside closes it. Closing the panel resumes gameplay at the exact paused point/state.
- Keep the essential GBA controls visible and independent: D-pad, A, B, L, R, SELECT, and START. Do not replace or hide these with the tools menu.
- Keep Android Back available as a separate system navigation action.
- Visual direction: dark purple premium NEXORA overlay, restrained and compact, inspired by the function-menu interaction the user liked in My Boy!, but using NEXORA's own visual identity.
- The menu button should be positioned with the existing auxiliary controls (SELECT/START/function area) with ergonomic adaptation for portrait and landscape; it must not obstruct the game viewport or core controls.
- Implemented on `feature/nexora-unified-next-release`: one hamburger button opens an AlertDialog-style compact menu; speed options were moved out of the permanent HUD; Save State/Load State call mGBA named-state APIs; opening the menu stops frame/audio processing and closing it restarts the loop. The Link room is reachable from this same menu.
- Native state files are stored privately under `filesDir/save_states`, keyed by the imported game's URI hash. Save/Load is disabled during a Link session to avoid state divergence.
- Status: code is integrated but still requires Android CI, state-file round-trip testing, and physical-device validation. Do not describe the actions as verified until those checks pass.

## Original cat mascot — implemented in current feature branch

The user supplied an original hand-drawn cat mascot on 2026-11-13 and approved its use in the app.

- Source photo was rotated 90 degrees clockwise and cleaned into a transparent, orange-red vector drawable while preserving the hand-drawn character.
- Vector asset: `app/src/main/res/drawable/nexora_cat_mascot.xml`.
- Android adaptive launcher and round icons now use the cat on the deep-purple background: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
- Android 12+ splash screen uses the cat mascot through `values-v31/styles.xml`; the app theme is `NexoraTheme`.
- A small mascot mark is also shown in the app's top brand header.
- The geometric NEXORA identity, dark palette, and typography remain intact.
- About-screen branding remains a future refinement; the icon, splash, and header placements are implemented in this branch.
- Physical-device validation of launcher masks and splash rendering remains pending on the user's Motorola G54.

## Game box artwork — implemented in current feature branch

The user supplied a screenshot identifying four installed GBA games and approved using the matching cover references.

- Pokémon FireRed Version (Rev 1): original North American front box art, sourced from Bulbagarden Archives (`FireRed_EN_boxart.png`). The previous Walmart-hosted image was replaced after the user noticed that its Charizard artwork did not match the original cover.
- Mario & Luigi: Superstar Saga: North American front cover.
- Mega Man Battle Network 2 (PT-BR ROM): original GBA front cover; ROM localization does not change the cover art.
- Metroid: Zero Mission: North American front cover.
- Artwork lookup and disk caching are implemented in `app/src/main/java/com/nexora/app/GameArtwork.kt`. The FireRed URL change also changes its cache key, so the app will fetch the corrected cover rather than reuse the previous cached image.
- User requirement: always display the complete original cover image, preserving its aspect ratio. Use `ContentScale.Fit`, never `Crop` or stretch; any unused space belongs to the card background, not to a modified cover.
- Home featured-cover frame was corrected to match the square aspect ratio of the current FireRed source image, so the rounded outline hugs the artwork rather than retaining the previous taller portrait frame. If the featured source artwork changes to a different aspect ratio later, the frame must be made adaptive to that source.
- User requirement: do not overlay NEXORA text, logos, or watermarks on game cover artwork. NEXORA branding belongs to the surrounding interface only. The featured Home cover must follow this too.
- Artwork is downloaded on demand from the configured image hosts, then cached privately. The app requires an internet connection for the first download; failed/offline lookups retain the NEXORA fallback art and do not affect ROM loading or saves.
- Covers appear in the Home featured-game card, Home carousel, and Library cards.
- The cover images remain the property of their respective rights holders. They are fetched from third-party hosts rather than bundled as redistributable NEXORA assets. Review image-host stability and artwork rights before public distribution.
- Other game titles continue to use the existing fallback artwork until their own cover sources are configured.

## Validation checklist

### Build and static checks
- Android debug and release builds complete successfully.
- No Kotlin/Compose or JNI signature mismatch.
- No regressions to native library packaging/ABIs.
- Review GitHub Actions logs, not just the overall run badge.

### Physical-device checks (Motorola G54, Android 15)
- Portrait and landscape layouts.
- D-pad: RIGHT → LEFT drag without lifting; all direction transitions; diagonals; release and cancellation.
- Simultaneous D-pad + A/B and shoulder buttons.
- Speed: 1.0x, 1.5x, 2.0x, 3.0x; rapid switching; return to 1.0x; audio remains stable and does not accumulate delay.
- Existing ROM loading, frame rendering, audio, in-game saves, and save persistence after fully closing/reopening the app.
- Library folders and safe deletion behavior.
- Mascot launcher icon, adaptive icon crop, splash screen, and restrained in-app placements.

The user does not currently have a second phone for multiplayer testing. Never claim real two-device multiplayer validation without two physical devices.

## Conversation handoff protocol

At the end of every substantial work session:
1. Commit source changes to the active feature branch.
2. Update this file with the exact branch, PR, commit SHA, build run/status, artifacts, tests, and unresolved issues.
3. Keep a draft PR as the review/handoff entry point; do not merge without approval.
4. In the final chat response, provide a concise handoff: what changed, where it lives, what is verified, and the next concrete task.
5. When continuing in a new chat, first read this file and inspect the current branch/PR/Actions state before editing. Never assume a previous chat's build or test status is still current.

## Multiplayer / Pokémon Link Cable — unified status

- Historical implementation is preserved in PRs #22, #23 and #24; the latest branch was `feature/pokemon-link-room` (head `8ddfe6ceb74f205e95bc3d169ddabb179a5ee484`).
- The current unified branch carries forward:
  - NXLP v1 bounded binary protocol and decoder.
  - TCP host/join transport on the same local Wi-Fi network, port 38271.
  - Four-digit room code mapped to a session ID.
  - Handshake checks GBA game code and CRC32 ROM identity; both devices must use the same ROM revision.
  - JNI APIs for game code, ROM CRC, attach/detach, incoming serial transfer/result and outgoing event queue.
  - mGBA custom GBA MULTI SIO driver, with P0 host and P1 joiner.
  - Link room dialog accessible from the in-game functions menu.
- Engineering correction on 2026-11-16: removed the 4-second blocking wait from the P0 mGBA timing callback. It now polls the bounded native result queue by rescheduling a timing event every 2048 GBA cycles, with a 4-second failure deadline. This prevents network waiting from blocking the emulation thread. It still needs two-device timing validation; this is not proof of lockstep accuracy.
- Current scope is two devices on one LAN and GBA MULTI SIO only. No internet relay, Bluetooth, four-player session, UART, GPIO, JOYBUS or RFU support is claimed.
- The previous implementation documentation explicitly said a Pokémon trade had not been physically tested. This remains true. Required acceptance test: two Android devices running the exact same Pokémon FireRed ROM/revision, host/join, enter Cable Club on both games, complete and confirm an actual Pokémon trade, then verify both games' saves.
- The user currently does not have a second phone available for multiplayer testing. Automated tests and CI can validate protocol/build behavior, but cannot replace the physical two-device trade test.

## Save States — unified implementation

- mGBA 0.10.5 pinned revision exposes `mCoreSaveStateNamed` and `mCoreLoadStateNamed`; verified against upstream `include/mgba/core/core.h` and `include/mgba/core/serialize.h`.
- JNI functions `nativeSaveState(path)` and `nativeLoadState(path)` use `SAVESTATE_ALL` and app-private files.
- The player UI derives a per-ROM state filename from the imported URI hash.
- Save/Load is disabled while the Link Cable is connected; native functions also check the link connection under the same lock ordering used by SIO attach.
- Status: implemented in source, not yet build-verified or device-tested at this handoff. Test save → advance gameplay → load → confirm exact state restoration; also test app restart and missing/corrupt state files.

## Validation checklist

### Automated / CI
- Android debug APK build.
- Signed release APK build when the repository signing secrets are configured; otherwise record the unsigned release limitation.
- JVM tests for LinkProtocol and WifiLinkTransport.
- Native C++ compile for both configured ABIs.
- Verify JNI declarations match native exported function names.
- Inspect complete workflow logs and artifacts; do not rely on a green badge alone.

### Physical-device checks (Motorola G54, Android 15)
- Portrait and landscape layouts; D-pad drag transitions, diagonals, release/cancel, simultaneous A/B/L/R/START/SELECT.
- Speed 1.0x/1.5x/2.0x/3.0x, rapid switching, audio latency and return to normal.
- Save State/Load State round-trip, missing/corrupt state, and app restart.
- Link host/join on two devices, exact ROM compatibility, transfer timing, disconnect/reconnect, actual Pokémon FireRed Cable Club trade.
- Existing ROM loading, frame rendering, audio, native battery saves, and save persistence after fully closing/reopening the app.
- Library folders, favorites, pinned games, multi-select and safe deletion.
- Mascot launcher icon, adaptive icon crop, splash screen, and in-app placements.

The user does not currently have a second phone for multiplayer testing. Never claim real two-device multiplayer validation without two physical devices.

## Conversation handoff protocol

At the end of every substantial work session:
1. Commit source changes to the active feature branch.
2. Update this file with exact branch, PR, commit SHA, CI run/status, artifacts, tests, and unresolved issues.
3. Keep the PR as draft until automated checks and required physical tests are complete; do not merge without explicit approval.
4. In the final chat response, state what changed, what is verified, what remains unverified, and the next engineering task.
5. When continuing in a new chat, read this file and inspect the current branch/PR/Actions state before editing.

## Next engineering steps

1. Complete the first unified Android CI run; fix every compile/test failure and rerun until green.
2. Review and harden the SIO driver: transfer IDs, timeout, queue overflow, disconnect during active transfer, P0/P1 roles, and serial status/IRQ behavior.
3. Add native or integration-level SIO tests where feasible, plus protocol tests for fragmented frames, invalid lengths, sequence errors and reconnects.
4. Review Save State API behavior, file error handling and game identity key; run save/load round-trip tests.
5. Complete localization of the gameplay menu and Link room messages for Portuguese, English and Spanish.
6. Implement the remaining approved landscape navigation rail on the RIGHT side of the display, preserving the bottom navigation in portrait.
7. Review the artwork catalog and third-party notices; preserve full cover aspect ratios and avoid logos/watermarks on artwork.
8. Build debug/release APKs and validate on the Motorola G54.
9. Complete the actual two-device Pokémon FireRed trade acceptance test when a second Android device is available.
10. Update this document with exact CI runs, artifacts and test results.