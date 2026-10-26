package app.radvpn

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

// Text labels come from shared JSON locales; suffixes are SI units.
@SuppressLint("SetTextI18n", "ImplicitSamInstance")
class MainActivity: Activity() {
 private lateinit var data:JSONObject
 private lateinit var words:Words
 private lateinit var body:LinearLayout
 private val worker=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 private var page="home"
 private var active="disconnected"
 private var status:TextView?=null
 private var metrics:TextView?=null
 private var control:Button?=null
 private var oldUp=0L;private var oldDown=0L;private var oldTime=System.currentTimeMillis()
 private val orange=Color.rgb(255,119,31)
 private val events=mutableListOf<String>()
 private val poll=object:Runnable{override fun run(){try{
  val s=Api.call(JSONObject().put("method","status"));val next=s.optString("state","disconnected");if(active!=next){events.add(0,t(next));if(events.size>100)events.removeAt(events.lastIndex)};active=next;status?.text=t(if(RadVpnService.lastError.isNotEmpty())RadVpnService.lastError else if(s.optString("error").isNotEmpty())s.optString("error") else active)
  control?.text=t(if(active=="connected"||active=="connecting")"disconnect" else "connect")
  val up=s.optLong("up");val down=s.optLong("down");val now=System.currentTimeMillis();val elapsed=((now-oldTime)/1000.0).coerceAtLeast(.1)
  metrics?.text="${t("up")}: $up B    ${t("down")}: $down B\n${t("speed")}: ↑ ${((up-oldUp).coerceAtLeast(0)/elapsed).toLong()} B/s   ↓ ${((down-oldDown).coerceAtLeast(0)/elapsed).toLong()} B/s\n${t("duration")}: ${s.optLong("seconds")} s\n${t("latency")}: ${if(s.isNull("latency"))"—" else s.optString("latency")} ms\n${t("local")}: ${s.optString("listener")}";oldUp=up;oldDown=down;oldTime=now
 }catch(_:Exception){};handler.postDelayed(this,1000)}}
 private fun bounded(input:java.io.InputStream,limit:Int):ByteArray { val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(out.size()<limit){val n=input.read(buffer,0,minOf(buffer.size,limit-out.size()));if(n<0)break;out.write(buffer,0,n)};return out.toByteArray() }
 private fun t(k:String)=words.t(k)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
  try{data=Vault.load(this)}catch(_:Exception){words=Words(this,"en");AlertDialog.Builder(this).setMessage(t("storage_failed")).setPositiveButton(t("close")){_,_->finish()}.setCancelable(false).show();return}
  words=Words(this,data.optString("lang","en"));render();if(intent.action==Intent.ACTION_SEND)intent.getStringExtra(Intent.EXTRA_TEXT)?.let{importText(it)}
  if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
 }
 override fun onResume(){super.onResume();if(::data.isInitialized)handler.post(poll)}
 override fun onPause(){handler.removeCallbacks(poll);super.onPause()}
 override fun onDestroy(){worker.shutdown();super.onDestroy()}
 private fun save(){try{Vault.save(this,data)}catch(_:Exception){report("storage_failed")}}
 private fun report(code:String){events.add(0,t(code));if(events.size>100)events.removeAt(events.lastIndex);AlertDialog.Builder(this).setTitle("RADVPN").setMessage(t(code)).setPositiveButton(t("close"),null).show()}
 private fun runJob(job:()->Unit){worker.execute{try{job()}catch(e:Exception){handler.post{report(if(e is IllegalStateException)e.message?:"operation_failed" else "operation_failed")}}}}
 private fun text(value:String,size:Float=16f,muted:Boolean=false):TextView=TextView(this).apply{text=value;textSize=size;setTextColor(if(muted)Color.rgb(163,169,181)else Color.WHITE);setPadding(0,dp(6),0,dp(12));body.addView(this)}
 private fun button(key:String,primary:Boolean=false,action:()->Unit):Button=Button(this).apply{text=t(key);isAllCaps=false;minHeight=dp(48);setTextColor(if(primary)Color.BLACK else Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(if(primary)orange else Color.rgb(35,39,45));setOnClickListener{try{action()}catch(_:Exception){report("operation_failed")}};body.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})}
 private fun input(key:String,value:String="",multiline:Boolean=false):EditText{text(t(key),14f,true);return EditText(this).apply{setText(value);setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);textSize=16f;minHeight=dp(if(multiline)110 else 48);setPadding(dp(8),dp(8),dp(8),dp(8));setSingleLine(!multiline);textDirection=View.TEXT_DIRECTION_LTR;backgroundTintList=android.content.res.ColorStateList.valueOf(orange);body.addView(this)}}
 private fun render(){status=null;metrics=null;control=null
  val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(13,15,18));isFillViewport=true};body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(32),dp(24),dp(32));layoutDirection=if(data.optString("lang")=="fa")View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR};scroll.addView(body);setContentView(scroll)
  text("ϟ  RADVPN",30f).setTextColor(orange);text("2.0.0 · "+t("development"),13f,true)
  val navigation=Spinner(this);val pages=listOf("home","profiles","subscriptions","settings","logs","about");navigation.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,pages.map{t(it)});navigation.setSelection(pages.indexOf(page));body.addView(navigation);navigation.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){if(page!=pages[position]){page=pages[position];render()}}}
  when(page){"home"->home();"profiles"->profiles();"subscriptions"->subscriptions();"settings"->settings();"logs"->events.forEach{text(it,14f,true)};"about"->{text("RADVPN 2.0.0",24f);text("Xray 26.10.21");text(t("development"));text(t("privacy"));text(t("backup_help"))}}
 }
 private fun selected():JSONObject?{val p=data.getJSONArray("profiles");for(i in 0 until p.length())if(p.getJSONObject(i).optString("id")==data.optString("selected"))return p.getJSONObject(i);return null}
 private fun home(){text(t("mode"),14f,true);val settings=data.getJSONObject("settings");for(mode in listOf("tunnel","proxy"))button(mode,settings.optString("mode")==mode){runJob{Api.call(JSONObject().put("method","stop"));handler.post{stopService(Intent(this,RadVpnService::class.java));settings.put("mode",mode);save();active="disconnected";render()}}};text(t(if(settings.optString("mode")=="tunnel")"tunnel_help" else "proxy_help"),14f,true);text(t("selected"),14f,true);text(selected()?.optString("name")?:t("select_profile"),24f)
  status=text(t(active),20f);status?.setTextColor(orange);control=button(if(active=="connecting"||active=="connected")"disconnect"else"connect",true){toggle()};metrics=text("—");text(t("privacy"),13f,true)
 }
 private fun toggle(){if(active=="connected"||active=="connecting"){startService(Intent(this,RadVpnService::class.java).setAction("stop"));return};if(selected()==null){report("select_profile");return};save();if(data.getJSONObject("settings").optString("mode")=="tunnel"){val consent=VpnService.prepare(this);if(consent!=null){startActivityForResult(consent,42);return}};startVpn()}
 private fun startVpn(){startForegroundService(Intent(this,RadVpnService::class.java).setAction("start"))}
 private fun merge(items:JSONArray,group:String=""){val target=data.getJSONArray("profiles");val ids=(0 until target.length()).map{target.getJSONObject(it).getString("id")}.toMutableSet();for(i in 0 until items.length()){val p=items.getJSONObject(i);if(ids.add(p.getString("id"))){p.put("group",group);target.put(p);if(data.optString("selected").isEmpty())data.put("selected",p.getString("id"))}}}
 private fun importText(value:String){runJob{val r=Api.call(JSONObject().put("method","import").put("text",value));handler.post{merge(r.getJSONArray("profiles"));save();render();Toast.makeText(this,"${t("imported")}: ${r.getJSONArray("profiles").length()} / ${r.optInt("rejected")}",Toast.LENGTH_LONG).show()}}}
 private fun prompt(key:String,value:String,callback:(String)->Unit){val edit=EditText(this).apply{setText(value);maxLines=8;textDirection=View.TEXT_DIRECTION_LTR};AlertDialog.Builder(this).setTitle(t(key)).setView(edit).setPositiveButton(t("save")){_,_->callback(edit.text.toString())}.setNegativeButton(t("cancel"),null).show()}
 private fun profiles(){val filter=input("search");val container=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(container)
  fun fill(){container.removeAllViews();val previous=body;body=container;val p=data.getJSONArray("profiles");val sorted=(0 until p.length()).map{p.getJSONObject(it)}.filter{it.optString("name").contains(filter.text.toString(),true)}.sortedWith(compareByDescending<JSONObject>{it.optBoolean("favorite")}.thenBy{it.optString("name")})
   for(item in sorted){text((if(item.optBoolean("favorite"))"★ "else"")+item.optString("name"),20f);text(t(if(item.optBoolean("enabled",true))"enabled"else"disabled"),13f,true)
    button("selected"){data.put("selected",item.getString("id"));save();page="home";render()}
    button("advanced"){prompt("advanced",item.getJSONObject("outbound").toString()){raw->runJob{val candidate=JSONObject(item.toString()).put("outbound",JSONObject(raw));Api.call(JSONObject().put("method","validate").put("profile",candidate).put("settings",data.getJSONObject("settings")));handler.post{item.put("outbound",candidate.getJSONObject("outbound"));save()}}}}
    button("rename"){prompt("name",item.optString("name")){name->if(name.isNotBlank()){item.put("name",name.take(200));save();render()}}}
    button("favorite"){item.put("favorite",!item.optBoolean("favorite"));save();render()};button("toggle"){item.put("enabled",!item.optBoolean("enabled",true));save();render()}
    button("duplicate"){val copy=JSONObject(item.toString()).put("id",java.util.UUID.randomUUID().toString());p.put(copy);save();render()}
    button("delete"){AlertDialog.Builder(this).setMessage(t("confirm_delete")).setPositiveButton(t("delete")){_,_->for(i in p.length()-1 downTo 0)if(p.getJSONObject(i)===item)p.remove(i);save();render()}.setNegativeButton(t("cancel"),null).show()}
   };if(p.length()==0)text(t("empty"));body=previous
  };fill();filter.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){fill()};override fun afterTextChanged(s:android.text.Editable?){}})
  val source=input("import","",true);button("import",true){importText(source.text.toString())};button("paste"){val c=getSystemService(ClipboardManager::class.java);if(c.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)==true)importText(c.primaryClip?.getItemAt(0)?.coerceToText(this).toString())};button("file"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),50)};button("qr"){IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt(t("qr")).setBeepEnabled(false).initiateScan()}
 }
 private fun subscriptions(){val subs=data.getJSONArray("subscriptions");for(i in 0 until subs.length()){val sub=subs.getJSONObject(i);text(android.net.Uri.parse(sub.optString("url")).host?:"—",20f);text(t("last_refresh")+": "+sub.optString("last","—"),13f,true);button("refresh"){refresh(sub)};button("delete"){subs.remove(i);save();render()}}
  val url=input("sub_url");button("add",true){val sub=JSONObject().put("url",url.text.toString()).put("id",java.util.UUID.randomUUID().toString());refresh(sub,true)}
 }
 private fun refresh(sub:JSONObject,add:Boolean=false){runJob{val r=Api.call(JSONObject().put("method","subscription").put("text",sub.getString("url")));handler.post{val items=r.getJSONArray("profiles");val ids=(0 until items.length()).map{items.getJSONObject(it).getString("id")}.toSet();val profiles=data.getJSONArray("profiles");for(i in profiles.length()-1 downTo 0){val p=profiles.getJSONObject(i);if(p.optString("group")==sub.getString("id")&&!ids.contains(p.getString("id")))profiles.remove(i)};merge(items,sub.getString("id"));sub.put("last",java.time.Instant.now().toString());if(add)data.getJSONArray("subscriptions").put(sub);save();render()}}}
 private fun settings(){for(lang in listOf("en","fa")){val b=button("language"){data.put("lang",lang);words=Words(this,lang);save();render()};b.text=if(lang=="en")"English"else"فارسی"};val s=data.getJSONObject("settings");val port=input("port",s.optString("port"));val bind=input("bind",s.optString("bind"));val mtu=input("mtu",s.optString("mtu"));val dns=input("dns",(0 until s.getJSONArray("dns").length()).joinToString(","){s.getJSONArray("dns").getString(it)});text(t("rules_help"),14f,true);val rules=input("rules",s.optJSONArray("rules")?.toString()?:"[]",true)
  button("save",true){if(active=="connected"||active=="connecting"){report("already_running");return@button};runJob{val candidate=JSONObject(s.toString()).put("port",port.text.toString().toInt()).put("mtu",mtu.text.toString().toInt()).put("bind",bind.text.toString().trim()).put("dns",JSONArray(dns.text.toString().split(',').map{it.trim()})).put("rules",JSONArray(rules.text.toString()));Api.call(JSONObject().put("method","settings").put("settings",candidate));handler.post{data.put("settings",candidate);save();Toast.makeText(this,t("success"),Toast.LENGTH_SHORT).show()}}}
  text(t("backup_help"),14f,true);button("export"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"RADVPN-2.0.0.radvpn"),51)};button("restore"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream"),52)}
 }
 @Deprecated("Native Activity permission and document flow") override fun onActivityResult(requestCode:Int,resultCode:Int,result:Intent?){super.onActivityResult(requestCode,resultCode,result)
  if(requestCode==42){if(resultCode==RESULT_OK)startVpn()else report("permission_denied");return}
  if(resultCode==RESULT_OK&&requestCode in 50..52){val uri=result?.data?:return;runJob{when(requestCode){50->{val bytes=contentResolver.openInputStream(uri)!!.use{bounded(it,2*1024*1024+1)};if(bytes.size>2*1024*1024)throw IllegalStateException("import_too_large");handler.post{importText(String(bytes,Charsets.UTF_8))}};51->{contentResolver.openOutputStream(uri)!!.use{it.write(Vault.encrypt(data))}};52->{val bytes=contentResolver.openInputStream(uri)!!.use{bounded(it,8*1024*1024+1)};val restored=Vault.decrypt(bytes);Api.call(JSONObject().put("method","stop"));handler.post{stopService(Intent(this,RadVpnService::class.java));data=restored;words=Words(this,data.optString("lang","en"));save();render()}}}};return}
  val qr=IntentIntegrator.parseActivityResult(requestCode,resultCode,result);qr?.contents?.let{importText(it)}
 }
 @Deprecated("Native back navigation") override fun onBackPressed(){if(page!="home"){page="home";render()}else super.onBackPressed()}
}
