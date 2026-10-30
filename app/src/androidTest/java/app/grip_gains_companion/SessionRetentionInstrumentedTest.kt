package app.grip_gains_companion

import android.Manifest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.rules.ExternalResource
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ProgressorHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionRetentionInstrumentedTest {
    @get:Rule(order = 0) val permissions = object: ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for (permission in listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant " + instrumentation.targetContext.packageName + " " + permission)).use { it.readBytes() }
            }
        }
    }
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    private fun web(): WebView = field(field(ui.activity, "webViewBridge")!!, "webView") as WebView
    @Suppress("UNCHECKED_CAST")
    private fun connection(value: ConnectionState) = ui.runOnIdle {
        val manager = field(ui.activity, "bluetoothManager")!!
        (field(manager, "_connectionState") as MutableStateFlow<ConnectionState>).value = value
    }
    private fun assertPage(original: WebView) {
        var result = ""
        val latch = CountDownLatch(1)
        ui.runOnIdle {
            assertSame(original, web())
            web().evaluateJavascript("window.currentSet", { result = it; latch.countDown() })
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("\"set-3-rep-2\"", result)
    }
    @Test fun disconnectsSettingsAndBackPreservePageAndRep() {
        println("retention: begin")
        connection(ConnectionState.Connected)
        var original: WebView? = null
        ui.waitUntil(10000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                original = field(field(ui.activity, "webViewBridge")!!, "webView") as? WebView
            }
            original != null
        }
        val page = original!!
        val loaded = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            page.stopLoading()
            page.webViewClient = object: WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { loaded.countDown() }
            }
            page.loadDataWithBaseURL("https://test.invalid", "<html><script>window.currentSet='set-3-rep-2';</script><body>Active set</body></html>", "text/html", "UTF-8", null)
        }
        println("retention: await page")
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        println("retention: start rep")
        val handler = field(ui.activity, "progressorHandler") as ProgressorHandler
        val samplesReady = CountDownLatch(1)
        ui.runOnIdle { ui.activity.lifecycleScope.launch {
            handler.enableCalibration = false; handler.canEngage = true
            handler.processSample(0.0, 0)
            handler.processSample(20.0, 10000)
            handler.processSample(22.0, 20000)
            samplesReady.countDown()
        } }
        assertTrue(samplesReady.await(5, TimeUnit.SECONDS))
        for (state in listOf(ConnectionState.Reconnecting, ConnectionState.Connecting, ConnectionState.Disconnected, ConnectionState.Scanning, ConnectionState.Error("Sensor off"), ConnectionState.Connected)) {
            println("retention: connection $state")
            connection(state)
            ui.onNodeWithText("Continue without device").assertDoesNotExist()
            assertPage(page)
            assertTrue(handler.engaged)
        }
        println("retention: settings")
        ui.onNodeWithContentDescription("Settings").performClick()
        assertTrue(ui.onAllNodesWithText("FREZ Dyno").fetchSemanticsNodes().isNotEmpty())
        assertPage(page)
        connection(ConnectionState.Disconnected)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.onNodeWithText("Continue without device").assertDoesNotExist()
        assertPage(page)
        assertTrue(handler.engaged)
    }
}
