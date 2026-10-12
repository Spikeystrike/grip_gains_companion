package app.grip_gains_companion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.model.ForceDevice
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import app.grip_gains_companion.service.ble.FrezDynoConnection

class FrezDiscoveryInstrumentedTest {
    private fun result(bytes: ByteArray): ScanResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT)
        val parse = ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java).apply { isAccessible = true }
        return ScanResult(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:66"), parse.invoke(null, bytes) as ScanRecord, -50, 1000000)
    }
    @Test fun advertisedFrezNameIsDetectedAndDisplayed() {
        try {
            val name = "FrezDyno-123456".toByteArray()
            val scan = result(byteArrayOf((name.size+1).toByte(), 9)+name)
            assertEquals(DeviceType.FREZ_DYNO, DeviceType.detect(scan))
            assertEquals("FrezDyno-123456", ForceDevice.fromScanResult(scan)?.name)
        } finally { InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity() }
    }
    @Test fun frezServiceIsRecognizedWithoutAName() {
        try {
            val uuid=FrezDynoConnection.SERVICE
            val data=ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN).put(17).put(7).putLong(uuid.leastSignificantBits).putLong(uuid.mostSignificantBits).array()
            assertEquals(DeviceType.FREZ_DYNO, DeviceType.detect(result(data)))
        } finally { InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity() }
    }
}
