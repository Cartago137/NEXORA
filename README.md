# NEXORA

NEXORA is an Android-first handheld emulator project. The current default branch contains an Android application written in Kotlin and Jetpack Compose, with a native C/C++ layer integrating mGBA.

> **Current repository status (28 November 2026):** the Android source project is present on `main`. The mainline milestone is an early GBA-focused application (version `0.3.0`), not a finished public release. The newer unified GBA/GB/GBC work is being developed separately in draft [PR #33](https://github.com/Cartago137/NEXORA/pull/33) and has not been merged into `main`. CI success means the code compiled and tests passed; it does not establish broad physical-device compatibility.

## Current implementation on `main`

- Android application using Kotlin and Jetpack Compose.
- Native C/C++17 JNI bridge and mGBA integration.
- GBA core enabled; GB/GBC cores are not enabled in this branch.
- ROM import and a basic local game library.
- Gameplay input, video rendering, audio output, and battery-save loading.
- Portrait/landscape handling.
- Android CI workflow for automated JVM tests and APK builds.

## In development / roadmap

These items must not be interpreted as finished or released features:

- GB and GBC core support and a unified multi-console library.
- Advanced library organization (folders, favorites, filters, pinning, multi-select).
- Save States and refined gameplay tools.
- Customizable touch controls and expanded speed controls.
- GBA Link Cable over Wi-Fi; real trading between two physical devices still needs validation.
- AI Control Center integration.
- Motion sensors and rumble support, which remain product goals requiring implementation and device/game validation.
- Experimental ARMv7 support for the Samsung Galaxy Tab A 7.0 SM-T280. This is separate from the regular app target and is not yet a supported release configuration.

For the latest combined development scope, see [PR #33](https://github.com/Cartago137/NEXORA/pull/33). It is a draft development integration, not a released build.

## Android compatibility (main)

| Setting | Current value |
| --- | --- |
| Minimum Android version | Android 8.0 (API 26) |
| Target Android version | API 36 |
| Compile SDK | API 37 |
| Supported APK ABIs | `arm64-v8a`, `x86_64` |
| Android Gradle Plugin | 9.3.1 |
| Gradle Wrapper | 9.5.0 |
| Kotlin | 2.4.21 |
| Java toolchain | JDK 17 |
| Android NDK | 27.2.12479018 |
| CMake | 3.22.1 |

The SM-T280 experiment targets Android 7.1.2 / API 25 and ARMv7, but it does not change the compatibility of `main`.

## Build from source

### Requirements

- JDK 17.
- Android SDK with **Android SDK Platform 37** and **Build Tools 37.0.0**.
- Android NDK **27.2.12479018**.
- CMake **3.22.1**.
- Internet access on the first build to download Gradle dependencies and the pinned mGBA source.

Android Studio can be used to open the repository and install the listed SDK components. The project includes the Gradle Wrapper, so a separate global Gradle installation is not required.

### Build a debug APK

From the repository root:

**Linux / macOS**
```bash
./gradlew :app:assembleDebug
```

**Windows**
```bat
gradlew.bat :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run JVM unit tests

**Linux / macOS**
```bash
./gradlew :app:testDebugUnitTest
```

**Windows**
```bat
gradlew.bat :app:testDebugUnitTest
```

A successful local build or CI run does not replace installation and gameplay testing on physical devices. Please report the device model, Android version, ABI, game/system tested, and any reproduction steps with bug reports.

### Release signing

Release signing is configured through Gradle properties named `NEXORA_STORE_FILE`, `NEXORA_STORE_PASSWORD`, `NEXORA_KEY_ALIAS`, and `NEXORA_KEY_PASSWORD`. Keep keystores and passwords outside Git: use local untracked Gradle properties or a secure CI secret store. Never commit signing credentials or keystore files.

## Architecture: current reality and direction

The repository currently uses **one Gradle application module: `:app`**. The Kotlin application, JNI bridge, and native build are organized within that module. The separation between UI, platform services, and emulator cores is an architectural goal, but the project has not yet been split into independent Gradle modules such as `:core-common` or `:core-gba`.

The intended direction is to introduce stable emulator-core interfaces and separate console-specific behavior incrementally. This should be done without replacing the working native core or creating unnecessary module boundaries before the responsibilities are clear.

## Third-party software and licensing

NEXORA currently includes third-party license notices and full license texts:

- [mGBA — MPL-2.0 notice](app/src/main/assets/licenses/mgba-MPL-2.0.txt)
- [blip_buf — LGPL-2.1 notice](app/src/main/assets/licenses/blip-buf-LGPL-2.1.txt)
- [inih — BSD notice](app/src/main/assets/licenses/inih-BSD.txt)
- [Third-party notices and integration details](docs/THIRD_PARTY_NOTICES.md)

These notices cover identified third-party components; they are **not** a project-wide license for NEXORA's original code. No top-level `LICENSE` has been selected or added yet. Until the project owner chooses and publishes a license, the public visibility of this repository should not be interpreted as permission to reuse, redistribute, or modify NEXORA-specific code. Contributors may discuss or submit changes, but contribution and redistribution terms should be made explicit before the project is presented as an open-source release.

NEXORA is intended to run user-provided game files. The project will not distribute commercial ROMs, copyrighted game assets, or proprietary BIOS files.

## Testing and release readiness

The automated workflow checks compilation and the tests currently present in the repository. Device compatibility is still limited and must be expanded across Android versions, chip architectures, and real gameplay sessions. In particular, GBA/GB/GBC behavior, audio, battery saves, Save States, touch controls, lifecycle recovery, and Link Cable trading require feature-specific validation before release.

No build should be described as a stable public release solely because GitHub Actions is green. Official releases should have a version tag, release notes, a verifiable APK, a documented compatibility matrix, and completed license review.

## Project records

- [APK audit and source recovery](docs/APK_AUDIT_AND_SOURCE_RECOVERY.md)
- [Third-party notices](docs/THIRD_PARTY_NOTICES.md)
- [GitHub Actions](https://github.com/Cartago137/NEXORA/actions)
- [Open pull requests](https://github.com/Cartago137/NEXORA/pulls)
