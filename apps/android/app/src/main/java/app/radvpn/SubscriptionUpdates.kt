package app.radvpn

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared by manual import and subscription updates; all mutations happen on the UI thread. */
object SubscriptionUpdates {
    fun merge(current: JSONArray, incoming: JSONArray, group: String = ""): JSONArray {
        val result = JSONArray(current.toString())
        val existing = (0 until result.length()).map { result.getJSONObject(it) }.associateBy { it.getString("id") }.toMutableMap()
        for (i in 0 until incoming.length()) {
            val p = JSONObject(incoming.getJSONObject(i).toString())
            val base = p.getString("id")
            require(base.isNotBlank() && p.has("outbound")) { "invalid_profile" }
            val id = if (group.isEmpty()) base else "$group:$base"
            p.put("id", id).put("group", group)
            p.put("name", DisplayNames.repair(p.optString("name")))
            val old = existing[id]
            if (old == null) { result.put(p); existing[id] = p }
            else {
                old.put("outbound", p.getJSONObject("outbound"))
                if (p.has("sourceLink")) old.put("sourceLink", p.getString("sourceLink"))
                if (!old.optBoolean("userNamed")) old.put("name", p.optString("name"))
            }
        }
        return result
    }

    fun apply(data: JSONObject, sub: JSONObject, result: JSONObject, add: Boolean = false) {
        val subscriptions = data.getJSONArray("subscriptions")
        // Reject duplicates before modifying any existing profile or subscription.
        if (add && (0 until subscriptions.length()).any { subscriptions.getJSONObject(it).optString("url") == sub.optString("url") })
            throw IllegalStateException("subscription_exists")
        val incoming = result.getJSONArray("profiles")
        val group = sub.getString("id")
        val rejected = result.optInt("rejected")
        val ids = (0 until incoming.length()).map { "$group:${incoming.getJSONObject(it).getString("id")}" }.toSet()
        val retained = JSONArray()
        val current = data.getJSONArray("profiles")
        for (i in 0 until current.length()) {
            val p = current.getJSONObject(i)
            // A partial parse must not erase previously imported, still useful profiles.
            if (rejected > 0 || incoming.length() == 0 || p.optString("group") != group || p.getString("id") in ids) retained.put(p)
        }
        val merged = merge(retained, incoming, group)
        data.put("profiles", merged)
        sub.put("last", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("count", (0 until merged.length()).count { merged.getJSONObject(it).optString("group") == group })
            .put("rejected", rejected).put("info", result.optJSONArray("info") ?: JSONArray())
        if (add) subscriptions.put(sub)
    }
}
