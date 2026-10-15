package app.grip_gains_companion.service

import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.model.ProgressorState
import app.grip_gains_companion.util.StatisticsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LongRepTest {
    @Test(timeout = 30000) fun tenMinuteFrezRepKeepsEverySampleAndExactStatistics() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false; canEngage = true; displayDeviceType = DeviceType.FREZ_DYNO }
        handler.processSample(0.0, 0)
        val weights = (1..60000).map { 10.0 + it % 7 }
        handler.processSample(weights.first(), 10000)
        val engagedState = handler.state.value
        assertTrue(engagedState is ProgressorState.Gripping)
        val started = System.nanoTime()
        for (i in 1 until weights.size) handler.processSample(weights[i], (i + 1) * 10000L)
        println("600-second FREZ rep processed in ${(System.nanoTime()-started)/1000000} ms")
        assertSame(engagedState, handler.state.value)
        assertEquals(StatisticsUtils.mean(weights), handler.sessionMean.value!!, 1e-9)
        assertEquals(StatisticsUtils.standardDeviation(weights), handler.sessionStdDev.value!!, 1e-9)
        handler.processSample(0.0, 600010000)
        assertEquals(weights, handler.sessionRepResults.value.single().samples)
        assertEquals(600.0, handler.sessionRepResults.value.single().duration, 0.0)
        handler.processSample(20.0, 600020000)
        handler.processSample(22.0, 600030000)
        handler.processSample(0.0, 600040000)
        assertEquals(listOf(20.0, 22.0), handler.sessionRepResults.value.last().samples)
        assertEquals(weights, handler.sessionRepResults.value.first().samples)
    }
    @Test(timeout = 30000) fun longWeightHoldHasExactMedianAndResetsOnReengage() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false; canEngage = false }
        handler.processSample(0.0, 0)
        val values = (1..30000).map { 10.0 + (it * 17 % 103) }
        values.forEachIndexed { index, value -> handler.processSample(value, (index + 1)*10000L) }
        assertEquals(StatisticsUtils.median(values), handler.weightMedian.value!!, 0.0)
        handler.processSample(0.0, 300010000)
        assertEquals(StatisticsUtils.trimmedMedian(values), handler.weightMedian.value!!, 0.0)
        handler.processSample(25.0, 300020000)
        assertEquals(25.0, handler.weightMedian.value!!, 0.0)
        handler.processSample(27.0, 300030000)
        assertEquals(26.0, handler.weightMedian.value!!, 0.0)
        handler.reset()
        handler.processSample(0.0, 0)
        handler.canEngage = true
        handler.processSample(40.0, 10000)
        handler.processSample(42.0, 20000)
        assertEquals(41.0, handler.sessionMean.value!!, 0.0)
    }
    @Test fun onlineMedianAndDeviationMatchBatchForEveryPrefix() {
        val median = RunningForceMedian()
        val stats = RunningForceStatistics()
        val values = ArrayList<Double>()
        repeat(1000) { i ->
            val value = ((i * 7919) % 101 - 50).toDouble()
            values.add(value); median.add(value); stats.add(value)
            assertEquals(StatisticsUtils.median(values), median.value, 0.0)
            assertEquals(StatisticsUtils.mean(values), stats.mean, 1e-10)
            assertEquals(StatisticsUtils.standardDeviation(values), stats.standardDeviation, 1e-10)
        }
        median.clear(); stats.clear(); median.add(-5.0); stats.add(-5.0)
        assertEquals(-5.0, median.value, 0.0)
        assertEquals(-5.0, stats.mean, 0.0)
        assertEquals(0.0, stats.standardDeviation, 0.0)
    }
}
