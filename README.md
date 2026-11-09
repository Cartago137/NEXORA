# NEXORA

NEXORA is an Android-first Game Boy Advance emulator project, designed around a distinctive console-inspired experience and an architecture that can grow to support additional systems.

> **Current status:** the Android app shell builds in CI. The current development milestone adds full-screen presentation and a persistent library for user-selected GBA ROM files. The mGBA core is not yet integrated, so games are not playable yet.

## Product direction

- **Platform:** Android first.
- **Initial system:** Game Boy Advance (GBA), using the selected mGBA core.
- **Future growth:** modular architecture that can accommodate additional console cores without rebuilding the application.
- **Languages:** Portuguese (default) and English.
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
├── app/
│   └── src/main/
│       ├── java/com/nexora/app/
│       │   ├── MainActivity.kt
│       │   ├── GameLibrary.kt
│       │   └── NativeBridge.kt
│       ├── cpp/
│       └── res/
│           ├── values/       # Portuguese (default)
│           └── values-en/    # English
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ROADMAP.md
│   ├── DECISIONS.md
│   ├── ANDROID_APP_SHELL.md
│   └── ANDROID_INTEGRATION_PLAN.md
└── .github/workflows/
    └── android-build.yml
```

## Current implementation

- Kotlin + Jetpack Compose Android application.
- Portuguese default strings and English translations; Android locale selects the matching resources.
- Four navigation areas: Home, Library, History, and Settings.
- Full-screen app window hides the Android status bar at the top.
- Android document picker accepts user-selected `.gba`, `.agb`, and `.bin` files.
- Library entries and persistable document permissions are stored across app restarts.
- Removing a library entry offers a separate option to delete the original document; deletion is never silent.
- C++/JNI native bridge builds, but it is still a placeholder and does not run mGBA.

## Build and test status

The project is built by GitHub Actions on the `foundation/android-app-shell` branch. A successful CI build proves that the APK compiles; physical-device behavior still requires testing. The app is not yet a playable emulator: mGBA, video, audio, emulation input, save states, and multiplayer remain future milestones.

## Legal and preservation note

NEXORA is intended to run user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files. Any third-party emulator core or library must be reviewed for license compatibility before integration.
