# NEXORA

NEXORA is an Android-first handheld emulator project targeting **Game Boy Advance (GBA), Game Boy (GB), and Game Boy Color (GBC)** through mGBA. It combines a custom Kotlin/Jetpack Compose interface, a native C/C++ emulation layer, and a modular foundation for future systems.

> **Development status:** the latest unified work lives on `feature/nexora-unified-next-release` and draft PR [#33](https://github.com/Cartago137/NEXORA/pull/33). It combines the latest Home/Library/player work, Wi-Fi GBA Link Cable integration, Save States, and GB/GBC core support. This is a development build, not a released or physically validated version.

## Product direction

- **Platform:** Android first.
- **First-release systems:** GBA, GB, and GBC.
- **Emulation:** mGBA pinned to upstream commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` (project version label 0.10.5).
- **Application:** Kotlin + Jetpack Compose.
- **Native layer:** C/C++ with Android NDK, CMake and JNI.
- **Languages:** Portuguese, English and Spanish resources.
- **Visual identity:** original NEXORA dark-purple interface with violet and pink accents; the user's hand-drawn cat mascot is used in the launcher/splash/header.
- **Library:** unified GBA/GB/GBC collection, console filter, search, folders, favorites, pinned items and multi-selection.
- **Player:** responsive layouts, continuous D-pad drag, GBA-only shoulder buttons, and selectable 1.0x/1.5x/2.0x/3.0x speed.
- **Gameplay menu:** one hamburger button opens Save State, Load State, speed, Link Cable and Exit actions.
- **Connectivity:** experimental two-device local Wi-Fi GBA MULTI SIO integration; Pokémon trade has not yet passed a physical two-device acceptance test.

## Current engineering status

- **Unified branch:** `feature/nexora-unified-next-release`
- **Draft PR:** [#33 — Unify NEXORA Link Cable, gameplay tools and latest interface](https://github.com/Cartago137/NEXORA/pull/33)
- **No merge to `main` is authorized yet.**
- The Wi-Fi room and NXLP protocol are integrated with a first mGBA GBA MULTI SIO driver. The native SIO callback uses non-blocking polling with a timeout; distributed lockstep correctness remains to be validated.
- Save State/Load State call mGBA named-state APIs and use app-private per-ROM files. They are disabled during a Link session.
- GB/GBC ROM detection uses mGBA's cartridge-header compatibility detection; the player uses active frame dimensions and hides GBA-only L/R controls.
- CI has been extended to run JVM protocol/transport tests as well as Android builds. Check the current PR Actions results before describing any build as passed.
- Physical testing on the Motorola G54 is still required. A real Pokémon trade additionally requires a second Android device running the same FireRed ROM/revision.

## Engineering principles

1. Keep presentation, application services, native core and system-specific integration separated.
2. Preserve tested behavior in ROM loading, battery saves, audio, rendering and input.
3. Never call a feature complete because a screen or placeholder exists.
4. Review every stage, run tests, fix failures and repeat before moving on.
5. Document limitations and exact validation status.
6. Do not merge into `main` without the user's explicit approval.

## Legal and preservation note

NEXORA runs user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files. Third-party emulator cores and libraries must retain their required license notices.
