# NEXORA — Project Continuity and Engineering State

Last updated: 2026-11-16 (NEXORA Android 0.4.0 CI and signed APK complete)

This is the durable engineering handoff for the Android NEXORA app. Update it after every meaningful implementation, test, or decision. Do not rely on chat history alone.

## Product identity and scope

- Product: NEXORA Android handheld emulator.
- First-release systems: Game Boy Advance (GBA), Game Boy (GB), and Game Boy Color (GBC).
- UI: Kotlin + Jetpack Compose; native emulation: C/C++ + JNI + mGBA.
- mGBA pinned commit: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.
- Languages: Portuguese, English and Spanish.
- Visual identity: NEXORA dark-purple premium gamer UI, original user-drawn cat mascot, restrained pink/violet accents.
- Never merge to `main`, publish a production release, or replace the user's accepted baseline without explicit approval.
- Build success is not physical-device validation.

## Source-control baseline

- Repository: `Cartago137/NEXORA`
- Current unified branch: `feature/nexora-unified-next-release`
- Current draft PR: [#33 — Unify NEXORA Link Cable, gameplay tools and latest interface](https://github.com/Cartago137/NEXORA/pull/33)
- App source release commit: `f9659941cb297757e26e35b2349533f0957a8606`.
- Android app version is `0.4.0` (`versionCode = 4`). Release notes: `docs/RELEASE_NOTES_0.4.0.md`.
- Previous complete green CI: run #475 / `43941142412`, version 0.3.0 (historical only).
- Final NEXORA 0.4.0 CI: run #519 / `43944029339`, source commit `f9659941cb297757e26e35b2349533f0957a8606`; status `success`. All 17 JVM tests passed, debug APK assembled, persistent release signing key prepared, and signed release APK assembled/uploaded.
- Final 0.4.0 debug artifact: `14264167074`.
- Final 0.4.0 signed release artifact: `14265325988`.
- Release artifact ZIP SHA-256: `c01e37c0ee8fde33571adc358ff7976a77e2480fe6f25b38094745556ae21efb`.
- Extracted signed APK SHA-256: `7258c7f33ea12485bedd9589a65ea107c9816f5d309155fde4001f8ed9072fb7`.
- APK contains `arm64-v8a` and `x86_64` native libraries and exports the expected JNI methods for frame rendering, active console dimensions/platform, Link attach and Save State/Load State.
- Historical feature branches and draft PRs are preserved for traceability: Link protocol/SIO (#22–#24), Library (#29), speed/D-pad (#30), and GB/GBC (#20). The unified PR #33 carries the combined work.
- Temporary conflict-check PR #32 was closed; it was not merged.
- No PR is to be merged without explicit user approval.

## Implemented in the unified branch (source integrated; physical validation still pending)

### Console support and video
- mGBA is configured with `M_CORE_GBA=ON` and `M_CORE_GB=ON`.
- `mCoreIsCompatible` detects GBA versus the shared GB/GBC core from the cartridge header; file extensions are not trusted for actual core selection.
- Native framebuffer uses a 256x224 maximum backing buffer and packs the active dimensions into Kotlin's IntArray.
- JNI exposes active width, height and platform.
- Player preserves native aspect ratio: GBA normally 240x160 (3:2); GB/GBC normally 160x144 (10:9).
- GB/GBC player hides GBA-only L/R shoulder controls and disables the GBA Link Cable action.
- ROM import accepts `.gb` and `.gbc` in addition to existing GBA formats.
- Library has All/GBA/GB/GBC filters and compact system labels. The filter currently classifies by filename extension; actual emulation selection uses the ROM header. Persisting detected platform metadata is future work.

### Home, Library and navigation
- Library folders, search, unfiled filter, favorites, pinned games/folders, multi-select, bulk move and safe folder deletion.
- Removing a game from the Library keeps the original file unless the user separately confirms deletion.
- Pinned games/folders remain at the top.
- Mascot appears in launcher/adaptive icon, splash and in-app header.
- Selected game cover art preserves full aspect ratio with `ContentScale.Fit`; no NEXORA overlays or watermarks are placed on cover artwork.
- Portrait uses the established bottom navigation.
- Landscape now uses a vertical navigation rail on the RIGHT edge; the content remains to its left. The Activity handles orientation/screen-size configuration changes so current Compose state and native session are not discarded during rotation.

### Player controls and gameplay function menu
- Runtime emulation speed: 1.0x, 1.5x, 2.0x and 3.0x; default resets to 1.0x for each new game session.
- Frame pacing and Android AudioTrack playback rate are adjusted together.
- D-pad is one continuous gesture surface: finger can drag between directions and diagonals without lifting; only D-pad bits change, preserving held A/B/L/R/START/SELECT.
- One hamburger button opens the compact localized `NEXORA — MENU DE FUNÇÕES` overlay.
- Menu actions: Save State, Load State, speed selector, Link Cable / Pokémon trade, and Exit game.
- Opening the menu pauses frame/audio processing; closing resumes the game. Android Back remains a separate action.
- Save/Load closes the menu after the operation and reports the result.

### Save States
- JNI calls mGBA `mCoreSaveStateNamed` / `mCoreLoadStateNamed` with `SAVESTATE_ALL`.
- Files live in app-private `filesDir/save_states` and use a SHA-256 identifier derived from the imported ROM URI.
- Save writes to a temporary file and atomically replaces the previous state only after successful serialization.
- Native Save/Load refuses while the remote SIO driver is still attached; UI disables the actions during a Link session or teardown.
- Source implementation is integrated. Still required: save → advance → load round-trip; missing/corrupt file behavior; app restart persistence; test on both GBA and GB-family ROMs.

### Pokémon Link Cable
- NXLP v1 bounded binary protocol, incremental TCP decoder and local Wi-Fi TCP host/join on port 38271.
- Four-digit room code, session handshake, player roles, game code and ROM CRC32 identity validation. Both devices must use the exact same ROM/revision.
- JNI queues connect the transport to a custom mGBA GBA MULTI 16-bit SIO driver: P0 host, P1 joiner.
- Link room is accessible from the in-game function menu.
- Native SIO callback never performs socket I/O and no longer blocks on a condition variable. P0 polls the bounded result slot every 2048 GBA cycles with a 4-second timeout.
- P1 fails a pending transfer if the peer disconnects rather than reporting false success.
- Kotlin transport has bounded handshake reads, cancellable host acceptance, tracked pending connect jobs, serialized teardown/reconnect, and Ping/Pong heartbeat with a 10-second response deadline.
- JVM tests cover protocol framing/validation, host/join, incompatible game/ROM rejection, cancellation while hosting, and serial transfer/result transport.
- Important limitation: the direct remote SIO implementation is not proven deterministic distributed lockstep. While SIOCNT is BUSY, a local core may continue advancing until the remote result arrives. A successful build or TCP test does not prove Pokémon trade correctness.

## Required validation and known blockers

The user currently does not have a second phone available. Do not claim an actual Pokémon trade was physically validated.

### Automated
- Run `:app:testDebugUnitTest`.
- Build debug and release APKs; inspect complete Actions logs and artifacts.
- Verify both configured native ABIs and JNI exports.
- Re-run CI after all current Link hardening commits; run #475 predates those changes.
- Review test output and fix every failure, then repeat.

### Motorola G54 (Android 15)
- GBA, GB and GBC ROM loading, frame dimensions/aspect ratio, audio, controls and native battery saves.
- Speed switching 1.0x/1.5x/2.0x/3.0x and audio latency.
- D-pad directional transitions, diagonals, release/cancel and simultaneous buttons.
- Save State/Load State round-trip, corrupt/missing state and app restart.
- Portrait/landscape Home, Library, Settings, right rail, scroll position and game continuity.
- Library filters, folders, pin/favorite, multi-select and safe deletion.
- Mascot icon, splash and artwork caching.

### Two-device Pokémon acceptance
- Two Android devices on the same Wi-Fi, same NEXORA build, same exact Pokémon FireRed ROM/revision.
- Host/join; enter Cable Club on both devices; complete and confirm an actual Pokémon trade.
- Verify both games' battery saves, repeat a second trade, test disconnect during transfer and reconnect.
- If transfer timing or game state diverges, replace the direct asynchronous event approach with a stricter distributed SIO/lockstep design before calling the feature ready.

## Handoff protocol

1. Inspect current PR, branch and Actions before editing.
2. Work in focused stages; after each stage inspect diffs, run relevant tests, fix issues and repeat.
3. Commit all source and documentation changes to the unified feature branch.
4. Record exact commit, CI run, artifacts, tests and remaining physical checks here.
5. Keep PR #33 draft until acceptance criteria are met. Never merge without explicit approval.
6. Clearly distinguish the Android NEXORA app from the separate NEXORA AI Control Center.

## Next engineering steps

1. Keep the 0.4.0 signed APK and source commit aligned; do not replace it with a different build without updating release notes.
2. Continue reviewing native SIO transfer ordering, BUSY/READY, IRQ, player role and disconnect behavior; add native tests where feasible.
3. Validate Save State round-trip and GB/GBC emulation on physical hardware when available.
4. Complete the Motorola G54 regression checklist (speed, audio, D-pad, Library, orientation, mascot and artwork).
5. Complete two-device FireRed Cable Club trade acceptance before describing Pokémon exchange as fully ready.
