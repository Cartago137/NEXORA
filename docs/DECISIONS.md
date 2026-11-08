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

## ADR-006 — Framework and emulator core remain undecided

**Status:** Open

**Decision:** Do not commit to a framework, language, or GBA core until options have been compared and a small technical prototype has been built.

**Reason:** A premature choice could create avoidable rewrites or license conflicts.

**Next step:** Compare viable Android application stacks and GBA core options for performance, maintainability, licensing, and integration complexity.
