# NEXORA — Project Continuity and Engineering State

Last updated: 2026-11-13 (player speed and continuous D-pad implementation started)

This document is the durable handoff point for work across ChatGPT conversations. Update it whenever a meaningful implementation, decision, test result, or blocker changes. Do not rely on chat summaries as the only record.

## Product baseline

- Android-first GBA emulator using Kotlin/Jetpack Compose, JNI/C++, and mGBA.
- Keep UI, native emulator core, Android services, and future console integrations separated.
- Preserve existing ROM loading, battery saves, audio, rendering, and input behavior while adding features.
- Never merge a pull request or replace the user's accepted baseline without explicit approval.
- Build success is not the same as physical-device validation.

## Current source-control baseline

- Repository: `Cartago137/NEXORA`
- Existing integration branch: `feature/library-folders`
- Existing draft PR: [#29 — Restore touch controls and add library folders](https://github.com/Cartago137/NEXORA/pull/29)
- PR #29 contains the restored touch-control layout and the Library folder work. It has not been merged.
- New working branch: `feature/player-speed-and-touch-gestures`
- New draft PR: [#30 — Add runtime emulation speed and continuous D-pad gestures](https://github.com/Cartago137/NEXORA/pull/30)
- Implementation commits: `7432b8619f9d00f61370a622c7bedc07cc9cb8a0`, `7b2be717f3c8556b5af5515a7ed737451607cb0a`
- CI trigger correction commit: `5e8c69e033d62b65683bf74491bc3c00803297d0`
- New work must branch from the latest accepted development line and remain in a draft PR until reviewed and physically tested.
- Do not merge PR #29 or the new work without the user's explicit approval.

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

The user does not currently have a second phone for multiplayer testing. Never claim real two-device multiplayer validation without two physical devices.

## Conversation handoff protocol

At the end of every substantial work session:
1. Commit source changes to the active feature branch.
2. Update this file with the exact branch, PR, commit SHA, build run/status, artifacts, tests, and unresolved issues.
3. Keep a draft PR as the review/handoff entry point; do not merge without approval.
4. In the final chat response, provide a concise handoff: what changed, where it lives, what is verified, and the next concrete task.
5. When continuing in a new chat, first read this file and inspect the current branch/PR/Actions state before editing. Never assume a previous chat's build or test status is still current.

## Next engineering steps

1. Implementation is now present on `feature/player-speed-and-touch-gestures`: live 1.0x/1.5x/2.0x/3.0x selector, frame pacing scaling, AudioTrack playback-rate scaling, and one continuous D-pad gesture surface.
2. CI workflow triggers were expanded so pushes to the feature branch and pull requests targeting `feature/library-folders` run Android builds.
3. Current build run: PR run `43529338911` was started against the earlier implementation commit; a fresh run for the latest head must be confirmed. The earlier duplicate run `43529324514` was cancelled by GitHub Actions concurrency.
4. Review compiler/build results and fix any issues before calling the implementation build-ready.
5. Add/update unit or instrumentation tests where practical and document what cannot be tested without the physical device.
6. Build debug and release APKs in GitHub Actions.
7. Update this handoff file with the exact CI run, artifacts, and test status.
8. Ask the user to test the APK on the Motorola G54; only then mark physical validation complete.
