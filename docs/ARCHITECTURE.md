# NEXORA Architecture

## 1. Goals

NEXORA is an Android-first GBA emulator with a premium interface and a modular foundation for future systems. The architecture must preserve the existing tested prototype's behavior while moving toward a reproducible source project.

## 2. Confirmed technology direction

- Android application and UI: Kotlin + Jetpack Compose.
- Native emulation layer: C/C++ with Android NDK and CMake.
- Initial emulator core: mGBA for Game Boy Advance.
- App/native boundary: JNI.
- Build source of truth: this GitHub repository, with GitHub Actions for repeatable builds.

The APK metadata identifies source revision `17bcb65bad01e23790605ef4ec32fbdb173e637a`, which is preserved in Git history. The later CI-verified `feature/audio-saves-orientation` branch contains the expanded Kotlin/Compose app, native JNI bridge, pinned mGBA CMake integration, and Gradle project; this source line has been restored to `main`.

## 3. Proposed layers

```text
Presentation (Kotlin / Compose)
  ├── Home and featured game
  ├── Library, folders, search and history
  ├── Emulator screen and virtual controls
  └── Settings, themes and localization

Application (Kotlin)
  ├── Game library use cases
  ├── Save-data and session coordination
  ├── Input mapping
  └── Multiplayer session coordination

Emulation abstraction
  ├── EmulatorCore interface
  ├── Core registry / system selection
  └── Core capability reporting

System modules (C/C++)
  └── GBA adapter and mGBA integration

Android platform services
  ├── File access and permissions
  ├── Audio, vibration and sensors
  ├── Storage and backup
  └── Local networking
```

The JNI bridge should remain narrow. Kotlin UI code must not depend directly on mGBA types or internal functions.

## 4. Emulator core contract

The application should communicate with an emulator through a stable contract. At minimum, it must eventually cover:

- initialize and shut down a core;
- load and unload a user-selected game;
- advance emulation and provide video frames;
- receive controller input;
- produce and consume audio;
- save and restore game data when supported;
- report supported capabilities and limitations;
- expose lifecycle and error information.

Exact signatures must be verified against the recovered/reconstructed native implementation rather than guessed from JNI symbol names alone.

## 5. Capability-based features

Not every system supports the same hardware features. Each core should report capabilities instead of assuming that all consoles support the same functions.

Potential capabilities include:

- save states;
- link cable / multiplayer;
- motion or tilt sensors;
- rumble;
- microphone or other peripherals, if relevant to a future system.

The interface should enable a feature only when the active core and device support it.

## 6. Multiplayer direction

Multiplayer is a major product goal. The design should distinguish:

1. local link emulation between two instances;
2. device-to-device communication over a local Wi-Fi network;
3. any future internet-based relay service.

These are technically different modes. Their feasibility, latency, synchronization, supported games, and security requirements must be tested independently. Do not promise universal compatibility before testing.

## 7. Data ownership

The library should track user-selected game files and their metadata without assuming that NEXORA owns or distributes those files.

Removing a game from the library must be distinct from deleting its original file. Any destructive file deletion should require explicit confirmation.

Deleting a custom folder should not delete the games assigned to it. Pinned and favorite status should remain independent of folder membership.

## 8. Quality requirements

- Keep core emulation work off the UI thread.
- Avoid blocking the interface during library scans, file operations, or save-data operations.
- Handle user files as untrusted input and handle malformed files safely.
- Preserve selection and session state across orientation changes.
- Treat persistent in-game save behavior as a regression requirement: the user has confirmed that saves survive a full app restart in the reference APK.
- Add automated tests around library behavior, input mapping, save management, and core integration.
- Record third-party licenses and notices before shipping dependencies.

## 9. Source recovery and compatibility

The tested APK is a behavioral reference, and its embedded Git revision has been recovered from the repository's preserved branches. The current restoration brings the later CI-verified source line into `main`.

- Keep the exact APK-generating revision available for comparison.
- Preserve the later rendering, audio, save, and input improvements already present in the source branches.
- Do not replace the existing mGBA implementation with a stub and call it equivalent.
- Verify JNI signatures, frame-buffer format, audio format, threading, and lifecycle behavior on the target device.
- Treat the Pokémon FireRed white/small rendering issue as open until reproduced and verified after the source restoration.

See [APK audit and source recovery](APK_AUDIT_AND_SOURCE_RECOVERY.md) and the [Android integration plan](ANDROID_INTEGRATION_PLAN.md).
