package app.radvpn

import org.json.JSONArray
import org.json.JSONObject

/** No live credentials or auth bypass: shared by installed instrumentation and JVM regression. */
internal object AccountSubscriptionFixtures {
    fun verify(): List<String> {
        fun state(): JSONObject = JSONObject().put("selected", "").put("profiles", JSONArray()).put("subscriptions", JSONArray())
            .put("radvpn", JSONObject().put("token", "LOCAL-FIXTURE-NOT-A-TOKEN").put("account", JSONObject()
                .put("account_id", "fixture").put("subscription", JSONObject().put("url", "https://subscription.invalid/fixture"))))
        fun result(): JSONObject = JSONObject().put("profiles", JSONArray().put(JSONObject().put("id", "one")
            .put("name", "Fixture").put("enabled", true).put("outbound", JSONObject().put("protocol", "socks"))))
        val checks = mutableListOf<String>()
        val data = state()
        val plan = AccountSubscriptionSync.prepare(data)!!
        check(data.getJSONArray("subscriptions").length() == 0)
        AccountSubscriptionSync.apply(data, plan, result())
        check(data.getJSONArray("subscriptions").length() == 1 && data.getJSONArray("profiles").length() == 1)
        check(data.getString("selected") == "radvpn-account-fixture:one")
        check(AccountSubscriptionSync.prepare(data) == null)
        repeat(3) { AccountSubscriptionSync.apply(data, AccountSubscriptionSync.prepare(data, true)!!, result()) }
        check(data.getJSONArray("subscriptions").length() == 1 && data.getJSONArray("profiles").length() == 1)
        checks.add("automatic first import selects profile; restore/retry idempotency")
        val pending = state(); pending.getJSONObject("radvpn").getJSONObject("account").getJSONObject("subscription").remove("url")
        check(AccountSubscriptionSync.prepare(pending) == null)
        pending.getJSONObject("radvpn").getJSONObject("account").getJSONObject("subscription").put("url", "https://subscription.invalid/ready")
        check(AccountSubscriptionSync.prepare(pending) != null)
        val before = data.toString()
        try { AccountSubscriptionSync.apply(data, plan, JSONObject().put("profiles", JSONArray())); error("empty parse accepted") }
        catch (e: IllegalStateException) { check(e.message == "account_subscription_pending") }
        check(data.toString() == before)
        checks.add("pending provisioning retries; empty fetch leaves stored state intact")
        data.getJSONObject("radvpn").getJSONObject("account").getJSONObject("subscription").put("url", "https://subscription.invalid/rotated")
        check(AccountSubscriptionSync.prepare(data)!!.getString("url").endsWith("rotated"))
        val adoption = state()
        adoption.getJSONArray("subscriptions").put(JSONObject().put("id", "manual-sub").put("url", plan.getString("url")))
        adoption.getJSONArray("profiles").put(JSONObject().put("id", "manual-sub:one").put("group", "manual-sub").put("userNamed", true).put("name", "My name").put("outbound", JSONObject()))
            .put(JSONObject().put("id", "unrelated").put("outbound", JSONObject()))
        adoption.put("selected", "manual-sub:one")
        AccountSubscriptionSync.apply(adoption, AccountSubscriptionSync.prepare(adoption)!!, result())
        check(adoption.getJSONArray("subscriptions").length() == 1 && adoption.getJSONArray("profiles").length() == 2)
        check(adoption.getJSONArray("profiles").getJSONObject(0).getString("name") == "My name")
        check(adoption.getString("selected") == "radvpn-account-fixture:one")
        adoption.getJSONArray("subscriptions").put(JSONObject().put("id", "second-copy").put("url", plan.getString("url")))
        adoption.getJSONArray("profiles").put(JSONObject().put("id", "second-copy:one").put("group", "second-copy").put("outbound", JSONObject()))
        AccountSubscriptionSync.apply(adoption, AccountSubscriptionSync.prepare(adoption, true)!!, result())
        check(adoption.getJSONArray("subscriptions").length() == 1 && adoption.getJSONArray("profiles").length() == 2)
        checks.add("rotated URL; adopt exact-URL manual import without duplication; retain unrelated profiles")
        val queue = mutableListOf<() -> Unit>()
        var valid = true; var saves = 0
        val delayed = state()
        val sync = AccountSubscriptionSync { result() }
        fun start() = sync.start(delayed, false, { queue.add(it) }, { it() }, { valid && it.matches(delayed) }, { saves++ }, { error(it) })
        start(); start(); check(queue.size == 1)
        valid = false; delayed.put("radvpn", JSONObject())
        queue.removeAt(0)()
        check(saves == 0 && delayed.getJSONArray("subscriptions").length() == 0)
        val retry = state(); var requests = 0
        val flaky = AccountSubscriptionSync { requests++; if (requests == 1) throw IllegalStateException("offline") else result() }
        repeat(2) { flaky.start(retry, false, { it() }, { it() }, { it.matches(retry) }, { saves++ }, { error(it) }) }
        check(requests == 2 && retry.getJSONArray("subscriptions").length() == 1)
        checks.add("concurrent imports single-flight; late completion after logout ignored; network failure auto-retry")
        return checks
    }
}
