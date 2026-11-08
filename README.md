# NEXORA

NEXORA is a mobile-first Game Boy Advance emulator project, designed around a distinctive console-inspired experience and an architecture that can grow to support additional systems.

> **Project status:** foundation and planning stage. The emulator core and platform integrations have not yet been implemented.

## Product direction

- **Platform:** Android first.
- **Initial system:** Game Boy Advance (GBA).
- **Future growth:** modular architecture that can accommodate additional console cores without rebuilding the application.
- **Languages:** English and Spanish.
- **Visual identity:** dark, premium interface inspired by deep Gengar-like purples, with restrained violet and pink/red accents.
- **Orientation-aware experience:** landscape mode evokes the Game Boy Advance; portrait mode evokes the Game Boy Advance SP.
- **Personalization:** selectable color themes and user-provided console-body skins.
- **Game library:** searchable collection, custom folders, favorites, pinned items, multi-select actions, and play history.
- **Featured game presentation:** a subtle floating 3D game box, not a flat cover or cartridge.
- **Special hardware features:** plan for compatible motion/tilt sensors and rumble emulation.
- **Connectivity:** multiplayer/link functionality is a core product goal, including local Wi-Fi connectivity where technically and legally feasible.

## Engineering principles

1. Keep the user interface, emulator core, platform services, and system-specific integrations separated.
2. Define stable interfaces between the app and each emulator core.
3. Keep console-specific behavior inside system modules rather than scattering it throughout the app.
4. Document decisions, limitations, licensing, and test coverage.
5. Prefer incremental, testable changes over a large unverified implementation.
6. Respect software licenses and users' rights: NEXORA will not include copyrighted game ROMs or BIOS files.

## Repository map

```text
NEXORA/
├── README.md
├── .gitignore
└── docs/
    ├── ARCHITECTURE.md
    ├── ROADMAP.md
    └── DECISIONS.md
```

The repository currently contains project documentation only. The implementation stack and emulator-core integration will be selected and validated before application code is introduced.

## Getting started

There is no runnable application yet. Start with the architecture and roadmap documents:

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)

## Legal and preservation note

NEXORA is intended to run user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files. Any third-party emulator core or library must be reviewed for license compatibility before integration.


## Implementation status

The initial Android app shell is being developed on the `foundation/android-app-shell` branch. It contains a Kotlin/Compose starter screen and a minimal C++/JNI bridge. It is not yet a playable emulator, and the mGBA core has not yet been linked into the Android build. See [the app shell milestone](docs/ANDROID_APP_SHELL.md) and [the Android integration plan](docs/ANDROID_INTEGRATION_PLAN.md).
