package app.radvpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** Login-only content: host supplies the existing dark/orange visual controls. */
internal object AccountLoginForm {
    fun render(
        activity: Activity,
        parent: LinearLayout,
        text: (String) -> String,
        label: (String) -> Unit,
        button: (String, Boolean, () -> Unit) -> Unit,
        hasSavedSession: Boolean,
        restore: () -> Unit,
        login: (String) -> Unit,
        explain: () -> Unit
    ) {
        label(text("account_login_help"))
        button("account_get_telegram", true) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/RadVpn7bot?start=register")))
            } catch (_: Exception) { explain() }
        }
        if (hasSavedSession) button("account_restore", false, restore)
        label(text("account_code"))
        val code = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        parent.addView(code)
        button("account_login", true) {
            val value = code.text.toString().trim()
            code.setText("")
            login(value)
        }
    }
}
