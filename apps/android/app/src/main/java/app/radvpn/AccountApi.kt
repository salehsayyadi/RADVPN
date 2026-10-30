package app.radvpn
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object AccountApi {
 const val PRODUCTION_ENDPOINT="https://api.bestget.ir/"
 fun endpoint(value:String):String {
  val u=URL(value.trim().trimEnd('/')+"/")
  check(u.protocol=="https" && u.host.isNotBlank() && u.userInfo==null && u.query==null && u.ref==null && u.host.equals("api.bestget.ir",ignoreCase=true) && (u.port==-1||u.port==443) && u.path=="/"){"account_https_required"}
  return u.toString()
 }
 fun call(base:String,path:String,token:String?=null,payload:JSONObject?=null):JSONObject {
  val c=URL(URL(endpoint(base)),path).openConnection() as HttpsURLConnection
  c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=15000
  c.setRequestProperty("Accept","application/json")
  if(token!=null)c.setRequestProperty("Authorization","Bearer $token")
  try {
   if(payload!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}}
   val status=c.responseCode
   if(status==401||status==403)throw IllegalStateException("account_revoked")
   val stream=if(status in 200..299)c.inputStream else c.errorStream
   val bytes=ByteArrayOutputStream()
   stream?.use { input->val b=ByteArray(4096);while(true){val n=input.read(b);if(n<0)break;check(bytes.size()+n<=262144){"account_unavailable"};bytes.write(b,0,n)}}
   val result=JSONObject(bytes.toString("UTF-8"))
   if(status !in 200..299||result.has("error"))throw IllegalStateException(when(result.optString("error")){"capacity_reached"->"account_capacity_reached";"subscription_pending"->"account_subscription_pending";else->"account_unavailable"})
   return result
  }catch(e:IllegalStateException){throw e}catch(_:Exception){throw IllegalStateException("account_unavailable")}finally{c.disconnect()}
 }
 fun login(code:String):JSONObject = call(PRODUCTION_ENDPOINT,"v1/auth/login",payload=JSONObject().put("code",code).put("platform","android").put("device_name","RADVPN Android")).put("endpoint",PRODUCTION_ENDPOINT)
 fun clearManagedData(data:JSONObject) {
  val profiles=JSONArray();val previous=data.optJSONArray("profiles")?:JSONArray()
  for(i in 0 until previous.length()){val profile=previous.getJSONObject(i);if(!managed(profile))profiles.put(profile)}
  val subscriptions=JSONArray();val previousSubs=data.optJSONArray("subscriptions")?:JSONArray()
  for(i in 0 until previousSubs.length()){val sub=previousSubs.getJSONObject(i);if(!sub.optString("id").startsWith("radvpn-account-"))subscriptions.put(sub)}
  data.put("profiles",profiles).put("subscriptions",subscriptions)
  data.put("radvpn",JSONObject().put("endpoint",PRODUCTION_ENDPOINT))
 }
 fun managed(profile:JSONObject?):Boolean=profile?.optString("group")?.startsWith("radvpn-account-")==true
 fun validate(data:JSONObject):JSONObject {
  val state=data.optJSONObject("radvpn")?:throw IllegalStateException("account_revoked")
  check(state.optString("token").isNotBlank()){"account_revoked"}
  return call(PRODUCTION_ENDPOINT,"v1/account",state.getString("token"))
 }
}
