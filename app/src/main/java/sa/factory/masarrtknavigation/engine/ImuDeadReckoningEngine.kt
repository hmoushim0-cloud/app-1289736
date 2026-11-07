package sa.factory.masarrtknavigation.engine

import kotlin.math.*

/**
 * Raw 6-Axis Inertial Measurement Unit reading (Accelerometer + Gyroscope).
 */
data class ImuReading(
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroPitchRate: Float,
    val gyroRollRate: Float,
    val gyroYawRate: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Fused Dead Reckoning navigation state.
 */
data class DeadReckoningState(
    val estimatedLatitude: Double,
    val estimatedLongitude: Double,
    val currentHeadingDeg: Float,
    val cumulativeDistanceMeters: Double,
    val isGpsOutageActive: Boolean,
    val gyroBiasDegPerSec: Float = 0.05f
)

/**
 * 6-Axis IMU Dead Reckoning Engine for seamless urban canyon and tunnel navigation.
 */
class ImuDeadReckoningEngine {

    companion object {
        const val METERS_PER_LATITUDE_DEG = 111139.0
    }

    /**
     * Integrates IMU angular velocity and odometer displacement to propagate position during GPS outage.
     */
    fun propagateDeadReckoning(
        previousState: DeadReckoningState,
        imu: ImuReading,
        speedKmh: Float,
        deltaTimeSec: Float
    ): DeadReckoningState {
        // Integrate gyro yaw rate to update heading
        val rawDeltaYaw = imu.gyroYawRate * deltaTimeSec
        var updatedHeading = (previousState.currentHeadingDeg + rawDeltaYaw) % 360f
        if (updatedHeading < 0) updatedHeading += 360f

        // Convert speed to distance in meters
        val speedMps = (speedKmh * 1000f) / 3600f
        val deltaDistanceM = speedMps * deltaTimeSec

        // Project coordinate using planar approximation
        val headingRad = Math.toRadians(updatedHeading.toDouble())
        val deltaNorth = deltaDistanceM * cos(headingRad)
        val deltaEast = deltaDistanceM * sin(headingRad)

        val deltaLat = deltaNorth / METERS_PER_LATITUDE_DEG
        val metersPerLonDeg = METERS_PER_LATITUDE_DEG * cos(Math.toRadians(previousState.estimatedLatitude))
        val deltaLon = if (abs(metersPerLonDeg) > 0.0001) deltaEast / metersPerLonDeg else 0.0

        val newLat = previousState.estimatedLatitude + deltaLat
        val newLon = previousState.estimatedLongitude + deltaLon

        return DeadReckoningState(
            estimatedLatitude = newLat,
            estimatedLongitude = newLon,
            currentHeadingDeg = updatedHeading,
            cumulativeDistanceMeters = previousState.cumulativeDistanceMeters + deltaDistanceM,
            isGpsOutageActive = true,
            gyroBiasDegPerSec = previousState.gyroBiasDegPerSec
        )
    }

    /**
     * Re-anchors dead reckoning filter upon receiving authentic RTK carrier-phase fix.
     */
    fun anchorFromRtkFix(fix: RtkGnssFix): DeadReckoningState {
        return DeadReckoningState(
            estimatedLatitude = fix.latitude,
            estimatedLongitude = fix.longitude,
            currentHeadingDeg = fix.headingDeg,
            cumulativeDistanceMeters = 0.0,
            isGpsOutageActive = false
        )
    }
}
