# Vape v4.21 Unlocker

> **Vape v4.21 recovery project with an integrated Lunar Client cosmetic unlocker.**

![Platform](https://img.shields.io/badge/platform-Windows%20x64-0078D6)
![Java](https://img.shields.io/badge/Java-17%20%7C%208-orange)
![Gradle](https://img.shields.io/badge/Gradle-8.8-02303A)
![License](https://img.shields.io/badge/license-CC0--1.0-lightgrey)
![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9%2B-62B47A)

A reconstructed and extended Vape v4.21 codebase for Minecraft, combining the recovered Vape Java/native architecture with a Lunar Client cosmetic unlocker.

The project contains the recovered client source, Windows x64 native loader, Java injection payload, runtime mappings, GUI/module infrastructure, and Lunar Client integration.

**Project version:** `4.21`

---

## Project Lineage

This repository is based on and extends:

### [RSSeeker/Vape-v4.21](https://github.com/RSSeeker/Vape-v4.21)

RSSeeker's repository is the primary upstream source used for the Vape v4.21 recovery work in this project.

Their project reconstructs and organizes the Vape 4.21 Java layer and Windows x64 native bridge and provides the foundation used here.

RSSeeker's project itself credits:

### [OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)


### What this repository adds

This repository builds on the recovered Vape v4.21 project with:

* Lunar Client cosmetic integration
* Cosmetic ownership/runtime patching
* Badges
* Emotes
* Sprays
* Jams
* Lunar+ related functionality
* English-focused project presentation
* One-click Windows build workflow
* Combined Java + native build process
* Ready-to-use native loader output

Credit for the underlying Vape recovery work belongs to the respective upstream contributors.

---

## Important Notice

This project is **not official Vape source code**, an official Vape release, or a vendor-signed Vape artifact.

It is a reconstructed software recovery/research project derived from publicly available recovery work.

Likewise, this project is not affiliated with, endorsed by, or sponsored by:

* Vape / Manthe
* Lunar Client
* Mojang Studios
* Microsoft

Vape, Lunar Client, Minecraft, and other referenced names and assets remain the property of their respective owners.

Use this project only in environments where you have permission to test it and make sure your use complies with applicable software licenses, server rules, and local laws.

---

# Features

## Vape v4.21 Module System

The recovered client contains the Vape module framework and supporting infrastructure.

Module categories include:

* **Blatant**
* **Combat**
* **Control**
* **Debug**
* **Macro**
* **Render**
* **Utility**
* **World**
* **Other / uncategorized modules**

The module framework includes configurable values, keybinds, lifecycle handling, event callbacks, and GUI integration.

---

## Lunar Client Cosmetic Unlocker

The project integrates Lunar Client cosmetic functionality directly into the recovered client environment.

Supported functionality includes:

* Cosmetics
* Badges
* Emotes
* Sprays
* Jams
* Lunar+
* Runtime configuration toggles
* Automatic injection option
* Debug mode
* Language configuration

The integration operates at runtime and does not require permanently replacing Lunar Client files.

> Availability and behavior may change when Lunar Client updates its internal implementation.

---

## Native Windows Loader

`Vape-v4.21.exe` provides the Windows x64 loader interface.

The loader handles:

* Minecraft process discovery
* Process selection
* Native DLL injection
* Java payload bootstrap
* Injection progress
* Runtime initialization
* Lunar integration startup

The native component bridges the Windows loader and the running Minecraft JVM.

### Default controls

| Action        | Key           |
| ------------- | ------------- |
| Open Vape GUI | `Right Shift` |

---

## Client Infrastructure

The recovered codebase includes much more than individual modules.

### Configuration and Profiles

* Save module configuration
* Load existing configurations
* Maintain client settings
* Profile management
* Config serialization/deserialization

### Event System

Runtime event infrastructure for:

* Game lifecycle events
* World changes
* Player events
* Rendering
* Input
* Client ticks
* Module callbacks

### Friend System

Includes infrastructure for:

* Friends
* Friend aliases
* Online state
* Ping handling
* Friend-related listeners

### Macro System

* Bindable macros
* Macro management
* Keyboard integration

### Click GUI

In-game interface for:

* Module management
* Settings
* Keybinds
* Configuration
* Client features

### Runtime Mapping System

The recovered project includes mappings and runtime helpers intended to support multiple Minecraft environments.

Mapping resources include support for environments such as:

* Vanilla
* Forge
* NeoForge
* Fabric

and multiple Minecraft generations.

Actual module compatibility varies depending on Minecraft version, loader, mappings, and rendering implementation.

### Native Bridge

The project includes JNI/JVMTI-related native infrastructure used to connect the Windows native loader with the Java client runtime.

This handles functionality such as:

* Native-to-Java bootstrap
* Runtime method registration
* JVM interaction
* Payload loading
* Runtime class access

---

# Repository Structure

```text
vape-v4.21-unlocker/
│
├── build.gradle
│   Gradle build configuration
│
├── settings.gradle
│   Gradle project configuration
│
├── gradle.properties
│   Gradle/project properties
│
├── gradlew
├── gradlew.bat
│   Bundled Gradle wrapper
│
├── build.bat
│   One-click Windows build/setup script
│
├── native/
│   Windows x64 native loader, injector,
│   bootstrap and JVM bridge
│
├── src/main/
│   │
│   ├── java/
│   │   │
│   │   ├── gg/vape/
│   │   │   ├── module/
│   │   │   ├── config/
│   │   │   ├── event/
│   │   │   ├── manager/
│   │   │   ├── account/
│   │   │   ├── friend/
│   │   │   ├── asm/
│   │   │   ├── mapping/
│   │   │   ├── lunar/
│   │   │   ├── runtime/
│   │   │   ├── reflect/
│   │   │   ├── ui/
│   │   │   ├── render/
│   │   │   └── ...
│   │   │
│   │   └── func/skidline/
│   │
│   └── resources/
│       ├── mappings/
│       └── resources/
│
├── tools/
│   Recovery/build utilities
│
├── LICENSE
└── README.md
```

---

# Build Outputs

A complete build can produce:

| Artifact               | Description                                          |
| ---------------------- | ---------------------------------------------------- |
| `Vape-v4.21.exe`       | Windows x64 loader / injector                        |
| `Vape-v4.21Native.dll` | Native JVM/injection bridge                          |
| Injection JAR          | Java runtime payload containing the recovered client |

The Java injection payload is generated through Gradle and packaged with its runtime dependencies.

---

# Requirements

## Running

* Windows x64
* 64-bit Minecraft JVM
* Supported Minecraft installation
* Lunar Client when using Lunar-specific functionality

## Building

The one-click build script can detect and install most required development dependencies through `winget`.

Required tools include:

* **JDK 17**
* **Git**
* **CMake**
* **Visual Studio 2022 Build Tools**
* **Visual C++ x64 toolchain**
* **Windows SDK**
* **Gradle 8.8**

The project includes a Gradle Wrapper and requires Gradle **8.8**.

Internet access is required on the first build so Gradle can retrieve its dependencies.

---

# Building

## One-Click Windows Build

Open Command Prompt in the repository directory and run:

```bat
build.bat
```

The script checks your development environment and installs missing dependencies through `winget` when possible.

It then builds:

1. The recovered Java source
2. The Java injection payload
3. The native Windows x64 bridge
4. The injector/loader
5. The complete injection bundle

Successful output is copied into the project output location.

---

## Check Build Environment

To verify your development environment without installing anything:

```bat
build.bat check
```

This checks components such as:

* Git
* JDK 17
* CMake
* Visual Studio C++ Build Tools
* Project source

---

## Build Java Payload Only

```bat
build.bat javaonly
```

This compiles and verifies the Java client/injection payload without building the Windows native components.

---

# Advanced Gradle Build

You can also use the Gradle wrapper directly.

## Compile and Verify

```bat
gradlew.bat clean build verifyInjectionPayload
```

This performs the Java build and verifies the generated injection payload.

## Build Injection JAR

```bat
gradlew.bat injectionJar
```

## Verify Injection Payload

```bat
gradlew.bat verifyInjectionPayload
```

## Build Native Components

```bat
gradlew.bat buildNative
```

## Prepare Full Injection Bundle

```bat
gradlew.bat prepareInjectionBundle -PtargetRelease=8
```

The complete bundle is generated under:

```text
build/injection/
```

---

# Java Compatibility

The build uses **JDK 17** as its Gradle toolchain.

For compatibility with older Minecraft JVM environments, the payload can also be compiled to Java 8-compatible bytecode using:

```bat
-PtargetRelease=8
```

This is particularly useful for older Minecraft versions such as 1.8.9.

---

# Usage

> Use only in environments where you are authorized to test the software.

1. Start Minecraft using a **64-bit JVM**.
2. Allow the game to reach the main menu or game world.
3. Run:

```text
Vape-v4.21.exe
```

4. Select the appropriate Minecraft Java process.
5. Start the injection.
6. After initialization, press:

```text
Right Shift
```

to open the in-game interface.

---

# Compatibility

The recovered project contains infrastructure and mapping data for multiple Minecraft versions and loaders.

Known target environments include:

| Environment     | Support                            |
| --------------- | ---------------------------------- |
| Windows x64     | Required                           |
| 64-bit JVM      | Required                           |
| Vanilla         | Mapping/runtime support present    |
| Forge           | Mapping/runtime support present    |
| Fabric          | Mapping/runtime support present    |
| NeoForge        | Mapping/runtime support present    |
| Lunar Client    | Supported by dedicated integration |
| Minecraft 1.8.9 | Primary legacy target              |

The source contains mapping resources covering multiple Minecraft generations.

### Important

Not every module is guaranteed to work on every Minecraft version.

Features that depend on:

* Rendering
* Packet mappings
* Input hooks
* Entity mappings
* JVM implementation details
* Client-specific internals

may require version-specific adaptation.

Lunar Client updates can also change internal classes or runtime behavior and may temporarily break Lunar-specific integration.

```


---

# Recovery Status

This project should be treated as a **reconstruction**, not as an exact copy of an original commercial Vape v4.21 distribution.

Recovered components may contain:

* Decompiled structures
* Reconstructed logic
* Compatibility fixes
* Recreated native interfaces
* Recovered resources
* Placeholder behavior where original implementation details were unavailable
* Additional functionality introduced by this repository

Behavior may therefore differ from the original Vape v4.21 client.
---

# Contributing

Contributions are welcome.

Useful contributions include:

* Compatibility fixes
* Mapping corrections
* Crash fixes
* Build improvements
* Documentation
* Runtime stability improvements
* Lunar compatibility updates
* Module recovery fixes
* Code cleanup
* Testing across Minecraft versions

When submitting an issue, include:

```text
Minecraft version:
Client/loader:
Java version:
Windows version:
What happened:
What you expected:
Relevant log:
Steps to reproduce:
```

Please avoid reports containing account credentials, access tokens, or other private information.

---

# Reporting Bugs

Before opening an issue:

1. Confirm you are using a 64-bit JVM.
2. Rebuild from the latest source.
3. Reproduce the problem.
4. Save the relevant logs.
5. Include your Minecraft version and loader.
6. Describe exactly when the failure occurs.

Detailed reports are much easier to investigate than reports such as:

```text
it doesn't work
```

---

# Security

This project contains native process/JVM integration code.

Because software of this type performs runtime injection and low-level JVM interaction, security software may treat compiled binaries as suspicious.

Users are encouraged to:

* Inspect the source
* Build binaries themselves
* Review native code before running it
* Verify downloaded artifacts
* Use isolated test environments where appropriate

Do not disable security software simply because a detection occurs. Review the source and determine why the detection happened.

---

# Disclaimer

This repository is provided for software recovery, interoperability research, compatibility testing, education, and development.

The maintainers do not claim ownership of Vape, Lunar Client, Minecraft, or third-party intellectual property included or referenced by the recovered project.

This repository does not grant rights to third-party trademarks, proprietary assets, copyrighted material, online services, accounts, cosmetics, or other content beyond the rights held by the respective contributors.

You are responsible for determining whether your use of the project complies with:

* Applicable law
* Software licenses
* Minecraft server rules
* Lunar Client terms
* Other third-party terms and policies

---

# License

This repository is distributed under the **CC0 1.0 Universal** public-domain dedication where applicable.

See:

```text
LICENSE
```

CC0 applies only to material that contributors have the legal right to dedicate.

Third-party:

* Libraries
* Trademarks
* Fonts
* Textures
* Decompiled/recovered material
* Game assets
* Client assets
* Other existing intellectual property

remain subject to their respective licenses and rights.

---

## Credits

### Vape 4.21 Recovery

[RSSeeker/Vape-v4.21](https://github.com/RSSeeker/Vape-v4.21)

Primary upstream recovery project and source foundation.

### Earlier Recovery Source

[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)

Credited by the RSSeeker recovery project as an earlier public source.

### This Repository

[0n4u/vape-v4.21-unlocker](https://github.com/0n4u/vape-v4.21-unlocker)

Lunar Client integration, cosmetic functionality, build integration, and project-specific modifications.

---

<p align="center">
  <strong>Vape v4.21 Unlocker</strong><br>
  Vape v4.21 Recovery × Lunar Client Integration
</p>
