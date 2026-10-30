package app.radvpn
import org.json.JSONObject
import java.util.UUID

/** Each native response remains below the existing transport bound; never load all pages at once. */
internal object AccountDevicePage {
 private fun valid(value:String):Boolean=try{UUID.fromString(value).toString()==value}catch(_:Exception){false}
 fun path(after:String?=null):String {
  check(after==null||valid(after)){"account_unavailable"}
  return "v1/account/sessions"+(if(after==null)"" else "?after=$after")
 }
 fun validate(result:JSONObject,after:String?=null):JSONObject {
  val rows=result.getJSONArray("sessions");check(rows.length()<=100 && valid(result.getString("current_session_id"))){"account_unavailable"}
  for(i in 0 until rows.length())check(valid(rows.getJSONObject(i).getString("id"))){"account_unavailable"}
  val next=if(result.isNull("next_cursor"))null else result.optString("next_cursor").takeIf{it.isNotBlank()}
  check(next==null||(valid(next)&&rows.length()>0&&(after==null||next>after))){"account_unavailable"}
  return result
 }
 fun fetch(token:String,after:String?=null):JSONObject=validate(AccountApi.call(AccountApi.PRODUCTION_ENDPOINT,path(after),token),after)
}
