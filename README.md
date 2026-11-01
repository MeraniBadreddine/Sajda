 <div align="center">

# Sajda
### Direction, with intention.

A refined Qibla compass experience for Android.  
Clarity in direction. Precision in detail.

<br>

[![Download v1.6.0](https://img.shields.io/badge/Download-v1.6.0-0A84FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/MeraniBadreddine/Sajda/releases/download/v1.6.0/app-release.apk)

[![Version](https://img.shields.io/badge/Version-1.6.0-30D158?style=flat-square)](https://github.com/MeraniBadreddine/Sajda/releases/tag/v1.6.0)
![Platform](https://img.shields.io/badge/Platform-Android-0A84FF?style=flat-square)
![License](https://img.shields.io/badge/Project-Open%20Source-8E8E93?style=flat-square)

**Version 1.6.0** &nbsp;·&nbsp; **Native Android** &nbsp;·&nbsp; **Minimal by Design**

<br>

---

### FIND YOUR DIRECTION.

</div>

Sajda is a focused Qibla compass designed to make finding the direction of prayer feel simple, clear, and intuitive.

Built around Android's sensor capabilities, Sajda combines a refined compass interface with location-based Qibla direction and sensor confidence feedback. Every element is designed to serve one purpose: helping you understand your direction without unnecessary distractions.

*Less visual noise. More clarity. Direction with intention.*

---

## Crafted with Precision

* **A Refined Compass**  
  A carefully designed compass dial with clear cardinal directions, precise degree markings, and a distinctive Qibla indicator. Smooth motion keeps the interface responsive while preserving visual stability.

* **Sensor Awareness**  
  Sajda uses device orientation and magnetic sensor data to determine heading. Sensor confidence is evaluated according to the data actually available, with calibration guidance when accuracy is unknown or insufficient.

* **Location-Based Direction**  
  Uses the device's location to determine the Qibla direction. Location data is validated before use, and cached location information is subject to age checks.

* **A Considered Interface**  
  A minimal visual language with refined typography, balanced spacing, subtle surfaces, and a focused information hierarchy.

* **A Distinctive App Identity**  
  A custom adaptive launcher icon, including round and monochrome variants, completes the visual experience across supported Android launchers.

---

## Design Specifications

| Specification | Implementation |
| :--- | :--- |
| **Compass** | Native Android sensor-based heading |
| **Qibla Direction** | Location-based direction calculation |
| **Sensor Confidence** | Accuracy-aware confidence feedback |
| **Calibration** | Guidance when sensor accuracy is uncertain |
| **Compass Dial** | 6° minor ticks and 30° major markings |
| **Direction Markers** | North, East, South, and West |
| **Qibla Indicator** | Distinctive blue directional marker |
| **Motion** | Smooth, frame-rate-independent animation |
| **Location Storage** | Validated coordinates and fix timestamp |
| **App Icon** | Adaptive, round, and monochrome variants |
| **Interface** | Arabic, English, and French resources |

---

## Getting Started

1. **Download**  
   Download the signed Android APK directly from the official GitHub release.

   [Download Sajda v1.6.0](https://github.com/MeraniBadreddine/Sajda/releases/download/v1.6.0/app-release.apk)

2. **Install**  
   Open the downloaded APK and follow the Android installation instructions. You may need to allow installation from your browser or file manager.

3. **Find Your Direction**  
   Allow location access when requested and use the compass to view the Qibla direction.

4. **Calibrate When Needed**  
   If the app indicates that sensor accuracy is uncertain, follow the calibration guidance and move the phone in a figure-eight motion.

*Compass accuracy depends on the device sensors, surrounding magnetic interference, and the quality of the available location data.*

---

## Engineering Philosophy

* **Clarity First:** The Qibla direction remains the primary focus.
* **Honest Feedback:** Sensor confidence reflects available accuracy information rather than assuming reliability.
* **Purposeful Motion:** Animations are smooth, restrained, and designed to stop when the interface settles.
* **Minimal Data:** Location storage is limited to the coordinates and timestamp needed by the application.
* **Native by Design:** Android capabilities are used directly, without unnecessary complexity.

---

## Architecture

Sajda is structured around focused Android components that separate sensor processing, location handling, and visual presentation.

```text
MainActivity
└── Main application interface and lifecycle

CompassEngine
└── Sensor data, heading and confidence evaluation

LocationStore
└── Location validation, storage and age checks

CompassView
└── Compass dial, Qibla indicator and animation

res/
├── drawable/
│   └── Interface backgrounds and visual assets
├── mipmap/
│   └── Adaptive launcher icon resources
└── values/
    └── Themes, typography and localized strings
```

---

## Build from Source

```bash
git clone https://github.com/MeraniBadreddine/Sajda.git
cd Sajda
gradle :app:test :app:assembleDebug
```

**Debug APK location:**

`app/build/outputs/apk/debug/app-debug.apk`

**Signed Release APK location:**

`app/build/outputs/apk/release/app-release.apk`

---

## Environment Specification

| Parameter | Value |
| :--- | :--- |
| **Version** | 1.6.0 |
| **Version Code** | 13 |
| **Minimum Android** | Android 8.0 (API Level 26) |
| **Target SDK** | API Level 35 |
| **Platform** | Android |
| **Language** | Kotlin |

---

## Quality and Verification

The v1.6.0 release has successfully passed:

* Android Gradle release build.
* APK signature verification (APK Signature Scheme v2).
* Installation and functional testing on a Google Pixel 7.

The source project also successfully passed the Gradle debug build and unit tests.

---

<div align="center">

### Less distraction. Clearer direction.

**Sajda · Android Edition · Version 1.6.0**

<sub>An independent open-source project.</sub>

</div>
