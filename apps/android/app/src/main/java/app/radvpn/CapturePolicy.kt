package app.radvpn
import android.app.Activity
import android.os.Build
import android.view.WindowManager

/** Capture is opt-in only on ordinary authenticated pages. Account screens never opt out. */
internal object CapturePolicy {
 fun allowed(requested:Boolean,authenticated:Boolean,accountScreen:Boolean):Boolean=requested && authenticated && !accountScreen
 fun apply(activity:Activity,requested:Boolean,authenticated:Boolean,accountScreen:Boolean) {
  val allow=allowed(requested,authenticated,accountScreen)
  if(allow)activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  else activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  if(Build.VERSION.SDK_INT>=33)activity.setRecentsScreenshotEnabled(allow)
 }
}
