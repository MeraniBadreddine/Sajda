 <div align="center">

# Sajda
### Direction, with intention.

A refined Qibla compass for Android.  
Designed to bring clarity, reliability, and purpose to every direction.

<br>

[![Release](https://img.shields.io/badge/Release-Sajda_v1.5.0-0A84FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/MeraniBadreddine/Sajda/releases/tag/v1.5.0)

**Version 1.5.0** &nbsp;·&nbsp; **Android Edition** &nbsp;·&nbsp; **Offline Qibla Calculations**

<br>

---

### FIND YOUR DIRECTION. WITH CONFIDENCE.

</div>

Sajda is an Android Qibla compass built around one essential purpose: helping you determine the direction of the Kaaba with clarity and dependable sensor behavior.

Designed with a focus on reliability, Sajda combines device location, compass sensors, and local geographic calculations to provide a straightforward experience without unnecessary complexity.

*Purposeful by design. Reliable by engineering. Focused on what matters.*

---

## Crafted with Precision

* **Reliable Location**  
  Sajda requests fresh GPS and network location updates together on supported Android versions. A bounded request lifecycle manages timeouts, cancellation, and stale callbacks. Cached locations may be used provisionally when recent enough, without being misrepresented as fresh.

* **Sensor-Aware Compass**  
  Uses the fused rotation vector when available, with an accelerometer and magnetometer fallback. Sensor axes are adjusted to the current display orientation, while heading smoothing maintains continuity across north.

* **Thoughtful Calibration Feedback**  
  Sensor confidence and magnetic-field warnings are treated as indicators, not guarantees of directional accuracy. Sajda avoids presenting unreliable or non-finite readings as valid compass output.

* **Universal Coordinates**  
  Supports valid geographic coordinates across the equator, dateline, and poles, including the coordinate pair (0°, 0°). Invalid and non-finite values are rejected.

* **Private by Design**  
  Qibla calculations run locally on the device. Location data is not uploaded to a server.

---

## Design Specifications

| Specification | Implementation |
| :--- | :--- |
| **Platform** | Native Android |
| **Purpose** | Qibla direction |
| **Location Sources** | GPS and network |
| **Location Handling** | Fresh and provisional cached fixes |
| **Primary Compass** | Rotation vector |
| **Fallback Compass** | Accelerometer + magnetometer |
| **Display Orientation** | Sensor-axis remapping |
| **Heading Processing** | Circular smoothing |
| **Magnetic Feedback** | Field-strength heuristics |
| **Coordinate Validation** | Geographic bounds and finite values |
| **Qibla Calculation** | Offline, local computation |
| **Location Privacy** | No server upload |

---

## Reliability Update — 1.5.0

Version 1.5.0 focuses on strengthening the foundations of location and compass behavior.

### Location Reliability

* Request GPS and network fixes together on Android 11+.
* Prevent one unavailable provider from blocking the other.
* Manage location requests through a single bounded lifecycle.
* Protect against timeouts, cancellation, and stale callbacks.
* Cancel pending work when the Activity leaves the foreground.
* Resume necessary location refreshes when the Activity returns.
* Accept cached locations only as provisional fixes no older than 24 hours.
* Validate coordinates when saving and loading local data.

### Compass and Sensor Reliability

* Prefer the fused rotation vector when available.
* Fall back to accelerometer and magnetometer when necessary.
* Remap sensor axes according to display rotation.
* Use an appropriate UI sampling rate.
* Smooth heading transitions across 359° and 1°.
* Ignore non-finite sensor frames.
* Initialize filters from real sensor samples.
* Start with an unknown calibration state rather than assuming medium accuracy.
* Separate magnetic-field warnings from the calculated Qibla bearing.
* Display an unavailable state when no usable compass combination exists.

### Rendering and Accessibility

* Restore Canvas drawing state on every rendering path.
* Provide a spoken compass description with the calculated bearing.
* Maintain a 48 dp touch target for the information button.

---

## Getting Started

1. **Download**  
   Visit the [GitHub Releases Page](https://github.com/MeraniBadreddine/Sajda/releases/tag/v1.5.0) and download the available release assets.

2. **Install**  
   Install the application on a compatible Android device.

3. **Grant Permissions**  
   Allow location access so Sajda can determine your geographic position and calculate the Qibla direction.

4. **Find Your Direction**  
   Hold your device flat and away from magnetic interference for more dependable compass readings.

*Compass performance depends on device sensors, calibration, and the surrounding magnetic environment.*

---

## Engineering Philosophy

* **Reliability First:** Location and sensor lifecycles are designed to handle failure and interruption safely.
* **Local by Design:** Geographic calculations remain on the device.
* **Honest Feedback:** Uncertain sensor conditions are never presented as guaranteed accuracy.
* **Purposeful Simplicity:** Every component serves the core experience.
* **Defensive Engineering:** Invalid coordinates, stale callbacks, and non-finite sensor values are handled explicitly.

---

## Architecture

Sajda combines Android location services, device motion sensors, and local geographic calculations.

```text
Location Layer
├── GPS and Network Providers
├── Bounded Request Lifecycle
├── Cached Location Validation
└── Lifecycle-Aware Updates

Compass Layer
├── Rotation Vector
├── Accelerometer + Magnetometer
├── Display Axis Remapping
└── Heading Smoothing

Qibla Layer
├── Geographic Coordinate Validation
├── Local Bearing Calculation
└── Direction Presentation

Interface Layer
├── Compass Visualization
├── Accessibility Description
└── Information Interface
```

---

## Build from Source

Clone the repository:

```bash
git clone https://github.com/MeraniBadreddine/Sajda.git
cd Sajda
```

Run the tests and assemble a debug build:

```bash
./gradlew :app:test :app:assembleDebug
```

**Built APK location:**

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Verification Status

Source-level changes and static checks were performed for this release.

The following verification steps remain to be completed on an Android development environment:

* Full Android Gradle build.
* Instrumentation test execution.
* Physical sensor calibration testing.
* Real-device location and compass validation.

Sensor confidence indicates reported sensor status and possible magnetic-field anomalies. It is not a guarantee of Qibla angular accuracy.

---

<div align="center">

### Less uncertainty. Clearer direction.

**Sajda · Android Edition · Version 1.5.0**

<sub>An independent open-source project.</sub>

</div>
