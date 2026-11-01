# Sajda

**Direction, with intention.**

## 1.5.0 — Reliability Update

This release focuses on trustworthy location and compass behavior before adding new features.

### Location
- Request fresh network and GPS fixes together on Android 11+; a null result from one provider no longer blocks the other.
- Keep a single bounded request lifecycle with timeout, cancellation, and stale-callback protection.
- Cancel pending requests and listeners when the Activity leaves the foreground; restart a needed refresh on resume.
- Use a cached fix only as provisional, and accept it only when no older than 24 hours.
- Never relabel a cached fix as fresh after a failed refresh.
- Validate finite latitude/longitude and geographic bounds when saving and loading local location data.
- Treat approximate location as valid input; no manual coordinate entry is required.

### Compass and sensors
- Use the fused rotation vector when available and collect raw magnetic-field samples separately to detect possible interference.
- Fall back to accelerometer + magnetometer when rotation-vector registration fails.
- Remap sensor axes to the current display rotation before deriving azimuth.
- Use the UI sensor sampling rate rather than a game-rate stream for a compass screen.
- Keep circular heading smoothing stable across north (359° ↔ 1°) and ignore non-finite frames.
- Initialize sensor filters from the first real sample to avoid artificial low-field readings during startup.
- Start with an unknown/unreliable calibration state rather than assuming medium accuracy.
- Keep field-strength warnings separate from the mathematical Qibla bearing; they are heuristics, not a promise of angular accuracy.
- Avoid publishing non-finite heading values.
- Show a clear unavailable state when the device has no usable compass sensor combination.

### Geometry, rendering, and accessibility
- Accept all valid geographic coordinates, including (0°, 0°); reject non-finite and out-of-range values.
- Restore the Canvas save layer on every drawing path, including a zero-size view.
- Provide a spoken compass description with the calculated bearing and a 48 dp information-button touch target.

### Quality
- Expand coordinate validation tests for the equator, dateline, poles, NaN, and infinity.
- Keep Qibla calculations offline and local. Location is not uploaded to a server.

### Verification note
Source-level changes and static checks were performed for this package. A full Android Gradle build, instrumentation run, sensor calibration test, and real-device validation still need to be completed on an Android development machine. Sensor confidence is an indicator of sensor-reported status and field anomalies, not a guaranteed Qibla accuracy measurement.

```bash
./gradlew :app:test :app:assembleDebug
```
