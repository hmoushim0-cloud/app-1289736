package sa.factory.masarrtknavigation.engine

import kotlin.math.*

/**
 * GNSS positioning fix status mode.
 */
enum class GnssFixQuality(val code: Int, val description: String, val typicalAccuracyMeters: Float) {
    NO_FIX(0, "No Fix", 99.0f),
    AUTONOMOUS_GPS(1, "Single Point GPS", 3.5f),
    DIFFERENTIAL_DGPS(2, "Differential GPS (SBAS/DGPS)", 0.8f),
    RTK_FLOAT(5, "RTK Float (Carrier Ambiguity Unresolved)", 0.25f),
    RTK_FIXED(4, "RTK Fixed (Centimeter Carrier Integer Solved)", 0.015f)
}

/**
 * High-precision geodetic position telemetry frame.
 */
data class RtkGnssFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMslMeters: Double,
    val fixQuality: GnssFixQuality,
    val satellitesUsed: Int,
    val hdop: Float,
    val speedKmh: Float,
    val headingDeg: Float,
    val correctionAgeSec: Float,
    val baseStationId: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * NMEA 0183 & RTCM 3.3 Differential GNSS High-Precision Engine.
 */
class RtkDifferentialGnssEngine {

    companion object {
        const val WGS84_EARTH_RADIUS_KM = 6371.0088
    }

    /**
     * Formats high-precision NMEA 0183 $GNGGA sentence string.
     */
    fun buildGnggaSentence(fix: RtkGnssFix): String {
        val latDeg = abs(fix.latitude).toInt()
        val latMin = (abs(fix.latitude) - latDeg) * 60.0
        val latDir = if (fix.latitude >= 0) "N" else "S"
        val latStr = String.format("%02d%07.4f,%s", latDeg, latMin, latDir)

        val lonDeg = abs(fix.longitude).toInt()
        val lonMin = (abs(fix.longitude) - lonDeg) * 60.0
        val lonDir = if (fix.longitude >= 0) "E" else "W"
        val lonStr = String.format("%03d%07.4f,%s", lonDeg, lonMin, lonDir)

        val altStr = String.format("%.2f,M", fix.altitudeMslMeters)
        val hdopStr = String.format("%.2f", fix.hdop)
        val ageStr = String.format("%.1f", fix.correctionAgeSec)

        val body = "GNGGA,120000.00," + latStr + "," + lonStr + "," + fix.fixQuality.code +
                "," + fix.satellitesUsed + "," + hdopStr + "," + altStr + ",-22.0,M," + ageStr + "," + fix.baseStationId
        val checksum = calculateNmeaChecksum(body)
        return "$" + body + "*" + checksum
    }

    /**
     * Computes great-circle distance between two geodetic coordinates in meters.
     */
    fun calculateHaversineDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return WGS84_EARTH_RADIUS_KM * c * 1000.0
    }

    /**
     * Evaluates whether RTK positioning meets centimeter surveying tolerances.
     */
    fun isCentimeterSurveyReady(fix: RtkGnssFix): Boolean {
        return fix.fixQuality == GnssFixQuality.RTK_FIXED &&
                fix.hdop <= 1.2f &&
                fix.satellitesUsed >= 18 &&
                fix.correctionAgeSec <= 2.5f
    }

    private fun calculateNmeaChecksum(sentenceBody: String): String {
        var checksum = 0
        for (ch in sentenceBody) {
            checksum = checksum xor ch.code
        }
        return String.format("%02X", checksum)
    }
}
