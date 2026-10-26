package app.radvpn

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket

/** Actual installed APK + native Go boundary on an Android emulator. No remote VPN claim. */
class NativeUiTest {
    @Test fun nativeBoundariesAndScreens() {
        val inst = InstrumentationRegistry.getInstrumentation()
        MainActivity.instrumentedCapture = true
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val checks = mutableListOf<String>()
        val out = File(inst.targetContext.getExternalFilesDir(null), "native-evidence").apply { mkdirs() }
        try {
            assertEquals("2.0.0", Api.call(JSONObject().put("method", "version")).getString("app"))
            val settings = Api.call(JSONObject().put("method", "defaults"))
            assertEquals("tunnel", settings.getString("mode")); assertEquals(10808, settings.getInt("port"))
            checks.add("actual packaged native core/version and Tunnel/10808 defaults")
            var rejected = false
            try { Api.call(JSONObject().put("method", "import").put("text", "not-a-config")) } catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            val profiles = Api.call(JSONObject().put("method", "import").put("text", "socks://127.0.0.1:19876#LOCAL-TEST-ONLY")).getJSONArray("profiles")
            assertEquals(1, profiles.length())
            checks.add("invalid input rejected; valid local-only fixture parsed, never displayed as a real server")
            rejected = false
            try { Api.call(JSONObject().put("method", "settings").put("settings", JSONObject(settings.toString()).put("dns", JSONArray().put("invalid")))) } catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected); checks.add("invalid DNS rejected by actual core boundary")
            Api.call(JSONObject().put("method", "stop"))
            ServerSocket(0).use { occupied ->
                val port = occupied.localPort
                if (port in 1024..65534) {
                    rejected = false
                    try { Api.call(JSONObject().put("method", "start").put("profile", profiles.getJSONObject(0)).put("settings", JSONObject(settings.toString()).put("mode", "proxy").put("port", port)).put("fd", -1)) } catch (_: IllegalStateException) { rejected = true }
                    assertTrue(rejected); assertNotEquals("connected", Api.call(JSONObject().put("method", "status")).getString("state"))
                }
            }
            Api.call(JSONObject().put("method", "stop")); checks.add("real occupied loopback port refuses startup; native stop is clean")
            val vault = JSONObject().put("lang", "fa").put("settings", settings)
            val encrypted = Vault.encrypt(vault)
            assertEquals("fa", Vault.decrypt(encrypted).getString("lang"))
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
            rejected = false; try { Vault.decrypt(encrypted) } catch (_: Exception) { rejected = true }; assertTrue(rejected)
            checks.add("actual Android Keystore AES-GCM roundtrip and tamper rejection; no user vault overwritten")
            val en = JSONObject(inst.targetContext.assets.open("en.json").bufferedReader().use { it.readText() })
            val fa = JSONObject(inst.targetContext.assets.open("fa.json").bufferedReader().use { it.readText() })
            assertEquals(en.keys().asSequence().toSet(), fa.keys().asSequence().toSet()); checks.add("English/Persian locale key parity")
            val captures = JSONArray()
            for (language in listOf("en", "fa")) for (page in MainActivity.qaPages) {
                inst.runOnMainSync { activity.showForCapture(page, language) }
                inst.waitForIdleSync(); SystemClock.sleep(300)
                val bitmap = inst.uiAutomation.takeScreenshot(); assertNotNull(bitmap)
                val name = "$page-$language-${bitmap.width}x${bitmap.height}.png"
                File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                inst.runOnMainSync {
                    val root = activity.window.decorView
                    fun audit(v: View) {
                        if ((v is Button || v is EditText) && v.visibility == View.VISIBLE && v.width > 0) {
                            val xy = IntArray(2); v.getLocationOnScreen(xy)
                            assertTrue("$page/$language horizontal control overflow", xy[0] >= -2 && xy[0] + v.width <= root.width + 2)
                        }
                        if (v is ViewGroup) for (i in 0 until v.childCount) audit(v.getChildAt(i))
                    }
                    audit(root)
                }
                captures.put(JSONObject().put("page", page).put("language", language).put("file", name))
                bitmap.recycle()
            }
            for (language in listOf("en", "fa")) {
                lateinit var dialog: android.app.AlertDialog
                inst.runOnMainSync { activity.showForCapture("connection", language); dialog = activity.showErrorForCapture() }
                inst.waitForIdleSync(); SystemClock.sleep(250)
                val bitmap = inst.uiAutomation.takeScreenshot()
                val name = "dialog-$language-${bitmap.width}x${bitmap.height}.png"
                File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                captures.put(JSONObject().put("page", "dialog").put("language", language).put("file", name)); bitmap.recycle()
                inst.runOnMainSync { dialog.dismiss() }
            }
            checks.add("22 actual native page captures plus 2 native dialogs; English/Persian RTL and horizontal control bounds")
            File(out, "report-${activity.resources.displayMetrics.widthPixels}.json").writeText(JSONObject().put("passed", true).put("checks", JSONArray(checks)).put("captures", captures).put("scope", "Actual installed debug APK on Android API35 emulator; not physical-device VPN, remote server, leak or battery acceptance.").toString(2))
        } finally {
            Api.call(JSONObject().put("method", "stop"))
            inst.runOnMainSync { activity.finish() }; MainActivity.instrumentedCapture = false
        }
    }
}
