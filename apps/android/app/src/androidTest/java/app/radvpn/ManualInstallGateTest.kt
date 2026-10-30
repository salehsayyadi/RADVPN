package app.radvpn

import android.content.Intent
import android.test.InstrumentationTestCase
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView

/** Test the distributed sandbox APK itself; no auth/capture overrides, no server calls. */
@Suppress("DEPRECATION")
class ManualInstallGateTest : InstrumentationTestCase() {
 fun testInstalledLoginFieldRemainsMaskedAndProtected() {
  val inst=instrumentation
  MainActivity.instrumentedCapture=false
  val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  try {
   inst.waitForIdleSync()
   inst.runOnMainSync {
    val all=views(activity.window.decorView)
    val text=all.filterIsInstance<TextView>().map{it.text.toString()}
    assertTrue(text.contains("Sign in"))
    assertTrue(text.contains("Get / Create Account in Telegram"))
    val code=all.filterIsInstance<EditText>().single()
    assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,code.inputType and InputType.TYPE_MASK_VARIATION)
    assertTrue(code.transformationMethod is PasswordTransformationMethod)
    assertEquals("",code.text.toString())
    val fixture="ManualMaskFixture42"
    code.setText(fixture)
    val transformed=code.transformationMethod.getTransformation(code.text,code).toString()
    assertFalse(fixture==transformed)
    assertEquals(fixture.length,transformed.length)
    code.setText("")
    assertTrue((activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)!=0)
    assertFalse(all.any{it is app.radvpn.ui.PowerControl})
   }
  } finally {inst.runOnMainSync{activity.finish()};inst.waitForIdleSync()}
 }
}
