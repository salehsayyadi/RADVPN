package app.radvpn

import org.json.JSONArray
import org.json.JSONObject

/** Fetch only after authenticated login. Never creates an account or connects the VPN. */
internal class AccountSubscriptionSync(private val fetch: (JSONObject) -> JSONObject = { Api.call(it) }) {
    private var busy = false

    fun start(data: JSONObject, force: Boolean, work: (() -> Unit) -> Unit,
              post: (() -> Unit) -> Unit, valid: (AccountRequestGuard) -> Boolean,
              changed: () -> Unit, report: (String) -> Unit) {
        if (busy) return
        val sub = prepare(data, force) ?: return
        val guard = AccountRequestGuard(data)
        val token = data.getJSONObject("radvpn").optString("token")
        fun current() = valid(guard) && data.optJSONObject("radvpn")?.optString("token") == token
        busy = true
        work {
            try {
                val result = fetch(JSONObject().put("method", "subscription").put("text", sub.getString("url")))
                post {
                    busy = false
                    if (current() && currentUrl(data) == sub.getString("url")) {
                        try { apply(data, sub, result); changed() }
                        catch (_: Exception) { if (force) report("account_subscription_pending") }
                    }
                }
            } catch (_: Exception) {
                post { busy = false; if (force && current()) report("account_unavailable") }
                // Keep the valid session; the next authenticated heartbeat retries automatically.
            }
        }
    }

    companion object {
        private fun currentUrl(data: JSONObject): String = data.optJSONObject("radvpn")
            ?.optJSONObject("account")?.optJSONObject("subscription")?.optString("url") ?: ""

        fun prepare(data: JSONObject, force: Boolean = false): JSONObject? {
            val account = data.optJSONObject("radvpn")?.optJSONObject("account") ?: return null
            val url = currentUrl(data)
            val accountId = account.optString("account_id")
            if (url.isBlank() || accountId.isBlank()) return null
            val id = "radvpn-account-$accountId"
            val subs = data.getJSONArray("subscriptions")
            val old = (0 until subs.length()).map { subs.getJSONObject(it) }.firstOrNull { it.optString("id") == id }
            val profiles = data.getJSONArray("profiles")
            val loaded = (0 until profiles.length()).any {
                val p = profiles.getJSONObject(it)
                p.optString("group") == id && p.optBoolean("enabled", true) && p.has("outbound")
            }
            if (!force && old?.optString("url") == url && old.optInt("rejected") == 0 && loaded) return null
            return (old?.let { JSONObject(it.toString()) } ?: JSONObject().put("id", id).put("name", "RADVPN account"))
                .put("url", url)
        }

        /** Stage every edit before committing: failed/empty parses cannot leave an empty subscription. */
        fun apply(data: JSONObject, sub: JSONObject, result: JSONObject) {
            check(result.getJSONArray("profiles").length() > 0) { "account_subscription_pending" }
            val staged = JSONObject(data.toString())
            val id = sub.getString("id")
            val subscriptions = staged.getJSONArray("subscriptions")
            // Adopt a previously manually imported copy of this exact URL instead of duplicating it.
            val aliases = (0 until subscriptions.length()).map { subscriptions.getJSONObject(it) }
                .filter { it.optString("url") == sub.getString("url") && it.optString("id") != id }
                .map { it.getString("id") }.toSet()
            val profiles = staged.getJSONArray("profiles")
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i); val group = p.optString("group")
                if (group in aliases) {
                    val oldId = p.getString("id")
                    val next = id + ":" + oldId.removePrefix("$group:")
                    p.put("group", id).put("id", next)
                    if (staged.optString("selected") == oldId) staged.put("selected", next)
                }
            }
            val unique = JSONArray(); val seen = mutableSetOf<String>()
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i)
                if (p.optString("group") != id || seen.add(p.getString("id"))) unique.put(p)
            }
            staged.put("profiles", unique)
            val nextSub = JSONObject(sub.toString())
            SubscriptionUpdates.apply(staged, nextSub, result)
            val nextSubs = JSONArray()
            for (i in 0 until subscriptions.length()) {
                val s = subscriptions.getJSONObject(i)
                if (s.optString("id") != id && s.optString("id") !in aliases) nextSubs.put(s)
            }
            nextSubs.put(nextSub)
            val merged = staged.getJSONArray("profiles")
            val selected = staged.optString("selected")
            if (!(0 until merged.length()).any { merged.getJSONObject(it).let { p -> p.optString("id") == selected && p.optBoolean("enabled", true) } }) {
                val first = (0 until merged.length()).map { merged.getJSONObject(it) }
                    .firstOrNull { it.optString("group") == id && it.optBoolean("enabled", true) }
                staged.put("selected", first?.optString("id") ?: "")
            }
            data.put("profiles", merged).put("subscriptions", nextSubs).put("selected", staged.optString("selected"))
        }
    }
}
