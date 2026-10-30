package app.radvpn

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Installed debug APK only: never bypasses authentication or claims production signing. */
class AccountGateTest {
 @Test fun installedLoginGateAndSecureStorage() {
  val inst=InstrumentationRegistry.getInstrumentation()
  MainActivity.instrumentedCapture=true
  val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
   .setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"socks://127.0.0.1:19876#must-not-import-before-login")
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
  val out=File(inst.targetContext.getExternalFilesDir(null),"account-evidence").apply{mkdirs()}
  val checks=mutableListOf<String>();val captures=JSONArray()
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  fun assertLocked(language:String) {
   val words=Words(activity,language);val all=views(activity.window.decorView)
   val text=all.filterIsInstance<TextView>().map{it.text.toString()}
   assertTrue(text.contains(words.t("account_login")));assertTrue(text.contains(words.t("account_get_telegram")))
   val code=all.filterIsInstance<EditText>().single()
   assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,code.inputType and InputType.TYPE_MASK_VARIATION)
   assertTrue(code.transformationMethod is android.text.method.PasswordTransformationMethod)
   assertEquals("",code.text.toString())
   assertFalse(all.filterIsInstance<Button>().any{it.text.toString()==words.t("connect")})
   assertFalse(all.any{it is app.radvpn.ui.PowerControl})
   assertNotEquals(0,activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
   val root=activity.window.decorView
   for(v in all.filter{it is Button || it is EditText})if(v.visibility==View.VISIBLE && v.width>0){
    val xy=IntArray(2);v.getLocationOnScreen(xy)
    assertTrue("Login control overflows horizontally",xy[0]>=-2 && xy[0]+v.width<=root.width+2)
   }
  }
  try {
   inst.waitForIdleSync();inst.runOnMainSync{assertLocked("en")}
   checks.add("First screen requires login; shared intent did not expose dashboard or connection controls")
   for(language in listOf("en","fa")){
    for(page in MainActivity.qaPages){
     inst.runOnMainSync{activity.showForCapture(page,language)}
     inst.waitForIdleSync();inst.runOnMainSync{assertLocked(language)}
    }
    // Draw the real attached native view inside test instrumentation; never weaken FLAG_SECURE
    // or OS Recents protection to collect evidence. This is a view render, not an OS screenshot.
    lateinit var bitmap:Bitmap
    inst.runOnMainSync{val root=activity.window.decorView;bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))}
    val name="login-$language-${bitmap.width}x${bitmap.height}.png"
    File(out,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();captures.put(name)
   }
   checks.add("Every protected capture target remains login-gated in English/Persian; masked input and control bounds checked")
   assertFalse(CapturePolicy.allowed(false,true,false));assertFalse(CapturePolicy.allowed(true,false,false))
   assertFalse(CapturePolicy.allowed(true,true,true));assertTrue(CapturePolicy.allowed(true,true,false))
   checks.add("Account/login FLAG_SECURE asserted; default capture denied; only explicit ordinary-page opt-in permitted; evidence is in-process view rendering, not OS screenshots")
   assertEquals("https://t.me/RadVpn7bot?start=register",AccountLoginForm.REGISTRATION_URL)
   assertEquals("https://api.bestget.ir/",AccountApi.endpoint(AccountApi.PRODUCTION_ENDPOINT))
   for(value in listOf("http://api.bestget.ir/","https://foreign.invalid/","https://api.bestget.ir:8443/","https://api.bestget.ir/path","https://user@api.bestget.ir/")){
    var rejected=false;try{AccountApi.endpoint(value)}catch(_:Exception){rejected=true};assertTrue(rejected)
   }
   checks.add("Canonical API validation and exact Telegram register deep link; Telegram client opening not exercised")
   val pageId="11111111-1111-1111-1111-111111111111";val nextId="22222222-2222-2222-2222-222222222222"
   val page=JSONObject().put("current_session_id",pageId).put("sessions",JSONArray().put(JSONObject().put("id",pageId))).put("next_cursor",nextId)
   assertEquals("v1/account/sessions?after=$nextId",AccountDevicePage.path(nextId));AccountDevicePage.validate(page)
   for(bad in listOf("../owner","https://foreign.invalid","not-a-cursor")){
    var denied=false;try{AccountDevicePage.path(bad)}catch(_:Exception){denied=true};assertTrue(denied)
   }
   var repeated=false;try{AccountDevicePage.validate(page,nextId)}catch(_:Exception){repeated=true};assertTrue(repeated)
   val oversized=JSONObject(page.toString()).put("sessions",JSONArray().apply{repeat(101){put(JSONObject().put("id",pageId))}})
   var bounded=false;try{AccountDevicePage.validate(oversized)}catch(_:Exception){bounded=true};assertTrue(bounded)
   checks.add("Native device paging cursor/path, repeated-cursor and response-row bounds tested with isolated fixtures; no authenticated device UI claim")
   val source=JSONObject().put("radvpn",JSONObject().put("token","LOCAL-NONAUTHENTIC-GUARD-FIXTURE"))
   val guard=AccountRequestGuard(source);assertTrue(guard.matches(source));assertFalse(guard.matches(JSONObject(source.toString())))
   source.put("radvpn",JSONObject());assertFalse(guard.matches(source))
   checks.add("Old account request completion rejected after data/account reference replacement")
   checks.addAll(AccountSubscriptionFixtures.verify())
   val directory=File(inst.targetContext.cacheDir,"account-vault-fixture").apply{mkdirs()}
   val context=object:ContextWrapper(inst.targetContext){override fun getFilesDir():File=directory}
   try{
    val state=JSONObject().put("lang","fa").put("profiles",JSONArray().put(JSONObject().put("id","manual").put("name","Manual")).put(JSONObject().put("id","managed").put("group","radvpn-account-fixture")))
     .put("subscriptions",JSONArray().put(JSONObject().put("id","manual-sub")).put(JSONObject().put("id","radvpn-account-fixture")))
     .put("radvpn",JSONObject().put("token","LOCAL-NONAUTHENTIC-FIXTURE-ONLY").put("session_id","fixture-session"))
    Vault.save(context,state);val stored=File(directory,"vault.bin").readBytes()
    assertFalse(String(stored,Charsets.ISO_8859_1).contains("LOCAL-NONAUTHENTIC-FIXTURE-ONLY"))
    val restored=Vault.load(context);assertEquals("LOCAL-NONAUTHENTIC-FIXTURE-ONLY",restored.getJSONObject("radvpn").getString("token"))
    AccountApi.clearManagedData(restored);Vault.save(context,restored)
    val clean=Vault.load(context);assertFalse(clean.getJSONObject("radvpn").has("token"))
    assertEquals("fa",clean.getString("lang"));assertEquals(1,clean.getJSONArray("profiles").length());assertEquals("manual",clean.getJSONArray("profiles").getJSONObject(0).getString("id"))
    assertEquals("manual-sub",clean.getJSONArray("subscriptions").getJSONObject(0).getString("id"))
    val tampered=stored.clone();tampered[tampered.lastIndex]=(tampered.last().toInt() xor 1).toByte()
    var rejected=false;try{Vault.decrypt(tampered)}catch(_:Exception){rejected=true};assertTrue(rejected)
   }finally{directory.deleteRecursively()}
   checks.add("Real Android Keystore encrypted session save/load and tamper rejection; managed cleanup preserves manual data/language in an isolated fixture directory")
   File(out,"report-${activity.resources.displayMetrics.widthPixels}.json").writeText(JSONObject().put("passed",true).put("checks",JSONArray(checks)).put("captures",captures)
    .put("scope","Installed API35 debug APK with protected account screens and test-only view renders; no original-key upgrade, authenticated live account, Telegram launch, remote VPN or production API connectivity claim").toString(2))
  }finally{inst.runOnMainSync{activity.finish()};MainActivity.instrumentedCapture=false}
 }
}
