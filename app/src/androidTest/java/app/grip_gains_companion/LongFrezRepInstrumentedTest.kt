package app.grip_gains_companion

import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.service.ProgressorHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LongFrezRepInstrumentedTest {
    @Test fun tenMinuteRepOnAndroidMainThreadRetainsAllSamples() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runBlocking {
                val handler = ProgressorHandler().apply {
                    enableCalibration = false; canEngage = true; displayDeviceType = DeviceType.FREZ_DYNO
                }
                handler.processSample(0.0, 0)
                var started = System.nanoTime()
                for (i in 1..60000) {
                    handler.processSample(20.0 + i % 2, i * 10000L)
                    if (i % 15000 == 0) {
                        println("150-second rep segment ${i / 15000}: ${(System.nanoTime() - started)/1000000} ms processing")
                        started = System.nanoTime()
                    }
                }
                assertEquals(20.5, handler.sessionMean.value!!, 1e-9)
                assertEquals(0.5, handler.sessionStdDev.value!!, 1e-9)
                handler.processSample(0.0, 600010000)
                assertEquals(60000, handler.sessionRepResults.value.single().samples.size)
                assertEquals(600.0, handler.sessionRepResults.value.single().duration, 0.0)
            }
        }
    }
}
