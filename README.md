# تطبيق مسار الفضائي — توجيه تصوير فيديو للمحتوى — (`masar-rtk-navigation-1289736`)
> منظومة navigation المتكاملة مع ربط GPS RTK High-Precision Receiver + Gyroscope

- **Project ID**: `#1289736`
- **Commercial Archetype**: `navigation`
- **Bound Hardware Peripheral**: `GPS RTK High-Precision Receiver + Gyroscope`
- **Package Name**: `sa.factory.masarrtknavigation`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update]

### Implemented Production Engines & Hardware Binding
1. **RtkDifferentialGnssEngine (`sa.factory.masarrtknavigation.engine.RtkDifferentialGnssEngine`)**:
   - Supports sub-centimeter multi-constellation RTK GNSS fix states (GPS, GLONASS, Galileo, BeiDou).
   - Generates and parses high-precision NMEA 0183 sentences ($GNGGA) with automated XOR checksum validation.
   - Calculates geodetic WGS-84 great-circle distances using the Haversine formula.
   - Evaluates centimeter-level surveying readiness based on carrier-phase integer ambiguity, HDOP, and correction age.

2. **ImuDeadReckoningEngine (`sa.factory.masarrtknavigation.engine.ImuDeadReckoningEngine`)**:
   - Fuses 6-axis IMU readings (3-axis accelerometer + 3-axis gyroscope) with vehicle odometer velocity.
   - Seamlessly activates Dead Reckoning (DR) during GNSS signal degradation in urban canyons, underpasses, and tunnels.
   - Re-anchors navigation filters upon carrier-phase lock re-acquisition.

3. **Hardware & UI Hardening**:
   - Integrated live reactive StateFlow telemetry stream in `HardwareSensorDriver.kt`.
   - Resolved Kotlin single-quote syntax issues in string templates in `HomeScreen.kt` and `TelemetryScreen.kt`.
   - Added missing Compose typography and unit imports in `MainActivity.kt`.
   - Cleaned UI badges to strictly enforce zero emoji and ASCII compliance.
   - Relocated regression test suite to `app/src/test/java/sa/factory/masarrtknavigation/` with 7 comprehensive unit tests (100% assertions green).

### Build & Verification Commands
```bash
# Run unit regression test suite
./gradlew test

# Assemble debug APK for Pixel 6 emulator testing
./gradlew assembleDebug
```
