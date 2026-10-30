package app.radvpn

import java.util.Locale
import org.json.JSONObject

/** Account presentation and managed subscription selection; no UI/session ownership. */
internal object AccountDetails {
    fun lines(account: JSONObject, text: (String) -> String): List<String> {
        val sub = account.optJSONObject("subscription") ?: JSONObject()
        fun amount(key: String) = if (sub.isNull(key)) "—" else String.format(Locale.US, "%.2f GiB", sub.optLong(key) / 1073741824.0)
        return listOf(
            "account_id" to account.optString("telegram_id"),
            "account_status" to account.optString("status"),
            "account_subscription" to sub.optString("status"),
            "account_total" to amount("total_bytes"),
            "account_used" to amount("used_bytes"),
            "account_remaining" to amount("remaining_bytes"),
            "account_capacity" to account.optString("device_capacity"),
            "account_active" to account.optString("active_sessions"),
            "account_slots" to account.optString("available_slots")
        ).map { (key, value) -> text(key) + ": " + value }
    }

    fun subscription(data: JSONObject, account: JSONObject): Pair<JSONObject, Boolean>? {
        val url = account.optJSONObject("subscription")?.optString("url") ?: ""
        if (url.isBlank()) return null
        val id = "radvpn-account-" + account.getString("account_id")
        val subscriptions = data.getJSONArray("subscriptions")
        for (i in 0 until subscriptions.length()) {
            val current = subscriptions.getJSONObject(i)
            if (current.optString("id") == id) return current to false
        }
        return JSONObject().put("id", id).put("name", "RADVPN account").put("url", url) to true
    }

    fun purchaseLink(account: JSONObject): String? {
        val url = account.optString("purchase_url")
        return if (url.matches(Regex("https://t\\.me/[A-Za-z0-9_]+"))) url + "?start=radvpn" else null
    }

    fun logout(state: JSONObject): JSONObject {
        return try {
            AccountApi.call(AccountApi.PRODUCTION_ENDPOINT, "v1/auth/logout", state.getString("token"), JSONObject())
        } catch (e: IllegalStateException) {
            // Keep a live token on transport failure rather than leak a paid slot.
            if (e.message != "account_revoked") throw e
            JSONObject()
        }
    }
}
