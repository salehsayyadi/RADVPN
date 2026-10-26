package app.radvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.util.concurrent.Executors

class RadVpnService : VpnService() {
 companion object { @Volatile var lastError="" }
 private var tun: ParcelFileDescriptor?=null
 private val worker=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 private var closing=false
 private var language="en"
 private var lastState=""
 private val watch=object: Runnable { override fun run() {
  if(closing)return
  val state=try{val snapshot=Api.call(JSONObject().put("method","status"));if(snapshot.optString("error").isNotEmpty())lastError=snapshot.optString("error");snapshot.optString("state")}catch(_:Exception){lastError="operation_failed";"error"}
  if(state=="error"||state=="disconnected"){shutdown();return}
  val lang=try{Vault.load(this@RadVpnService).optString("lang","en")}catch(_:Exception){language}
  if(state!=lastState||lang!=language){language=lang;lastState=state;getSystemService(NotificationManager::class.java).notify(200,notification(state))}
  handler.postDelayed(this,2000)
 } }
 private fun notification(state:String): Notification {
  val w=Words(this,language)
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("vpn","RADVPN",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,RadVpnService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"vpn").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("RADVPN 2.0.0").setContentText(w.t(state)).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,w.t("disconnect"),stop).build()).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if(intent?.action=="stop"){shutdown();return START_NOT_STICKY}
  if(intent==null){stopSelf();return START_NOT_STICKY}
  if(tun!=null||lastState=="connecting"||lastState=="connected")return START_NOT_STICKY
  val data=try{Vault.load(this)}catch(_:Exception){stopSelf();return START_NOT_STICKY}
  lastError="";language=data.optString("lang","en");lastState="connecting";startForeground(200,notification("connecting"))
  worker.execute {
   try {
    val profiles=data.getJSONArray("profiles");var p:JSONObject?=null
    for(i in 0 until profiles.length()){val item=profiles.getJSONObject(i);if(item.optString("id")==data.optString("selected"))p=item}
    check(p!=null){"select_profile"}
    val settings=data.getJSONObject("settings")
    Api.call(JSONObject().put("method","validate").put("profile",p).put("settings",settings))
    engine.Engine.setProtector(object: engine.Protector { override fun protect(fd:Long):Boolean=this@RadVpnService.protect(fd.toInt()) })
    if(settings.getString("mode")=="tunnel") {
     check(prepare(this)==null){"vpn_permission_required"}
     val builder=Builder().setSession("RADVPN").setMtu(settings.getInt("mtu")).addAddress("172.29.83.1",30).addAddress("fdfe:dcba:9876::1",126).addRoute("0.0.0.0",0).addRoute("::",0)
     val dns=settings.getJSONArray("dns");for(i in 0 until dns.length())builder.addDnsServer(dns.getString(i))
     // Exclude only our own UID: subscription/bootstrap DNS and the engine cannot loop.
     builder.addDisallowedApplication(packageName)
     tun=builder.establish() ?: throw IllegalStateException("vpn_permission_required")
    }
    Api.call(JSONObject().put("method","start").put("profile",p).put("settings",settings).put("fd",tun?.fd?:-1))
    handler.post{handler.postDelayed(watch,500)}
   } catch(e:Exception){lastError=if(e is IllegalStateException)e.message?:"operation_failed"else"operation_failed";handler.post{shutdown()}}
  }
  return START_NOT_STICKY
 }
 private fun shutdown(){if(closing)return;closing=true;handler.removeCallbacks(watch);worker.execute{try{Api.call(JSONObject().put("method","stop"))}catch(_:Exception){};try{tun?.close()}catch(_:Exception){};tun=null;engine.Engine.setProtector(null);handler.post{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}}
 override fun onRevoke(){shutdown();super.onRevoke()}
 override fun onDestroy(){handler.removeCallbacksAndMessages(null);try{Api.call(JSONObject().put("method","stop"))}catch(_:Exception){};try{tun?.close()}catch(_:Exception){};engine.Engine.setProtector(null);worker.shutdown();super.onDestroy()}
 override fun onBind(intent: Intent?): IBinder?=super.onBind(intent)
}
