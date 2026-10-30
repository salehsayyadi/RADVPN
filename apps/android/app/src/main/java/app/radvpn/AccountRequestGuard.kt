package app.radvpn
import org.json.JSONObject

/** Reject an old request's completion after restore/logout/replacement; no auth bypass. */
internal class AccountRequestGuard(private val data:JSONObject) {
 private val account=data.optJSONObject("radvpn")
 fun matches(current:JSONObject):Boolean=current===data && current.optJSONObject("radvpn")===account
}
