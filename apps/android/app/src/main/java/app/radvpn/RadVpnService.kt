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
 companion object {
  @Volatile var lastError=""
  fun rate(value:Long):String = when {
   value >= 1048576 -> String.format(java.util.Locale.US,"%.1f MiB/s",value/1048576.0)
   value >= 1024 -> String.format(java.util.Locale.US,"%.1f KiB/s",value/1024.0)
   else -> "${value.coerceAtLeast(0)} B/s"
  }
 }
 private var tun: ParcelFileDescriptor?=null
 private val worker=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 @Volatile private var closing=false
 private var previousTime=0L
 private var previousUp=0L
 private var previousDown=0L
 private var rateUp=0L
 private var rateDown=0L
 private var showSpeed=true
 private var language="en"
 private var lastState=""
 private var managed=false
 private var accountCheck=0L
 private var accountValid=0L
 private val watch=object: Runnable { override fun run() {
  if(closing)return
  worker.execute {
   if(!closing) {
    try {
     if(managed && android.os.SystemClock.elapsedRealtime()-accountCheck>30000L){
      accountCheck=android.os.SystemClock.elapsedRealtime()
      try{AccountApi.validate(Vault.load(this@RadVpnService));accountValid=accountCheck}
      catch(e:Exception){if(e.message=="account_revoked" || accountCheck-accountValid>60000L){lastError=e.message?:"account_unavailable";handler.post{shutdown()};return@execute}}
     }
     val snapshot=Api.call(JSONObject().put("method","status"))
     val state=snapshot.optString("state")
     if(snapshot.optString("error").isNotEmpty())lastError=snapshot.optString("error")
     if(state=="error"||state=="disconnected") { handler.post{shutdown()}; return@execute }
     val now=android.os.SystemClock.elapsedRealtime();val up=snapshot.optLong("up");val down=snapshot.optLong("down")
     if(previousTime>0 && now>previousTime){rateUp=(up-previousUp).coerceAtLeast(0)*1000/(now-previousTime);rateDown=(down-previousDown).coerceAtLeast(0)*1000/(now-previousTime)}
     previousTime=now;previousUp=up;previousDown=down
     try { val stored=Vault.load(this@RadVpnService);language=stored.optString("lang","en");showSpeed=stored.optBoolean("showSpeed",true) }catch(_:Exception){}
     lastState=state
     if(!closing)getSystemService(NotificationManager::class.java).notify(200,notification(state))
    } catch(_:Exception) { lastError="operation_failed";handler.post{shutdown()};return@execute }
    handler.postDelayed(this,2000)
   }
  }
 } }
 private fun notification(state:String): Notification {
  val w=Words(this,language)
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("vpn","RADVPN",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,RadVpnService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"vpn").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("RADVPN ${BuildConfig.VERSION_NAME}").setContentText(w.t(state) + if(showSpeed && state=="connected") "  ↓ ${rate(rateDown)}  ↑ ${rate(rateUp)}" else "").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,w.t("disconnect"),stop).build()).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if(intent?.action=="stop"){shutdown();return START_NOT_STICKY}
  if(intent==null){stopSelf();return START_NOT_STICKY}
  if(tun!=null||lastState=="connecting"||lastState=="connected")return START_NOT_STICKY
  val data=try{Vault.load(this)}catch(_:Exception){stopSelf();return START_NOT_STICKY}
  lastError="";language=data.optString("lang","en");showSpeed=data.optBoolean("showSpeed",true);lastState="connecting";startForeground(200,notification("connecting"))
  worker.execute {
   try {
    val profiles=data.getJSONArray("profiles");var p:JSONObject?=null
    for(i in 0 until profiles.length()){val item=profiles.getJSONObject(i);if(item.optString("id")==data.optString("selected"))p=item}
    check(p!=null){"select_profile"}
    // Every application connection requires a valid session, including manual profiles.
    managed=true
    AccountApi.validate(data);accountValid=android.os.SystemClock.elapsedRealtime();accountCheck=accountValid
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
 override fun onDestroy(){closing=true;handler.removeCallbacksAndMessages(null);worker.execute{try{Api.call(JSONObject().put("method","stop"))}catch(_:Exception){};try{tun?.close()}catch(_:Exception){};tun=null;engine.Engine.setProtector(null)};worker.shutdown();super.onDestroy()}
 override fun onBind(intent: Intent?): IBinder?=super.onBind(intent)
}
