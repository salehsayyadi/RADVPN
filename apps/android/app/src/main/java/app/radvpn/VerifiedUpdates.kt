package app.radvpn
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object VerifiedUpdates {
 data class Candidate(val version:String,val code:Int,val testing:Boolean,val url:String,val digest:String)
 private fun version(v:String):List<Int>? {
  if(!v.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))return null
  return v.split('.').map{it.toIntOrNull()?:return null}
 }
 private fun newer(a:List<Int>,b:List<Int>):Boolean {for(i in 0..2){if(a[i]!=b[i])return a[i]>b[i]};return false}
 private fun connection(value:String):HttpsURLConnection {
  var url=URL(value)
  repeat(8){
   check(url.protocol=="https" && (url.host=="api.github.com"||url.host=="github.com"||url.host.endsWith(".githubusercontent.com"))){"update_integrity_failed"}
   val c=url.openConnection() as HttpsURLConnection;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("User-Agent","RADVPN/3.0.3")
   if(c.responseCode in listOf(301,302,303,307,308)){val next=c.getHeaderField("Location")?:throw IllegalStateException("update_integrity_failed");c.disconnect();url=URL(url,next)}else{return c}
  }
  throw IllegalStateException("update_integrity_failed")
 }
 fun find():Candidate? {
  val c=connection("https://api.github.com/repos/salehsayyadi/RADVPN/releases?per_page=20")
  val bytes=java.io.ByteArrayOutputStream()
  try{check(c.responseCode==200){"account_unavailable"};c.inputStream.use{input->val b=ByteArray(4096);while(true){val n=input.read(b);if(n<0)break;check(bytes.size()+n<=1048576){"update_integrity_failed"};bytes.write(b,0,n)}}}finally{c.disconnect()}
  val list=JSONArray(bytes.toString("UTF-8"));var best:Candidate?=null;var bestV=version(BuildConfig.VERSION_NAME)!!
  for(i in 0 until list.length()){
   val r=list.getJSONObject(i);val name=r.optString("tag_name").removePrefix("v");val v=version(name)?:continue
   if(r.optBoolean("draft")||!newer(v,bestV))continue
   val assets=r.getJSONArray("assets")
   for(j in 0 until assets.length()){
    val a=assets.getJSONObject(j);if(a.optString("name")!="RADVPN-Android.apk")continue
    val digest=a.optString("digest");check(digest.matches(Regex("sha256:[a-f0-9]{64}"))){"update_integrity_unavailable"}
    val url=a.getString("browser_download_url");check(url.startsWith("https://github.com/salehsayyadi/RADVPN/releases/download/")){"update_integrity_failed"}
    best=Candidate(name,v[0]*10000+v[1]*100+v[2],r.optBoolean("prerelease"),url,digest.removePrefix("sha256:"));bestV=v
   }
  }
  return best
 }
 fun download(context:Context,candidate:Candidate):File {
  val dir=File(context.cacheDir,"verified-updates").apply{mkdirs()};val partial=File(dir,"candidate.partial");val target=File(dir,"candidate.apk");partial.delete()
  val c=connection(candidate.url);val hash=MessageDigest.getInstance("SHA-256");var length=0L
  try {
   check(c.responseCode==200){"update_integrity_failed"}
   c.inputStream.use{input->partial.outputStream().use{out->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;length+=n;check(length<=536870912){"update_integrity_failed"};hash.update(b,0,n);out.write(b,0,n)}}}
   check(length>0 && (c.contentLengthLong<0 || c.contentLengthLong==length)){"update_integrity_failed"}
   val actual=hash.digest().joinToString(""){"%02x".format(it)}
   check(actual==candidate.digest){"update_integrity_failed"}
   verifyCompatibility(context,partial,candidate.version)
   target.delete();check(partial.renameTo(target)){"update_integrity_failed"};return target
  }finally{c.disconnect();partial.delete()}
 }
 @Suppress("DEPRECATION")
 private fun verifyCompatibility(context:Context,file:File,expectedVersion:String?=null){
  val pm=context.packageManager;val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val installed=pm.getPackageInfo(context.packageName,flags);val incoming=pm.getPackageArchiveInfo(file.path,flags)?:throw IllegalStateException("update_signature_mismatch")
  check(incoming.packageName==context.packageName && (expectedVersion==null || incoming.versionName==expectedVersion)){"update_signature_mismatch"}
  val old=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong();val next=if(Build.VERSION.SDK_INT>=28)incoming.longVersionCode else incoming.versionCode.toLong()
  check(next>old){"update_signature_mismatch"}
  val a=if(Build.VERSION.SDK_INT>=28)installed.signingInfo?.apkContentsSigners else installed.signatures
  val b=if(Build.VERSION.SDK_INT>=28)incoming.signingInfo?.apkContentsSigners else incoming.signatures
  fun hashes(s:Array<android.content.pm.Signature>?):Set<String> = s?.map{MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){b->"%02x".format(b)}}?.toSet()?:emptySet()
  check(hashes(a).isNotEmpty() && hashes(a)==hashes(b)){"update_signature_mismatch"}
 }
 fun install(context:Context,file:File){
  verifyCompatibility(context,file)
  if(!context.packageManager.canRequestPackageInstalls()){
   context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   throw IllegalStateException("update_install_permission")
  }
  val installer=context.packageManager.packageInstaller
  val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{setAppPackageName(context.packageName)}
  val id=installer.createSession(params)
  try{installer.openSession(id).use{session->file.inputStream().use{input->session.openWrite("base.apk",0,file.length()).use{out->input.copyTo(out);session.fsync(out)}}
   val intent=Intent(context,UpdateResult::class.java).setAction("app.radvpn.UPDATE_RESULT")
   val flags=PendingIntent.FLAG_UPDATE_CURRENT or (if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0)
   val callback=PendingIntent.getBroadcast(context,id,intent,flags)
   session.commit(callback.intentSender)
  }}catch(e:Exception){installer.abandonSession(id);throw e}
 }
}
class UpdateResult:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)){
   PackageInstaller.STATUS_PENDING_USER_ACTION->{@Suppress("DEPRECATION") val confirm=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT);if(confirm!=null)context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
   PackageInstaller.STATUS_SUCCESS->Toast.makeText(context,"RADVPN updated",Toast.LENGTH_LONG).show()
   else->{val lang=try{Vault.load(context).optString("lang","en")}catch(_:Exception){"en"};Toast.makeText(context,Words(context,lang).t("update_install_failed")+" ("+intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)+")",Toast.LENGTH_LONG).show()}
  }
 }
}
