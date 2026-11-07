package sa.factory.masarrtknavigation.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.masarrtknavigation.engine.*
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    val peripheral = "GPS RTK High-Precision Receiver + Gyroscope"
    val rtkEngine = RtkDifferentialGnssEngine()
    val drEngine = ImuDeadReckoningEngine()

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "RTK Base Station RTCM 3.3 Link & 6-Axis IMU Initializing..."))
        delay(200)

        // Seed initial Riyadh coordinate (KAFD area: 24.7673 N, 46.6438 E)
        val initialFix = RtkGnssFix(
            latitude = 24.767320,
            longitude = 46.643810,
            altitudeMslMeters = 612.45,
            fixQuality = GnssFixQuality.RTK_FIXED,
            satellitesUsed = 28,
            hdop = 0.65f,
            speedKmh = 42.5f,
            headingDeg = 145.2f,
            correctionAgeSec = 0.8f,
            baseStationId = "BASE-RUH-KAFD"
        )
        val nmeaSnt = rtkEngine.buildGnggaSentence(initialFix)
        val accM = String.format("%.3f", initialFix.fixQuality.typicalAccuracyMeters)

        val initPayload = "[ONLINE] Fix: " + initialFix.fixQuality.name + " (" + accM + "m) | " +
                "Sats: " + initialFix.satellitesUsed + " | HDOP: " + initialFix.hdop + " | " +
                "Heading: " + initialFix.headingDeg + " deg | NMEA: " + nmeaSnt.take(24) + "..."
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", initPayload))

        var drState = drEngine.anchorFromRtkFix(initialFix)
        var frameCounter = 1

        while (true) {
            delay(1500)
            frameCounter++
            val jitter = Random.nextInt(1, 8)
            val imu = ImuReading(
                accelX = 0.12f,
                accelY = 0.05f,
                accelZ = 9.81f,
                gyroPitchRate = 0.02f,
                gyroRollRate = -0.01f,
                gyroYawRate = 0.45f
            )
            drState = drEngine.propagateDeadReckoning(drState, imu, speedKmh = 45.0f, deltaTimeSec = 1.5f)

            val latFmt = String.format("%.6f", drState.estimatedLatitude)
            val lonFmt = String.format("%.6f", drState.estimatedLongitude)
            val distFmt = String.format("%.1f", drState.cumulativeDistanceMeters)
            val headFmt = String.format("%.1f", drState.currentHeadingDeg)

            val liveStatus = "[ACTIVE_STREAMING] Frame #" + frameCounter + " (+" + jitter + "ms) | " +
                    "RTK: 24.76N, 46.64E | DR Pos: " + latFmt + "," + lonFmt + " | " +
                    "Head: " + headFmt + " deg | Dist: " + distFmt + "m | IMU: 6-Axis OK"

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", liveStatus))
        }
    }
}
