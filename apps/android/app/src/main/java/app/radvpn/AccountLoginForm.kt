package app.radvpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.LinearLayout
import app.radvpn.ui.Design

/** Login-only content: host supplies the existing dark/orange visual controls. */
internal object AccountLoginForm {
    const val REGISTRATION_URL = "https://t.me/RadVpn7bot?start=register"
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
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REGISTRATION_URL)))
            } catch (_: Exception) { explain() }
        }
        if (hasSavedSession) button("account_restore", false, restore)
        label(text("account_code"))
        val code = EditText(activity).apply {
            // setSingleLine can replace the transformation method; enforce masking last.
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setTextColor(Design.text); setHintTextColor(Design.muted)
            background = Design.surface(activity,Design.input,Design.border,10)
            setPadding(Design.dp(activity,12),Design.dp(activity,12),Design.dp(activity,12),Design.dp(activity,12))
            minHeight = Design.dp(activity,48)
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            contentDescription = text("account_code")
        }
        parent.addView(code)
        button("account_login", true) {
            val value = code.text.toString().trim()
            code.setText("")
            login(value)
        }
    }
}
