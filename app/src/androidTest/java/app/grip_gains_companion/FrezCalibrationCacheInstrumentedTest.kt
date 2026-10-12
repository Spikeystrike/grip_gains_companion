package app.grip_gains_companion

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.service.ble.FrezCalibrationCache
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FrezCalibrationCacheInstrumentedTest {
    @Test fun cachePersistsAndSeparatesDevicesAndRejectsCorruption() {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "frez-cache-test-" + UUID.randomUUID()
        val context = object: ContextWrapper(original) {
            override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences = original.getSharedPreferences(name, mode)
        }
        try {
            val cache = FrezCalibrationCache(context)
            assertNull(cache.read("new"))
            assertTrue(cache.save("A", 0.0123456789))
            assertTrue(cache.save("B", -0.03))
            val reopened = FrezCalibrationCache(context)
            assertEquals(0.0123456789, reopened.read("A")!!, 0.0)
            assertEquals(-0.03, reopened.read("B")!!, 0.0)
            assertTrue(reopened.save("A", 0.04))
            assertEquals(0.04, FrezCalibrationCache(context).read("A")!!, 0.0)
            for (bad in listOf("garbage", "NaN", "0.0", "Infinity")) {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("C", bad).commit()
                assertNull(cache.read("C"))
            }
        } finally { original.deleteSharedPreferences(name) }
    }
}
