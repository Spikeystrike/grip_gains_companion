package app.grip_gains_companion.service

import app.grip_gains_companion.model.ForceHistoryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ForceGraphHistoryTest {
    @Test fun windowUsesSamplesEvenWhenPhoneTimeIsFarAhead() {
        val samples = (0..100).map { ForceHistoryEntry(Date(it * 100L), it.toDouble()) }
        val visible = visibleForceHistory(samples, 5)
        assertEquals(51, visible.size)
        assertEquals(samples.last(), visible.last())
        assertEquals(samples, visibleForceHistory(samples, 0))
    }
    @Test fun longSessionsStayBoundedAndKeepPeaksAndRecentData() {
        val history = ForceGraphHistory()
        var snapshot = emptyList<ForceHistoryEntry>()
        var updates = 0
        repeat(200001) { i ->
            history.add(ForceHistoryEntry(Date(i * 10L), when(i) { 10 -> -123.0; 21 -> 456.0; else -> 20.0 }))?.let {
                snapshot = it; updates++; assertTrue(it.size <= 8192)
            }
        }
        assertEquals(40001, updates)
        assertEquals(0L, snapshot.first().timestamp.time)
        assertEquals(2000000L, snapshot.last().timestamp.time)
        assertEquals(-123.0, snapshot.minOf { it.force }, 0.0)
        assertEquals(456.0, snapshot.maxOf { it.force }, 0.0)
        assertEquals(3001, visibleForceHistory(snapshot, 30).size)
        assertTrue(snapshot.zipWithNext().all { (a,b) -> a.timestamp <= b.timestamp })
    }
    @Test fun resetDropsPriorSessionAndPublishesFirstSampleImmediately() {
        val history = ForceGraphHistory()
        history.add(ForceHistoryEntry(Date(100), 20.0))
        history.clear()
        val fresh = history.add(ForceHistoryEntry(Date(101), 0.0))!!
        assertEquals(1, fresh.size)
        assertEquals(0.0, fresh.single().force, 0.0)
    }
    @Test fun deviceClockResetAfterTheOriginalAnchorDoesNotSendGraphBackwards() = runBlocking {
        val handler = ProgressorHandler().apply { enableCalibration = false }
        handler.processSample(10.0, 1000000)
        handler.processSample(20.0, 11000000)
        val previous = handler.forceHistory.value.last().timestamp.time
        handler.processSample(30.0, 2000000)
        assertTrue(handler.forceHistory.value.last().timestamp.time > previous)
        assertEquals(30.0, handler.currentForce.value, 0.0)
        assertTrue(visibleForceHistory(handler.forceHistory.value, 5).isNotEmpty())
    }
}
