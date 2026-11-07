package sa.factory.masarrtknavigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.masarrtknavigation.data.repository.MasarRtkRepositoryImpl
import sa.factory.masarrtknavigation.engine.*
import sa.factory.masarrtknavigation.hardware.HardwareSensorDriver
import sa.factory.masarrtknavigation.model.MasarRtkItem

class MasarRtkRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = MasarRtkRepositoryImpl(sensorDriver)

        val item1 = MasarRtkItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = MasarRtkItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testRtkFixQualityAndSurveyTolerance() {
        val engine = RtkDifferentialGnssEngine()
        val fixedFix = RtkGnssFix(
            latitude = 24.7136,
            longitude = 46.6753,
            altitudeMslMeters = 610.0,
            fixQuality = GnssFixQuality.RTK_FIXED,
            satellitesUsed = 24,
            hdop = 0.7f,
            speedKmh = 0f,
            headingDeg = 0f,
            correctionAgeSec = 1.0f,
            baseStationId = "BASE-01"
        )
        val floatFix = fixedFix.copy(fixQuality = GnssFixQuality.RTK_FLOAT, hdop = 1.8f)

        assertTrue("RTK Fixed with low HDOP must be survey ready", engine.isCentimeterSurveyReady(fixedFix))
        assertFalse("RTK Float must not qualify for centimeter survey ready", engine.isCentimeterSurveyReady(floatFix))
        assertEquals(0.015f, GnssFixQuality.RTK_FIXED.typicalAccuracyMeters, 0.001f)
    }

    @Test
    fun testNmeaGnggaSentenceFormattingAndChecksum() {
        val engine = RtkDifferentialGnssEngine()
        val fix = RtkGnssFix(
            latitude = 24.7136,
            longitude = 46.6753,
            altitudeMslMeters = 615.5,
            fixQuality = GnssFixQuality.RTK_FIXED,
            satellitesUsed = 22,
            hdop = 0.8f,
            speedKmh = 35.0f,
            headingDeg = 180.0f,
            correctionAgeSec = 1.2f,
            baseStationId = "BASE-RUH"
        )
        val sentence = engine.buildGnggaSentence(fix)

        assertTrue("NMEA sentence must start with $", sentence.startsWith("$"))
        assertTrue("NMEA sentence must contain GNGGA", sentence.contains("GNGGA"))
        assertTrue("NMEA sentence must contain checksum separator *", sentence.contains("*"))
        val checksumPart = sentence.substringAfterLast("*")
        assertEquals("NMEA checksum must be exactly 2 hex chars", 2, checksumPart.length)
    }

    @Test
    fun testHaversineDistanceCalculation() {
        val engine = RtkDifferentialGnssEngine()
        // Distance between Kingdom Tower (24.7114, 46.6744) and Faisaliah Tower (24.6903, 46.6853) in Riyadh (~2.6 km)
        val distanceMeters = engine.calculateHaversineDistanceMeters(
            24.7114, 46.6744,
            24.6903, 46.6853
        )
        assertTrue("Distance between Riyadh towers should be approx 2.6km +/- 300m", distanceMeters in 2300.0..2900.0)
    }

    @Test
    fun testImuDeadReckoningPropagation() {
        val drEngine = ImuDeadReckoningEngine()
        val initialFix = RtkGnssFix(
            latitude = 24.7000,
            longitude = 46.7000,
            altitudeMslMeters = 600.0,
            fixQuality = GnssFixQuality.RTK_FIXED,
            satellitesUsed = 20,
            hdop = 0.8f,
            speedKmh = 60.0f,
            headingDeg = 90.0f, // Heading East
            correctionAgeSec = 0.5f,
            baseStationId = "BASE-DR"
        )
        val initialState = drEngine.anchorFromRtkFix(initialFix)
        assertFalse(initialState.isGpsOutageActive)

        // Simulate 2 seconds of tunnel travel heading due East at 60 km/h (16.67 m/s -> ~33.3 meters)
        val imu = ImuReading(0f, 0f, 9.81f, 0f, 0f, 0f)
        val propagated = drEngine.propagateDeadReckoning(initialState, imu, speedKmh = 60.0f, deltaTimeSec = 2.0f)

        assertTrue("GPS outage mode must be active during DR", propagated.isGpsOutageActive)
        assertEquals("Heading should remain 90 deg due to zero yaw rate", 90.0f, propagated.currentHeadingDeg, 0.1f)
        assertTrue("Cumulative distance should be approx 33.3m", propagated.cumulativeDistanceMeters in 32.0..35.0)
        assertTrue("Longitude should have increased (traveled East)", propagated.estimatedLongitude > initialFix.longitude)
    }

    @Test
    fun testHardwareSensorDriverEmitsValidTelemetry() = runBlocking {
        val driver = HardwareSensorDriver()
        val frames = driver.observePeripheralStream().take(2).toList()

        assertEquals(2, frames.size)
        assertEquals("CONNECTING", frames[0].connectionState)
        assertEquals("ONLINE", frames[1].connectionState)
        assertTrue(frames[1].livePayload.contains("ONLINE"))
        assertTrue(frames[1].livePayload.contains("RTK_FIXED"))
        assertTrue(frames[1].livePayload.contains("NMEA:"))
    }
}
