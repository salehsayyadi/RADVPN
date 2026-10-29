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
            assertEquals("2.0.3", Api.call(JSONObject().put("method", "version")).getString("app"))
            assertEquals(0, activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
            val artwork=android.util.Base64.decode(inst.targetContext.assets.list("lightning")!!.sorted().joinToString(""){name->inst.targetContext.assets.open("lightning/$name").bufferedReader().use{it.readText()}},android.util.Base64.DEFAULT)
            val sha=java.security.MessageDigest.getInstance("SHA-256").digest(artwork).joinToString(""){"%02x".format(it)}
            assertEquals("4e105c50bdc9c657793e7fcfb197bca3413d688169bf9c0172a058f5ecdf96ee",sha)
            assertEquals("1.0 KiB/s",RadVpnService.rate(1024))
            assertEquals("0 B/s",RadVpnService.rate(-1))
            checks.add("exact approved lightning asset; screenshots enabled by default; measured notification rate formatting")
            val metadata="ss://"+android.util.Base64.encodeToString("chacha20-ietf-poly1305:fixture-only".toByteArray(),android.util.Base64.NO_WRAP).replace("=","%3D")+"@0.0.0.0:1234#Quota%20remaining"
            val info=Api.call(JSONObject().put("method","import").put("text",metadata))
            assertEquals(0,info.getJSONArray("profiles").length());assertEquals("Quota remaining",info.getJSONArray("info").getString(0))
            val reality="vless://11111111-1111-4111-8111-111111111111@example.org:443?security=reality&flow=xtls-rprx-vision&encryption=none&type=tcp&headerType=none&[sni=example.org](http://sni=example.org/)&fp=chrome&pbk=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY&sid=1122334455667788#Fixture"
            assertEquals(1,Api.call(JSONObject().put("method","import").put("text",reality)).getJSONArray("profiles").length())
            checks.add("REALITY none-header, pasted Markdown SNI and percent-encoded Shadowsocks metadata through actual native core")
            verifyLargeSubscriptionThroughPackagedEngine()
            checks.add("actual packaged JNI engine imports Base64 subscription with 62 WS/TLS + 12 REALITY PQ profiles, duplicate and metadata handling; atomic Android update preserves local edits and partial results")
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
            inst.runOnMainSync {
                activity.verifyGroupsForCapture()
                activity.showForCapture("settings","en")
                fun switches(v:View):List<android.widget.Switch> = if(v is android.widget.Switch)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{switches(v.getChildAt(it))} else emptyList()
                val capture=switches(activity.window.decorView).first{it.contentDescription.toString()==en.getString("capture_allowed")}
                assertTrue(capture.isChecked);capture.isChecked=false
                assertNotEquals(0,activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
                capture.isChecked=true
                assertEquals(0,activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            checks.add("same endpoint remains in two independent subscription groups; refresh preserves favorite/custom name; actual capture privacy switch toggles FLAG_SECURE")
            val captures = JSONArray()
            for (language in listOf("en", "fa")) for (page in MainActivity.qaPages) {
                inst.runOnMainSync { activity.showForCapture(page, language) }
                if(page=="subscriptions") inst.runOnMainSync {
                    fun buttons(v:View):List<Button> = if(v is Button)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{buttons(v.getChildAt(it))} else emptyList()
                    val text=buttons(activity.window.decorView).map{it.text.toString()}
                    assertTrue("subscription Update action missing",text.contains(if(language=="fa")fa.getString("update") else en.getString("update")))
                    assertFalse("old Refresh action still visible",text.contains("Refresh"))
                }
                inst.waitForIdleSync(); SystemClock.sleep(300)
                val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
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
                val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
                val name = "dialog-$language-${bitmap.width}x${bitmap.height}.png"
                File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                captures.put(JSONObject().put("page", "dialog").put("language", language).put("file", name)); bitmap.recycle()
                inst.runOnMainSync { dialog.dismiss() }
            }
            checks.add("30 actual native page captures plus 2 native dialogs; English/Persian RTL and horizontal control bounds")
            File(out, "report-${activity.resources.displayMetrics.widthPixels}.json").writeText(JSONObject().put("passed", true).put("checks", JSONArray(checks)).put("captures", captures).put("scope", "Actual installed debug APK on Android API35 emulator; not physical-device VPN, remote server, leak or battery acceptance.").toString(2))
        } finally {
            Api.call(JSONObject().put("method", "stop"))
            inst.runOnMainSync { activity.finish() }; MainActivity.instrumentedCapture = false
        }
    }
    private fun verifyLargeSubscriptionThroughPackagedEngine() {
        val pq=android.util.Base64.encodeToString(ByteArray(1952){7},android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        val pbk=android.util.Base64.encodeToString(ByteArray(32){3},android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        val id="11111111-1111-4111-8111-111111111111"
        val ws=(0 until 62).map { "vless://$id@node.example.invalid:${443+it}?encryption=none&security=tls&sni=identity.example.invalid&fp=chrome&type=ws&host=web.example.invalid&path=%2F#SyntheticWS$it" }
        val reality=(0 until 12).map { "vless://$id@node.example.invalid:${8443+it}?encryption=none&security=reality&type=tcp&sni=identity.example.invalid&fp=chrome&flow=xtls-rprx-vision&pbk=$pbk&sid=1122334455667788&pqv=$pq&spx=%2F#SyntheticPQ$it" }
        val credential=android.util.Base64.encodeToString("chacha20-ietf-poly1305:fixture-only".toByteArray(),android.util.Base64.NO_WRAP)
        val metadata=(0 until 3).map{"ss://$credential@0.0.0.0:1234#SyntheticInfo$it"}
        val body=(metadata+ws+ws.first()+reality).joinToString("\n")
        val encoded=android.util.Base64.encodeToString(body.toByteArray(Charsets.UTF_8),android.util.Base64.NO_WRAP)
        val result=Api.call(JSONObject().put("method","import").put("text",encoded))
        val incoming=result.getJSONArray("profiles")
        assertEquals(74,incoming.length());assertEquals(0,result.getInt("rejected"));assertEquals(3,result.getJSONArray("info").length())
        var pqCount=0
        val settings=Api.call(JSONObject().put("method","defaults")).put("mode","proxy")
        for(i in 0 until incoming.length()) {
            val p=incoming.getJSONObject(i)
            val tls=p.getJSONObject("outbound").getJSONObject("streamSettings").optJSONObject("realitySettings")
            if(tls!=null) {assertEquals(pq,tls.getString("mldsa65Verify"));pqCount++;Api.call(JSONObject().put("method","validate").put("profile",p).put("settings",settings))}
        }
        assertEquals(12,pqCount)
        val data=JSONObject().put("profiles",JSONArray()).put("subscriptions",JSONArray())
        val sub=JSONObject().put("id","synthetic").put("url","https://example.invalid/subscription").put("name","Synthetic")
        SubscriptionUpdates.apply(data,sub,result,true)
        assertEquals(74,data.getJSONArray("profiles").length());assertEquals(74,sub.getInt("count"))
        data.getJSONArray("profiles").getJSONObject(0).put("name","Custom").put("userNamed",true).put("favorite",true).put("enabled",false)
        SubscriptionUpdates.apply(data,sub,result)
        val old=data.getJSONArray("profiles").getJSONObject(0)
        assertEquals("Custom",old.getString("name"));assertTrue(old.getBoolean("favorite"));assertFalse(old.getBoolean("enabled"))
        val partial=JSONObject().put("profiles",JSONArray().put(incoming.getJSONObject(0))).put("rejected",1)
        SubscriptionUpdates.apply(data,sub,partial)
        assertEquals(74,data.getJSONArray("profiles").length());assertEquals(1,sub.getInt("rejected"))
        val before=data.toString();var duplicate=false
        try {SubscriptionUpdates.apply(data,JSONObject(sub.toString()).put("id","duplicate"),partial,true)}catch(_:IllegalStateException){duplicate=true}
        assertTrue(duplicate);assertEquals(before,data.toString())
    }

}
