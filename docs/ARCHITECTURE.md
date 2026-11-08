# NEXORA Architecture

## 1. Goals

The architecture must support a polished Android application beginning with Game Boy Advance emulation, while allowing future systems to be added as independent modules.

The design should avoid coupling the interface directly to GBA-specific code.

## 2. Proposed layers

```text
Presentation
  ├── Home and featured game
  ├── Library, folders, search and history
  ├── Emulator screen and virtual controls
  └── Settings, themes and localization

Application
  ├── Game library use cases
  ├── Save-state and session coordination
  ├── Input mapping
  └── Multiplayer session coordination

Emulation abstraction
  ├── EmulatorCore interface
  ├── Core registry / system selection
  └── Core capability reporting

System modules
  └── GBA core integration (first system)

Platform services
  ├── File access and permissions
  ├── Audio, vibration and sensors
  ├── Storage and backup
  └── Local networking
```

This is a conceptual boundary map, not a commitment to a specific programming language, framework, or folder layout. Those choices must be validated before implementation.

## 3. Emulator core contract

The application should communicate with an emulator through a stable contract. At minimum, the contract will eventually need to cover:

- initialize and shut down a core;
- load and unload a user-selected game;
- advance emulation and provide video frames;
- receive controller input;
- produce and consume audio;
- save and restore game state when supported;
- report supported capabilities and limitations;
- expose lifecycle and error information.

Exact method signatures must be designed only after the runtime and core integration approach are selected.

## 4. Capability-based features

Not every system supports the same hardware features. Each core should report capabilities instead of assuming that all consoles support the same functions.

Potential capabilities include:

- save states;
- link cable / multiplayer;
- motion or tilt sensors;
- rumble;
- microphone or other peripherals, if relevant to a future system.

The interface should enable a feature only when the active core and device support it.

## 5. Multiplayer direction

Multiplayer is a major product goal. The design should distinguish:

1. local link emulation between two instances;
2. device-to-device communication over a local Wi-Fi network;
3. any future internet-based relay service.

These are technically different modes. Their feasibility, latency, synchronization, supported games, and security requirements must be tested independently. Do not promise universal compatibility before testing.

## 6. Data ownership

The library should track user-selected game files and their metadata without assuming that NEXORA owns or distributes those files.

Removing a game from the library must be distinct from deleting its original file. Any destructive file deletion should require explicit confirmation.

Deleting a custom folder should not delete the games assigned to it. Pinned and favorite status should remain independent of folder membership.

## 7. Quality requirements

- Keep core emulation work off the UI thread.
- Avoid blocking the interface during library scans, file operations, or save-state operations.
- Handle orientation changes without losing the current session or selection state.
- Treat user files as untrusted input and handle malformed files safely.
- Add automated tests around library behavior, input mapping, save management, and core integration.
- Record third-party licenses and notices before shipping dependencies.

## 8. Open technical decisions

The following are intentionally unresolved:

- application framework and language;
- GBA core and integration method;
- native versus managed boundary;
- rendering and audio pipeline;
- persistence technology;
- multiplayer transport and synchronization design;
- minimum Android version and device performance targets.

Resolve these with small prototypes and documented trade-offs rather than guessing early.
