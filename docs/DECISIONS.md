# Architecture Decision Record

This file records decisions and important constraints. Update it when a decision is validated or changed.

## ADR-001 — Modular, multi-system architecture

**Status:** Accepted

**Decision:** Separate presentation, application logic, emulator abstraction, system-specific cores, and Android platform services.

**Reason:** NEXORA starts with GBA but should be able to expand to other systems without a full rewrite.

**Consequence:** The initial implementation may take slightly more planning, but system-specific code should remain isolated.

## ADR-002 — Android-first release

**Status:** Accepted

**Decision:** Target Android first.

**Reason:** The initial product is a mobile GBA emulator.

**Consequence:** Android file access, lifecycle, sensors, audio, vibration, and performance constraints must be considered from the beginning. Other platforms remain future possibilities, not current promises.

## ADR-003 — User-provided game files

**Status:** Accepted

**Decision:** NEXORA will manage files selected by the user and will not bundle commercial ROMs or proprietary BIOS files.

**Reason:** Respect copyright and keep user ownership clear.

**Consequence:** Import, removal, and deletion must be separate operations, with confirmation before deleting an original file.

## ADR-004 — Capability-based hardware features

**Status:** Accepted

**Decision:** Optional features such as link, motion sensors, and rumble must be exposed through capability reporting.

**Reason:** Support varies by emulator core, game, and device.

**Consequence:** The interface must not show unsupported controls as if they were guaranteed to work.

## ADR-005 — Multiplayer is a core product goal

**Status:** Accepted as a goal; implementation design pending

**Decision:** Plan for local link emulation and device-to-device Wi-Fi connectivity as important features.

**Reason:** Multiplayer, including compatible Pokémon trading and battles, is a key part of the intended experience.

**Consequence:** Prototype each connection mode early enough to discover technical limitations. Do not claim compatibility until verified.

## ADR-006 — mGBA as the GBA emulation core

**Status:** Accepted

**Decision:** Use mGBA as NEXORA's Game Boy Advance emulation core.

**Reason:** The tested APK contains mGBA-related native symbols and an mGBA MPL-2.0 license notice, consistent with the project's selected core.

**Consequence:** Integrate mGBA behind the stable emulator-core abstraction. Verify the exact upstream revision, build configuration, license obligations, and supported capabilities before shipping.

## ADR-007 — Native emulation integration in C/C++

**Status:** Accepted

**Decision:** Use C/C++ for the native emulation/integration layer, working with mGBA's predominantly C codebase.

**Reason:** The inspected APK contains a native shared library with JNI entry points and mGBA-related symbols.

**Consequence:** The native boundary and build pipeline must be recovered or reconstructed and validated against the reference APK.

## ADR-008 — Kotlin and Jetpack Compose for Android UI

**Status:** Accepted as the source-reconstruction target

**Decision:** Use Kotlin and Jetpack Compose for Android screens, navigation, library management, lifecycle coordination, and settings.

**Reason:** Static inspection of the existing test APK found Kotlin application classes, a `MainActivityKt` entry point, and Compose-generated player-screen classes.

**Consequence:** The exact original Kotlin source and dependency versions still need to be recovered or reconstructed. The inspected APK is evidence of the stack, not a substitute for source code.

## ADR-009 — GitHub as the source of truth

**Status:** Accepted

**Decision:** Keep the maintainable NEXORA source, documentation, and repeatable build pipeline in the GitHub repository. Use GitHub Actions for automated builds and checks.

**Reason:** The project is being developed and maintained through GitHub; a reproducible source tree is required for future work.

**Consequence:** Do not treat an APK produced outside this repository as a reproducible build. Keep reference binaries distinct from source and CI artifacts.
