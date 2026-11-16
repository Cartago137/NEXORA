# NEXORA 0.4.0 — Development release notes

**Android version:** 0.4.0 (versionCode 4)  
**Source:** `feature/nexora-unified-next-release`  
**Status:** signed release candidate build; not merged to `main`.

## New systems

- GBA, Game Boy and Game Boy Color are enabled through mGBA.
- Console detection uses the ROM header; video buffer and aspect ratio adapt to the active system.
- Library can filter All, GBA, GB and GBC games.

## Home and Library

- Unified Library folders, search, favorites, pinned games/folders, multi-select and safe removal.
- Landscape navigation rail on the right; portrait keeps the bottom navigation.
- Original NEXORA cat mascot in launcher, splash and app header.
- Selected box artwork keeps the full original image without crop or NEXORA overlays.

## Gameplay

- Live speed options: 1.0x, 1.5x, 2.0x and 3.0x.
- AudioTrack playback rate follows emulation speed.
- Continuous D-pad drag supports direction changes and diagonals without lifting.
- Compact function menu groups Save State, Load State, speed, Link Cable and exit.
- Save States use mGBA's named-state API, per-ROM SHA-256 file identity, temporary writes and atomic replacement.

## Pokémon Link Cable — experimental

- Local Wi-Fi host/join with room code, IP and ROM identity handshake.
- NXLP v1 protocol and first native GBA MULTI SIO bridge.
- Link session heartbeat, bounded timeouts, cancellable room setup and serialized disconnect/reconnect.
- Save States are disabled while Link is attached.

**Important:** Link Cable has passed protocol/transport tests and Android compilation, but an actual Pokémon trade between two physical Android devices has not yet been validated. Use the exact same Pokémon FireRed ROM/revision on both devices. Do not treat this as universal multiplayer support or a final guarantee of trade correctness.

## Validation status

- 17 JVM protocol/transport tests are included in the current CI workflow.
- Debug and signed release APKs must pass in the final workflow run for this exact 0.4.0 commit.
- Physical checks on Motorola G54 remain outstanding: GBA/GB/GBC gameplay, audio, controls, speed, Save States, orientation, Library and icon/splash.
- Two-device Cable Club trade acceptance remains outstanding because a second Android device is not currently available for testing.

## Compatibility and safety

- Minimum Android version remains API 26.
- Native ABIs: arm64-v8a and x86_64.
- No ROMs or commercial game files are included.
- Do not merge PR #33 into `main` without explicit user approval.
