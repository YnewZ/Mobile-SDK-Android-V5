# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a DJI Mobile SDK (MSDK) V5 Android sample application for controlling DJI drones (Matrice 350 RTK, Matrice 300 RTK, etc.). It extends the official DJI MSDK V5 sample with custom external control features.

## Project Structure

The repo contains 3 Gradle modules:

- `android-sdk-v5-as/` — Aggregator module containing `settings.gradle`, root `build.gradle`, and `dependencies.gradle`. **All Gradle commands must be run from this directory.**
- `android-sdk-v5-sample/` — The main application module (`com.df.dronecontroller`). Contains all custom business logic.
- `android-sdk-v5-uxsdk/` — DJI UX SDK library module (`dji.v5.ux`). Reusable UI widgets for FPV, camera, gimbal, etc. Imported by `android-sdk-v5-sample`.

## Build Commands

All commands run from `android-sdk-v5-as/`:

```bash
# Build debug APK
./gradlew :sample:assembleDebug

# Full build (debug + release)
./gradlew :sample:build

# Clean
./gradlew clean
```

Lint is disabled (`checkReleaseBuilds false`, `abortOnError false`). There are no unit tests or instrumentation tests in this project.

Build configuration:
- compileSdk=35, minSdk=24, targetSdk=35
- Kotlin 2.1.0, AGP 8.7.0
- NDK filtered to `arm64-v8a` only
- ViewBinding enabled
- Release and debug builds both use the same signing config (`msdkkeystore.jks`) defined in `gradle.properties`

## High-Level Architecture

### MSDK V5 Key-Value Communication

All hardware interaction (camera, gimbal, flight controller, payload) uses DJI MSDK V5's Key-Value API:

- Keys live in `dji.sdk.keyvalue.key.*Key` (e.g., `CameraKey`, `GimbalKey`, `FlightControllerKey`).
- Operations use the `dji.v5.et` DSL: `create()`, `listen()`, `set()`, `action()`.
- Example pattern:
  ```kotlin
  CameraKey.KeyCameraMode.create(ComponentIndexType.LEFT_OR_MAIN).set(mode, onSuccess, onFailure)
  GimbalKey.KeyGimbalAttitude.create(ComponentIndexType.LEFT_OR_MAIN).listen(owner) { attitude -> ... }
  ```

Many sample pages provide direct Key-Value manipulation via `KeyValueFragment` (`keyvalue/` package) as a generic debugging tool.

### External Control Architecture (Custom Feature)

This project's core innovation is an external control layer that lets outside systems command the drone via two entry points:

1. **HTTP API** — `HttpControlServer` (port 8080) accepts `POST /virtualstick/control` with a JSON body.
2. **PSDK Payload Data** — `PayLoadDataVM` receives binary float arrays from a PSDK payload.

Both converge on `ExternalControlManager.handleExternalRequest(jsonParams)`, which parses the JSON into `VirtualStickFlightControlParam` and calls `VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(param)`.

**PSDK data format** (little-endian float array, 6 floats):
- `floats[0]` → `roll`
- `floats[1]` → `pitch`
- `floats[2]` → `verticalThrottle`
- `floats[5]` → `yaw`

**HTTP JSON format** (all fields optional):
```json
{
  "pitch": 0.5,
  "roll": 0.0,
  "yaw": 10.0,
  "verticalThrottle": 1.2,
  "rollPitchControlMode": "VELOCITY",
  "yawControlMode": "ANGULAR_VELOCITY",
  "verticalControlMode": "VELOCITY"
}
```

### Application Lifecycle

`DJIAircraftApplication` → `DJIApplication` → `MSDKManagerVM.initMobileSDK()` → `SDKManager.getInstance().init()` → `registerApp()`. `DJIAircraftMainActivity` starts the HTTP control service (`ExternalControlManager.startControlService(8080)`) in `onCreate`.

### Navigation & Page Registration

`TestingToolsActivity` hosts the debugging UI. Pages are registered dynamically:

- `AircraftFragmentPageInfoFactory` / `CommonFragmentPageInfoFactory` return `FragmentPageItemList` objects containing menu entries.
- Each entry maps a `FragmentPageItem` to a destination ID in `nav_aircraft.xml` / `nav_common.xml`.
- Adding a new page requires: (1) Fragment + ViewModel + layout, (2) navigation `<fragment>` node, (3) string resources for title/description, (4) entry in the corresponding `*PageInfoFactory`.

### Virtual Stick

`VirtualStickFragment` / `VirtualStickVM` manage both basic and advanced virtual stick modes. Before sending virtual stick commands (including via `ExternalControlManager`), the app must:

1. Call `VirtualStickManager.getInstance().enableVirtualStick(callback)`.
2. Ensure the **RC remote controller manually enables virtual stick and virtual stick advanced mode** (this is a hardware requirement, not code-only).

## Important Constraints

- The app is **landscape-only** (`screenOrientation="landscape"`).
- NDK ABI is restricted to `arm64-v8a`; it will not run on 32-bit devices or emulators without arm64 support.
- DJI SDK API Key is hardcoded in `gradle.properties` (`AIRCRAFT_API_KEY`).
- Google Maps and MapLibre tokens are placeholder values (`ENTER YOUR ...`) in `gradle.properties`.
