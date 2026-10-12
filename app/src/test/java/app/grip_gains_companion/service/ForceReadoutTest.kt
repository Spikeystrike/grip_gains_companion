package app.grip_gains_companion.service

import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.util.WeightFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ForceReadoutTest {
    @Test fun onlyFrezIsSmoothedAndRawMeasurementsStayUnchanged() = runBlocking {
        for (device in DeviceType.entries + listOf(null)) {
            val handler = ProgressorHandler().apply { enableCalibration = false; displayDeviceType = device }
            handler.processSample(20.0, 0)
            handler.processSample(30.0, 100000)
            assertEquals(30.0, handler.currentForce.value, 0.0)
            assertEquals(30.0, handler.forceHistory.value.last().force, 0.0)
            if (device == DeviceType.FREZ_DYNO) assertTrue(handler.displayForce.value in 23.0..25.0)
            else assertEquals(30.0, handler.displayForce.value, 0.0)
        }
    }
    @Test fun deviceChangesAndZeroingResetTheFilter() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false; displayDeviceType = DeviceType.FREZ_DYNO }
        handler.processSample(20.0, 0)
        handler.processSample(30.0, 100000)
        handler.displayDeviceType = DeviceType.TINDEQ_PROGRESSOR
        assertEquals(30.0, handler.displayForce.value, 0.0)
        handler.displayDeviceType = DeviceType.FREZ_DYNO
        handler.processSample(40.0, 200000)
        assertEquals(40.0, handler.displayForce.value, 0.0)
        handler.resetDisplayReadout()
        handler.processSample(0.0, 300000)
        assertEquals(0.0, handler.displayForce.value, 0.0)
        handler.reset()
        assertEquals(0.0, handler.displayForce.value, 0.0)
    }
    @Test fun updatesAreLimitedAndGapsDiscardOldValues() {
        val readout = ForceReadout()
        readout.sample(20.0, 0)
        var updates = 0
        for (i in 1..100) readout.sample(if(i%2==0) 21.0 else 19.0, i*10L)?.let {
            updates++; assertTrue(it in 19.7..20.3)
        }
        assertEquals(10, updates)
        assertEquals(0.0, readout.sample(0.0, 3000)!!, 0.0)
        assertEquals(5.0, readout.sample(5.0, 0)!!, 0.0)
    }
    @Test fun roundedZeroNeverHasAMinusInEitherUnitOrPrecision() {
        for (lbs in listOf(false, true)) for (decimals in 0..3) {
            assertFalse(WeightFormatter.format(-0.0, lbs, decimals=decimals).startsWith("-"))
            assertFalse(WeightFormatter.format(-0.00001, lbs, decimals=decimals).startsWith("-"))
        }
        assertEquals("0.0 kg", WeightFormatter.format(-0.049, false))
        assertEquals("-0.1 kg", WeightFormatter.format(-0.06, false))
        assertEquals("-0.1 lbs", WeightFormatter.format(-0.04, true))
        assertEquals("0.0", WeightFormatter.format(-0.01, true, includeUnit=false))
    }
}
