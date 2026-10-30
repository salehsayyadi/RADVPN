package app.radvpn
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object AccountApi {
 fun endpoint(value:String):String {
  val u=URL(value.trim().trimEnd('/')+"/")
  check(u.protocol=="https" && u.host.isNotBlank() && u.userInfo==null && u.query==null && u.ref==null){"account_https_required"}
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
 fun managed(profile:JSONObject?):Boolean=profile?.optString("group")?.startsWith("radvpn-account-")==true
 fun validate(data:JSONObject):JSONObject {
  val state=data.optJSONObject("radvpn")?:throw IllegalStateException("account_revoked")
  check(state.optString("token").isNotBlank()){"account_revoked"}
  return call(state.getString("endpoint"),"v1/account",state.getString("token"))
 }
}
