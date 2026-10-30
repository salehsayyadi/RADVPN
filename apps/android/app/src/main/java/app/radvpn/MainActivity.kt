package app.radvpn

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.VpnService
import android.os.*
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import app.radvpn.ui.*
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Native Android UI. VpnService / gomobile engine remain the real networking boundary. */
@SuppressLint("SetTextI18n", "ImplicitSamInstance")
class MainActivity : Activity() {
    companion object {
        // Set only by in-process debug instrumentation; no exported intent can enable captures.
        @JvmField var instrumentedCapture = false
        val qaPages = listOf("home", "servers", "configs", "subscriptions", "profiles", "traffic", "settings", "connection", "advanced", "logs", "about", "favorites", "home-populated", "servers-populated", "server-menu")
    }
    private lateinit var data: JSONObject
    private lateinit var words: Words
    private lateinit var body: LinearLayout
    private lateinit var root: LinearLayout
    private var serverPopup: PopupMenu? = null
    private var serverAnchor: View? = null
    private var groupFilter = ""
    private var completedTests = 0
    private val worker = Executors.newFixedThreadPool(2)
    private val probes = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())
    private val polling = AtomicBoolean(false)
    private var page = "account"
    @Volatile private var accountReady=false
    @Volatile private var accountBusy=false
    private var accountChecked=0L
    private var accountVerifiedAt=0L
    private var active = "disconnected"
    private var pending = false
    private var resumed = false
    private var screenStatus: TextView? = null
    private var connectButton: Button? = null
    private var power: PowerControl? = null
    private var downRate: TextView? = null
    private var upRate: TextView? = null
    private var ping: TextView? = null
    private var session: TextView? = null
    private var plot: TrafficPlot? = null
    private var snapshot = JSONObject()
    private var oldUp = 0L
    private var oldDown = 0L
    private var oldTime = 0L
    private var rateUp = 0L
    private var rateDown = 0L
    private val samples = mutableListOf<Pair<Long, Long>>()
    private val events = mutableListOf<Pair<String, String>>()
    private val measured = mutableMapOf<String, Long?>()
    private var filter = ""
    private var favoritesOnly = false
    private var latencySort = false
    private var probeCancel = AtomicBoolean(false)
    private var probing = false
    private var destroyed = false
    private val qa get() = BuildConfig.DEBUG && instrumentedCapture
    private val isIdle get() = !pending && !probing && active in listOf("disconnected", "error")
    private fun dp(n: Int) = Design.dp(this, n)
    private fun t(key: String) = words.t(key)
    private fun settings() = data.getJSONObject("settings")
    private fun profiles() = data.getJSONArray("profiles")
    private fun items() = (0 until profiles().length()).map { profiles().getJSONObject(it) }
    private fun selected() = items().firstOrNull { it.optString("id") == data.optString("selected") && it.optBoolean("enabled", true) }
    private fun clock() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else { @Suppress("DEPRECATION") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION }
        CapturePolicy.apply(this,false,false,true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        try {
            data = if (qa) JSONObject().put("lang", "en").put("selected", "").put("profiles", JSONArray()).put("subscriptions", JSONArray()).put("settings", Api.call(JSONObject().put("method", "defaults"))) else Vault.load(this)
        } catch (_: Exception) {
            words = Words(this, "en")
            AlertDialog.Builder(this).setMessage(t("storage_failed")).setPositiveButton(t("close")) { _, _ -> finish() }.setCancelable(false).show()
            return
        }
        words = Words(this, data.optString("lang", "en"))
        migrateGroups(); applyCapturePolicy(); render()
        if (state != null) { page = state.getString("page", "home"); render() }
        if (accountReady && intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let { importText(it) }
        restoreAccount()
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("page", page); super.onSaveInstanceState(out) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (accountReady && intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let { importText(it) } }
    override fun onResume() { super.onResume(); resumed = true; if (::data.isInitialized) handler.post(poll) }
    override fun onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); probeCancel.set(true); worker.shutdownNow(); probes.shutdownNow(); super.onDestroy() }
    private fun save() { if (qa) return; try { Vault.save(this, data) } catch (_: Exception) { report("storage_failed") } }
    private fun event(key: String) { events.add(0, clock() to key); while (events.size > 100) events.removeAt(events.lastIndex) }
    private fun showDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create(); dialog.show();dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val rtl = ::data.isInitialized && data.optString("lang") == "fa"
        dialog.window?.decorView?.let { decor ->
            decor.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            fun align(v: View) {
                if (v is TextView && v !is EditText && v !is Button) { v.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR; v.textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
                if (v is ViewGroup) for (i in 0 until v.childCount) align(v.getChildAt(i))
            }
            align(decor); decor.requestLayout()
        }
        return dialog
    }
    private fun report(key: String) { if (destroyed) return; event(key); showDialog(AlertDialog.Builder(this).setTitle("RADVPN").setMessage(t(key)).setPositiveButton(t("close"), null)) }
    private fun job(action: () -> Unit) { if (destroyed) return; worker.execute { try { action() } catch (e: Exception) { handler.post { pending = false; report(if (e is IllegalStateException) e.message ?: "operation_failed" else "operation_failed"); updateMetrics() } } } }
    private fun idle(action: () -> Unit) { if (!isIdle) report("already_running") else action() }
    private fun go(target: String) { page = if(accountReady)target else "account"; filter = ""; favoritesOnly = target == "favorites"; groupFilter = ""; render() }
    private fun applyCapturePolicy() = CapturePolicy.apply(this,data.optBoolean("allowCapture",false),accountReady,page=="account")
    private fun migrateGroups() {
        for (p in items()) { val group = p.optString("group"); val id = p.optString("id"); if (group.isNotEmpty() && !id.startsWith("$group:")) { val next = "$group:$id"; p.put("id", next); if (data.optString("selected") == id) data.put("selected",next) } }
    }

    // Polling never calls the native core on the Android main thread.
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed || destroyed) return
            if (polling.compareAndSet(false, true)) job {
                try {
                    accountHeartbeat()
                    val result = Api.call(JSONObject().put("method", "status"))
                    handler.post {
                        if (destroyed) return@post
                        val next = result.optString("state", "disconnected")
                        if (next != active) { active = next; event(next) }
                        if (next in listOf("connected", "error") || RadVpnService.lastError.isNotEmpty()) pending = false
                        snapshot = result
                        val now = SystemClock.elapsedRealtime()
                        val up = result.optLong("up"); val down = result.optLong("down")
                        if (oldTime > 0 && now > oldTime) {
                            rateUp = ((up - oldUp).coerceAtLeast(0) * 1000 / (now - oldTime))
                            rateDown = ((down - oldDown).coerceAtLeast(0) * 1000 / (now - oldTime))
                            if (next == "connected") { samples.add(rateUp to rateDown); while (samples.size > 90) samples.removeAt(0) }
                        }
                        oldTime = now; oldUp = up; oldDown = down; updateMetrics()
                    }
                } finally { polling.set(false) }
            }
            handler.postDelayed(this, 1500)
        }
    }
    private fun bytes(value: Long): String = when {
        value >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GiB", value / (1024.0 * 1024 * 1024))
        value >= 1024 * 1024 -> String.format(Locale.US, "%.1f MiB", value / (1024.0 * 1024))
        value >= 1024 -> String.format(Locale.US, "%.1f KiB", value / 1024.0)
        else -> "$value B"
    }
    private fun updateMetrics() {
        val shown = if (pending) "starting" else active
        val error = RadVpnService.lastError.ifEmpty { snapshot.optString("error") }
        screenStatus?.text = t(if (error.isNotEmpty()) error else shown)
        screenStatus?.setTextColor(if (error.isNotEmpty()) Design.red else if (active == "connected") Design.green else Design.muted)
        power?.state = if (error.isNotEmpty()) "error" else shown
        power?.contentDescription = t(if (isIdle) "connect" else "disconnect")
        connectButton?.let{val icon=Design.icon(this,"power",Design.background).apply{setBounds(0,0,dp(18),dp(18))};it.text=SpannableString("  ${t(if(isIdle)"connect" else "disconnect")}").apply{setSpan(android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BOTTOM),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}}
        downRate?.text = bytes(rateDown) + "/s"; upRate?.text = bytes(rateUp) + "/s"
        ping?.text = if (snapshot.isNull("latency") || !snapshot.has("latency")) "—" else snapshot.optLong("latency").toString() + " ms"
        session?.text = "${t("down")}: ${bytes(snapshot.optLong("down"))}   ·   ${t("up")}: ${bytes(snapshot.optLong("up"))}\n${t("duration")}: ${snapshot.optLong("seconds")} s"
        plot?.invalidate()
    }

    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 14f, color: Int = Design.text, bold: Boolean = false): TextView {
        val v = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(3), 0, dp(5)); if (bold) setTypeface(typeface, Typeface.BOLD); if (Build.VERSION.SDK_INT >= 29) breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY }
        body.addView(v, LinearLayout.LayoutParams(-1, -2)); return v
    }
    private fun help(key: String) = label(t(key), 12f, Design.muted)
    private fun button(key: String, primary: Boolean = false, action: () -> Unit): Button {
        val v = Button(this).apply { text = t(key); textSize = 14f; isAllCaps = false; setTextColor(if (primary) Design.background else Design.muted); minHeight = dp(48); minimumHeight = dp(48); minimumWidth = 0; setPadding(dp(12), dp(8), dp(12), dp(8)); background = Design.button(this@MainActivity, primary); if (key in listOf("connect", "import", "add_config", "paste", "file", "qr", "add_subscription", "add_server", "tunnel", "proxy")) {
                val symbol = when(key) { "connect" -> "power"; "paste" -> "copy"; "file" -> "configs"; "qr" -> "qr"; "tunnel" -> "tunnel"; "proxy" -> "proxy"; else -> "plus" }
                val icon=Design.icon(this@MainActivity,symbol,if(primary)Design.background else Design.muted).apply{setBounds(0,0,dp(18),dp(18))}
                text=SpannableString("  ${t(key)}").apply{setSpan(android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BOTTOM),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
            }; setOnClickListener { try { action() } catch (_: Exception) { report("operation_failed") } } }
        body.addView(v, LinearLayout.LayoutParams(if (body.orientation == LinearLayout.HORIZONTAL) 0 else -1, -2, if (body.orientation == LinearLayout.HORIZONTAL) 1f else 0f).apply { setMargins(dp(3), dp(5), dp(3), dp(5)) }); return v
    }
    private fun row(action: () -> Unit): LinearLayout {
        val old = body; val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        old.addView(r, LinearLayout.LayoutParams(-1, -2)); body = r; action(); body = old; return r
    }
    private fun card(action: () -> Unit): LinearLayout {
        val old = body; val p = vertical().apply { setPadding(dp(16), dp(14), dp(16), dp(14)); background = Design.surface(this@MainActivity) }
        old.addView(p, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }); body = p; action(); body = old; return p
    }
    private fun field(key: String, value: String = "", multiline: Boolean = false, numeric: Boolean = false, showLabel: Boolean = true): EditText {
        if(showLabel)help(key)
        val v = EditText(this).apply {
            setText(value); textSize = 14f; setTextColor(Design.text); setHintTextColor(Design.subtle); minHeight = dp(if (multiline) 112 else 48); background = Design.surface(this@MainActivity, Design.input, radius = 9); setPadding(dp(12), dp(10), dp(12), dp(10)); setSingleLine(!multiline); gravity = Gravity.TOP or Gravity.START; textDirection = View.TEXT_DIRECTION_LTR; layoutDirection = View.LAYOUT_DIRECTION_LTR
            inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_TEXT_VARIATION_URI
            if (key in listOf("search", "name", "subscription_name")) { textDirection = View.TEXT_DIRECTION_FIRST_STRONG; layoutDirection = root.layoutDirection; inputType = android.text.InputType.TYPE_CLASS_TEXT }
            if (multiline) typeface = Typeface.MONOSPACE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }); return v
    }
    private fun iconAction(parent: LinearLayout, icon: String, key: String, action: (View) -> Unit) {
        val holder = FrameLayout(this).apply { contentDescription = t(key); isClickable = true; isFocusable = true; background = Design.button(this@MainActivity); setOnClickListener { action(it) } }
        holder.addView(Symbol(this, icon, Design.orange), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        parent.addView(holder, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
    }
    private fun title(key: String) = label(t(key), 16f, Design.text, true)
    private fun empty(key: String = "empty", action: Boolean = true) {
        val symbol = Symbol(this, "servers", Design.subtle)
        body.addView(symbol, LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24); bottomMargin = dp(12) })
        label(t(key), 16f, Design.muted, true).gravity = Gravity.CENTER
        label(t("import_hint"), 13f, Design.subtle).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(12)) }
        if (action) button("add_config", true) { go("configs") }
    }

    private fun render() {
        if (destroyed) return
        if(!accountReady)page="account"
        applyCapturePolicy()
        serverPopup?.dismiss(); serverPopup = null; serverAnchor = null
        screenStatus = null; downRate = null; upRate = null; ping = null; session = null; plot = null; power = null; connectButton = null
        root = vertical().apply { setBackgroundColor(android.graphics.Color.TRANSPARENT); layoutDirection = if (data.optString("lang") == "fa") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val s = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); v.setPadding(s.left, s.top, s.right, maxOf(s.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom)) }
            else { @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom) }
            insets
        }
        val backdrop = LightningBackdrop(this); backdrop.addView(root, FrameLayout.LayoutParams(-1,-1)); setContentView(backdrop); root.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(64)))
        if (accountReady && page !in listOf("home", "servers", "configs", "settings")) iconAction(header, "back", "home") { go(if (page == "favorites") "servers" else "home") }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.START; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        if (page == "home" || !accountReady) {
            brand.addView(Symbol(this, "brand", Design.orange), LinearLayout.LayoutParams(dp(27), dp(30)))
            val text = SpannableString("RADVPN").apply { setSpan(ForegroundColorSpan(Design.orange), 0, 3, 0) }
            brand.addView(TextView(this).apply { this.text = text; textSize = 22f; setTextColor(Design.text); setTypeface(typeface, Typeface.BOLD); setPadding(dp(7), 0, 0, 0) })
        } else brand.addView(TextView(this).apply { text = t(page); textSize = 19f; setTextColor(Design.text); setTypeface(typeface, Typeface.BOLD) })
        header.addView(brand, LinearLayout.LayoutParams(0, -1, 1f))
        if (page == "servers") { iconAction(header, "more", "more") { anchor -> showServerMenu(anchor) }; serverAnchor = header.getChildAt(header.childCount-1) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body = vertical().apply { setPadding(dp(16), dp(6), dp(16), dp(14)) }; scroll.addView(body)
        when (page) {
            "account" -> accountPage()
            "home" -> home()
            "servers", "profiles", "favorites" -> serverPage()
            "configs" -> configs()
            "subscriptions" -> subscriptions()
            "traffic" -> traffic()
            "settings", "connection", "advanced" -> settingsPage()
            "logs" -> diagnostics()
            "about" -> about()
        }
        if(!accountReady){updateMetrics();return}
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(6)); background = Design.surface(this@MainActivity, Design.panel, radius = 0) }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(72)))
        for (target in listOf("home", "servers", "configs", "settings")) {
            val on = page == target || (target == "settings" && page in listOf("connection", "advanced")) || (target == "servers" && page in listOf("profiles", "favorites"))
            val tab = vertical().apply { gravity = Gravity.CENTER; isClickable = true; isFocusable = true; contentDescription = t(target); isSelected = on; if (on) background = Design.surface(this@MainActivity, Design.secondary, Design.border, 8); setOnClickListener { go(target) } }
            tab.addView(Symbol(this, target, if (on) Design.orange else Design.subtle), LinearLayout.LayoutParams(dp(21), dp(21)))
            tab.addView(TextView(this).apply { text = t(target); textSize = 12f; setTextColor(if (on) Design.orange else Design.muted); gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            nav.addView(tab, LinearLayout.LayoutParams(0, -1, 1f))
        }
        updateMetrics()
    }
    private fun modes(on: String, action: (String) -> Unit) = row { for (mode in listOf("tunnel", "proxy")) button(mode, mode == on) { action(mode) } }
    private fun home() {
        button("account") { go("account") }
        modes(settings().optString("mode")) { mode -> if (mode != settings().optString("mode")) idle { settings().put("mode",mode); save(); render() } }
        val compact = resources.displayMetrics.heightPixels / resources.displayMetrics.density < 780
        val area = vertical().apply { gravity = Gravity.CENTER; setPadding(0,dp(4),0,dp(8)) }; body.addView(area)
        power = PowerControl(this).apply { setOnClickListener { toggle() } }; val size=dp(if(compact)140 else 164);area.addView(power,LinearLayout.LayoutParams(size,size))
        screenStatus=TextView(this).apply { textSize=13f;gravity=Gravity.CENTER;setTextColor(Design.muted);setPadding(0,dp(7),0,dp(5)) };area.addView(screenStatus)
        card { row {
            body.addView(Symbol(this,"servers",Design.orange),LinearLayout.LayoutParams(dp(27),dp(30)))
            val old=body;val info=vertical().apply{setPadding(dp(12),0,dp(8),0)};old.addView(info,LinearLayout.LayoutParams(0,-2,1f));body=info
            help("selected");label(selected()?.optString("name") ?: t("select_profile"),15f,Design.text,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END}
            selected()?.let{label(protocolLabel(it),12f,Design.muted).textDirection=View.TEXT_DIRECTION_LTR};body=old
            iconAction(body,"next","servers"){go("servers")}
        } }.apply { setOnClickListener{go("servers")};isClickable=true;isFocusable=true }
        connectButton=button("connect",true){toggle()}
        card { row { downRate=statistic("down",Design.blue);upRate=statistic("up",Design.orange);ping=statistic("latency",Design.purple) } }
        if(settings().optString("mode")=="proxy")label("SOCKS5 ${settings().optString("bind")}:${settings().optInt("port")} · HTTP ${settings().optInt("port")+1}",12f,Design.muted).textDirection=View.TEXT_DIRECTION_LTR
        help("real_status_help")
    }

    private fun statistic(key: String, color: Int): TextView {
        val old = body; val p = vertical().apply { gravity = Gravity.CENTER }; old.addView(p, LinearLayout.LayoutParams(0, -2, 1f)); body = p
        body.addView(Symbol(this, if (key == "latency") "clock" else key, color), LinearLayout.LayoutParams(dp(19), dp(19)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        label(t(key), 10f, Design.muted).gravity = Gravity.CENTER
        val value = label("—", 16f, color, true).apply { gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR }
        body = old; return value
    }
    private fun toggle() {
        if(!accountReady){go("account");report("account_required");return}
        if (qa) { report("select_profile"); return }
        if (!isIdle) { pending = false; event("disconnecting"); startService(Intent(this, RadVpnService::class.java).setAction("stop")); return }
        if (selected() == null) { report("select_profile"); return }
        save()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100); return }
        requestVpn()
    }
    private fun requestVpn() {
        if(!accountReady){go("account");return}
        if (settings().optString("mode") == "tunnel") { val intent = VpnService.prepare(this); if (intent != null) { startActivityForResult(intent, 42); return } }
        startVpn()
    }
    private fun startVpn() { if(!accountReady){go("account");return};pending = true; RadVpnService.lastError = ""; event("starting"); updateMetrics(); startForegroundService(Intent(this, RadVpnService::class.java).setAction("start")) }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(code, permissions, grantResults); if (code == 100) requestVpn() }

    private fun checkForUpdate()=job {
        val candidate=VerifiedUpdates.find()
        handler.post {
            if(candidate==null)report("update_no_newer") else showDialog(AlertDialog.Builder(this).setTitle("RADVPN "+candidate.version).setMessage(t(if(candidate.testing)"update_testing_confirm" else "update_download_confirm")).setNegativeButton(t("close"),null).setPositiveButton(t("update")){_,_->job{
                val file=VerifiedUpdates.download(this,candidate)
                handler.post{try{VerifiedUpdates.install(this,file)}catch(e:Exception){report(e.message?:"operation_failed")}}
            }})
        }
    }
    private fun clearAccount() {
        if(active!="disconnected"||pending)startService(Intent(this,RadVpnService::class.java).setAction("stop"))
        accountReady=false;page="account";accountChecked=0L
        AccountApi.clearManagedData(data);repairSelection();save()
    }
    private fun accountRequest(action:()->JSONObject,done:(JSONObject)->Unit) {
        if(accountBusy)return
        accountBusy=true
        val guard=AccountRequestGuard(data)
        job {
            try { val result=action();handler.post{if(acceptAccountResult(guard))done(result)} }
            catch(e:Exception){handler.post{if(acceptAccountResult(guard)){if(e.message=="account_revoked")clearAccount();report(e.message?:"account_unavailable");render()}}}
        }
    }
    private fun acceptAccountResult(guard:AccountRequestGuard):Boolean {
        accountBusy=false
        if(destroyed)return false
        if(guard.matches(data))return true
        if(!accountReady)restoreAccount()
        return false
    }
    private fun restoreAccount() {
        if(data.optJSONObject("radvpn")?.optString("token").isNullOrBlank())return
        accountRequest({AccountApi.validate(data)}){result->
            data.getJSONObject("radvpn").put("endpoint",AccountApi.PRODUCTION_ENDPOINT).put("account",result)
            accountReady=true;accountVerifiedAt=SystemClock.elapsedRealtime();accountChecked=accountVerifiedAt;save();go("home")
        }
    }
    private fun accountHeartbeat() {
        val now=SystemClock.elapsedRealtime()
        if(!accountReady||accountBusy||now-accountChecked<30000L)return
        accountChecked=now
        val snapshot=JSONObject(data.toString());val token=snapshot.optJSONObject("radvpn")?.optString("token")?:return
        try {
            val result=AccountApi.validate(snapshot)
            handler.post{if(data.optJSONObject("radvpn")?.optString("token")==token){data.getJSONObject("radvpn").put("account",result);accountVerifiedAt=now;save()}}
        } catch(e:Exception) {
            handler.post{if(data.optJSONObject("radvpn")?.optString("token")==token){
                if(e.message=="account_revoked")clearAccount()
                else if(now-accountVerifiedAt>60000L){accountReady=false;page="account";startService(Intent(this,RadVpnService::class.java).setAction("stop"))}
                if(!accountReady)render()
            }}
        }
    }
    private fun accountPage() = AccountScreen.render(this,body,data,accountReady,::t,{label(it)},
        {key,primary,action->button(key,primary,action)},::accountRequest,::restoreAccount,
        {result->data.put("radvpn",result);accountReady=true;accountVerifiedAt=SystemClock.elapsedRealtime();accountChecked=accountVerifiedAt;save();go("home")},
        {sub,fresh->updateSubscription(sub,fresh)},::save,{clearAccount();render()},::render,::report,::showDialog)

    private fun repairSelection() { if (selected() == null) data.put("selected", items().firstOrNull { it.optBoolean("enabled", true) }?.optString("id") ?: "") }
    private fun merge(incoming: JSONArray, group: String = "") {
        data.put("profiles", SubscriptionUpdates.merge(profiles(), incoming, group))
        repairSelection()
    }

    private fun importText(value: String) = idle {
        job {
            val links=value.trim().split(Regex("\\s+"))
            check(links.isNotEmpty() && links.all { link->listOf("vless://","vmess://","trojan://","ss://").any{link.startsWith(it,true)} }){"direct_links_only"}
            val r = Api.call(JSONObject().put("method", "import").put("text", value))
            handler.post { merge(r.getJSONArray("profiles")); data.put("importInfo",r.optJSONArray("info") ?: JSONArray()); save(); go("servers"); Toast.makeText(this, "${t("imported")}: ${r.getJSONArray("profiles").length()} / ${r.optInt("rejected")}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun clipboard() = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
    private fun configs() {
        title("add_config");help("direct_link_help")
        val raw=field("server_links",multiline=true,showLabel=false).apply{minHeight=dp(128)};raw.hint="vless://…\nvmess://…\ntrojan://…\nss://…"
        button("add_server",true){importText(raw.text.toString())}
        row{button("paste"){raw.setText(clipboard())};button("qr"){if(!qa)IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt(t("qr")).setBeepEnabled(false).initiateScan()};button("file"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),50)}}
        card {
            title("add_subscription");val url=field("sub_url",showLabel=false);url.hint="https://provider.example/…"
            row{label(t("sub_tab_name"),11f,Design.muted).layoutParams=LinearLayout.LayoutParams(0,-2,1f);iconAction(body,"plus","add_subscription"){idle{prompt("subscription_name",""){name->updateSubscription(JSONObject().put("name",name.take(200)).put("url",url.text.toString().trim()).put("id",java.util.UUID.randomUUID().toString()),true)}}}}
        }
        card { row {
            body.addView(Symbol(this,"configs",Design.orange),LinearLayout.LayoutParams(dp(23),dp(27)))
            val old=body;val info=vertical().apply{setPadding(dp(12),0,0,0)};old.addView(info,LinearLayout.LayoutParams(0,-2,1f));body=info
            label(t("subscription_details"),14f);val details=data.optJSONArray("importInfo") ?: JSONArray()
            if(details.length()==0)help("subscription_details_help")else for(i in 0 until details.length())label(details.getString(i),12f,Design.muted)
            body=old
        }}.setOnClickListener{go("subscriptions")}
    }

    private fun serverPage() {
        if(page!="favorites"){
            val scroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
            val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            body.addView(scroll,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(10)});scroll.addView(tabs)
            fun tab(name:String,id:String){val on=groupFilter==id;val v=TextView(this).apply{minWidth=maxOf(dp(96),(resources.displayMetrics.widthPixels-dp(48))/3);text=name;textSize=14f;gravity=Gravity.CENTER;setPadding(dp(16),0,dp(16),0);setTextColor(if(on)Design.background else Design.muted);background=Design.button(this@MainActivity,on);isClickable=true;isFocusable=true;setOnClickListener{groupFilter=id;render()}};tabs.addView(v,LinearLayout.LayoutParams(-2,dp(44)).apply{marginEnd=dp(7)})}
            tab(t("all"),"");val subs=data.getJSONArray("subscriptions");for(i in 0 until subs.length()){val sub=subs.getJSONObject(i);tab(sub.optString("name").ifBlank{android.net.Uri.parse(sub.optString("url")).host ?: t("subscriptions")},sub.getString("id"))}
        }
        val source=items().filter{it.optString("name").contains(filter,true) && (page!="favorites" || it.optBoolean("favorite")) && (groupFilter.isEmpty() || it.optString("group")==groupFilter)}
        val sorted=source.sortedWith(if(latencySort)compareBy<JSONObject>{measured[it.optString("id")] ?: Long.MAX_VALUE}.thenBy{it.optString("name")} else compareBy{it.optString("name").lowercase()})
        label("${sorted.size} · ${t("servers")}",12f,Design.muted)
        for(item in sorted)profileRow(item)
        if(sorted.isEmpty())card{empty(if(items().isEmpty())"empty" else "no_matches")}
        if(probing)label("${t("testing_in_progress")} $completedTests",12f,Design.orange)
    }
    private fun showServerMenu(anchor:View){
        serverPopup?.dismiss()
        val popup=PopupMenu(this,anchor)
        fun entry(key:String,action:()->Unit){popup.menu.add(t(key)).setOnMenuItemClickListener{action();true}}
        entry(if(probing)"cancel" else "test_configs"){if(probing){probeCancel.set(true);job{Api.call(JSONObject().put("method","cancel_tests"))}}else testAll()}
        entry("sort_test_result"){latencySort=true;render()}
        entry("sort"){latencySort=false;render()}
        entry("favorites"){go("favorites")}
        entry("search"){prompt("search",filter){filter=it;render()}}
        entry("subscriptions"){go("subscriptions")}
        serverPopup=popup;popup.show()
    }
    private fun protocolLabel(p:JSONObject):String{
        val out=p.optJSONObject("outbound") ?: return "—";val stream=out.optJSONObject("streamSettings")
        val security=stream?.optString("security") ?: "";return out.optString("protocol").uppercase(Locale.US)+(if(security.isNotEmpty() && security!="none")" / "+security.uppercase(Locale.US) else "")
    }

    private fun profileRow(item:JSONObject){
        val id=item.optString("id");val enabled=item.optBoolean("enabled",true)
        card{row{
            body.addView(Symbol(this,"servers",if(id==data.optString("selected"))Design.orange else Design.muted),LinearLayout.LayoutParams(dp(28),dp(30)))
            val old=body;val info=vertical().apply{setPadding(dp(10),0,dp(6),0)};old.addView(info,LinearLayout.LayoutParams(0,-2,1f));body=info
            label(item.optString("name"),15f,if(enabled)Design.text else Design.subtle,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END}
            label(protocolLabel(item),12f,Design.muted).textDirection=View.TEXT_DIRECTION_LTR
            val group=item.optString("group");val subs=data.getJSONArray("subscriptions");for(i in 0 until subs.length()){val sub=subs.getJSONObject(i);if(sub.optString("id")==group)label(sub.optString("name"),11f,Design.subtle)}
            body=old
            val latency=if(!measured.containsKey(id))t("not_tested")else measured[id]?.let{"$it ms"} ?: t("probe_failed")
            label(latency,11f,if(measured[id]!=null)Design.green else Design.subtle).apply{layoutParams=LinearLayout.LayoutParams(-2,-2);maxWidth=dp(95)}
        }}.apply{if(id==data.optString("selected"))background=Design.surface(this@MainActivity,Design.panel,Design.orange);isClickable=true;isFocusable=true;contentDescription=item.optString("name")+", "+t("select");setOnClickListener{choose(item)};setOnLongClickListener{profileMenu(it,item);true}}
    }

    private fun choose(item: JSONObject) = idle { if (!item.optBoolean("enabled", true)) report("profile_disabled") else { data.put("selected", item.getString("id")); save(); go("home") } }
    private fun profileMenu(anchor: View, item: JSONObject) {
        PopupMenu(this, anchor).apply {
            fun entry(key: String, act: () -> Unit) { menu.add(t(key)).setOnMenuItemClickListener { act(); true } }
            entry("select") { choose(item) }
            entry("favorite") { item.put("favorite",!item.optBoolean("favorite"));save();render() }
            entry("rename") { prompt("name", item.optString("name")) { name -> if (name.isNotBlank()) { item.put("name", name.take(200)).put("userNamed",true); save(); render() } } }
            entry("toggle") { idle { item.put("enabled", !item.optBoolean("enabled", true)); repairSelection(); save(); render() } }
            entry("duplicate") { val copy = JSONObject(item.toString()).put("id", java.util.UUID.randomUUID().toString()).put("group", ""); profiles().put(copy); save(); render() }
            entry("edit_server") { idle { editServer(item) } }
            entry("delete") { idle { confirm("confirm_delete") { for (i in profiles().length() - 1 downTo 0) if (profiles().getJSONObject(i).optString("id") == item.optString("id")) profiles().remove(i); repairSelection(); save(); render() } } }
        }.show()
    }


    private fun editServer(original: JSONObject) {
        val candidate=JSONObject(original.toString())
        val outbound=candidate.getJSONObject("outbound")
        val protocol=outbound.getString("protocol")
        val endpoints=outbound.getJSONObject("settings")
        val endpoint=(endpoints.optJSONArray("vnext") ?: endpoints.getJSONArray("servers")).getJSONObject(0)
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        val setters=mutableListOf<()->Unit>()
        fun bind(node:JSONObject,key:String,label:String,number:Boolean=false){
            form.addView(TextView(this).apply{text=t(label);setTextColor(Design.muted)})
            val field=EditText(this).apply{setText(node.optString(key));setTextColor(Design.text);setSingleLine(true);background=Design.surface(this@MainActivity,Design.input);setPadding(dp(12),dp(8),dp(12),dp(8));inputType=if(number)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
            form.addView(field,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(14)})
            setters.add{if(number){val n=field.text.toString().toIntOrNull() ?: throw IllegalStateException("invalid_profile");check(n in 1..65535){"invalid_profile"};node.put(key,n)}else node.put(key,field.text.toString().trim())}
        }
        bind(candidate,"name","name");bind(endpoint,"address","address");bind(endpoint,"port","server_port",true)
        if(protocol in listOf("vless","vmess")){
            val user=endpoint.getJSONArray("users").getJSONObject(0);bind(user,"id","client_id");if(protocol=="vless")bind(user,"flow","flow")
        }else{bind(endpoint,"password","password");if(protocol=="shadowsocks")bind(endpoint,"method","method")}
        outbound.optJSONObject("streamSettings")?.let{stream->
            val security=stream.optString("security")
            stream.optJSONObject(if(security=="reality")"realitySettings" else "tlsSettings")?.let{tls->
                bind(tls,"serverName","sni");bind(tls,"fingerprint","fingerprint")
                if(security=="reality"){bind(tls,"publicKey","public_key");bind(tls,"shortId","short_id");bind(tls,"spiderX","spider_path")}
            }
            stream.optJSONObject("wsSettings")?.let{ws->bind(ws,"path","path");ws.optJSONObject("headers")?.let{bind(it,"Host","host")}}
            stream.optJSONObject("grpcSettings")?.let{bind(it,"serviceName","service_name")}
            stream.optJSONObject("xhttpSettings")?.let{bind(it,"path","path");bind(it,"host","host")}
        }
        val scroll=ScrollView(this).apply{addView(form)}
        val dialog=AlertDialog.Builder(this).setTitle(t("edit_server")).setView(scroll).setPositiveButton(t("save"),null).setNegativeButton(t("cancel"),null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            try{
                setters.forEach{it()};check(candidate.optString("name").isNotBlank() && candidate.optString("name").length<=200){"invalid_profile"}
                val validation=JSONObject(settings().toString()).put("mode","proxy")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                worker.execute{try{Api.call(JSONObject().put("method","validate").put("profile",candidate).put("settings",validation));handler.post{original.put("name",candidate.getString("name")).put("outbound",outbound).put("userNamed",true);measured.remove(original.optString("id"));save();dialog.dismiss();render()}}catch(e:Exception){handler.post{dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true;report(e.message ?: "invalid_profile")}}}
            }catch(e:Exception){report(e.message ?: "invalid_profile")}
        }}
        dialog.show()
    }

    private fun testAll(){
        if(probing || !isIdle){report("already_running");return}
        val source=items().filter{it.optBoolean("enabled",true) && (groupFilter.isEmpty() || it.optString("group")==groupFilter)}.map{JSONObject(it.toString())}
        if(source.isEmpty())return
        probing=true;completedTests=0;probeCancel=AtomicBoolean(false);val cancelled=probeCancel;val candidateSettings=JSONObject(settings().toString());render()
        job{
            try{
                val futures=source.map{item->probes.submit<Triple<String,Long?,Boolean>>{
                    if(cancelled.get())return@submit Triple(item.optString("id"),null,false)
                    val r=try{Api.call(JSONObject().put("method","probe").put("profile",item).put("settings",candidateSettings))}catch(_:Exception){JSONObject()}
                    val ms=if(r.optBoolean("connected") && !r.isNull("latency") && r.optLong("up")>0 && r.optLong("down")>0)r.optLong("latency")else null
                    handler.post{completedTests++;if(page=="servers")render()}
                    Triple(item.optString("id"),ms,true)
                }}
                val results=futures.map{it.get()}
                handler.post{for((id,ms,attempted)in results)if(attempted)measured[id]=ms}
            }finally{handler.post{probing=false;if(page in listOf("servers","favorites","profiles"))render()}}
        }
    }

    private fun subscriptions() {
        if (data.getJSONArray("subscriptions").length() == 0) card { empty("no_subs", false) }
        val subs = data.getJSONArray("subscriptions")
        for (i in 0 until subs.length()) {
            val sub = subs.getJSONObject(i)
            card {
                val meta=sub.optJSONArray("info");if(meta!=null)for(j in 0 until meta.length())label(meta.getString(j),13f,Design.muted)
                titleText(sub.optString("name").ifBlank { android.net.Uri.parse(sub.optString("url")).host ?: "RADVPN" })
                label(android.net.Uri.parse(sub.optString("url")).host ?: "—", 12f, Design.muted)
                label("${t("profile_count")}: ${sub.optInt("count")} · ${t("last_refresh")}: ${sub.optString("last", "—")}", 12f, Design.subtle)
                if(sub.optInt("rejected")>0) label("${t("subscription_rejected")}: ${sub.optInt("rejected")}",12f,Design.orange)
                row { button("update", true) { idle { updateSubscription(sub) } }; button("rename") { prompt("subscription_name", sub.optString("name")) { n -> sub.put("name", n.take(200)); save(); render() } } }
                button("delete") { idle { confirm("remove_sub") { val id = sub.getString("id"); for (j in profiles().length() - 1 downTo 0) if (profiles().getJSONObject(j).optString("group") == id) profiles().remove(j); subs.remove(i); repairSelection(); save(); render() } } }
            }
        }
        card {
            title("add_subscription"); val name = field("subscription_name"); val url = field("sub_url")
            row { button("paste") { url.setText(clipboard()) }; button("add", true) { idle { updateSubscription(JSONObject().put("name", name.text.toString().take(200)).put("url", url.text.toString().trim()).put("id", java.util.UUID.randomUUID().toString()), true) } } }
            help("android_refresh_help")
        }
    }
    private fun updateSubscription(sub:JSONObject,add:Boolean=false)=job{
        val r=Api.call(JSONObject().put("method","subscription").put("text",sub.getString("url")))
        handler.post{
            try {
                SubscriptionUpdates.apply(data, sub, r, add)
                repairSelection()
            } catch (e: Exception) { report(if(e.message in listOf("subscription_exists","invalid_profile"))e.message!! else "operation_failed"); return@post }
            save();render()
        }
    }

    private fun titleText(value: String) = label(value, 16f, Design.text, true)
    private fun changeLanguage(lang: String) { data.put("lang", lang); words = Words(this, lang); save(); render() }
    private fun preferenceCard(key:String,titleKey:String,helpKey:String,symbol:String,changed:()->Unit){
        card{
            row{
                body.addView(Symbol(this,symbol,Design.orange),LinearLayout.LayoutParams(dp(21),dp(24)))
                val text=label(t(titleKey),14f,Design.text,true);text.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(8)}
                val control=Switch(this).apply{isChecked=data.optBoolean(key,key!="allowCapture");thumbTintList=android.content.res.ColorStateList.valueOf(Design.text);trackTintList=android.content.res.ColorStateList.valueOf(if(isChecked)Design.orange else Design.border);minHeight=dp(44);contentDescription=t(titleKey);setOnCheckedChangeListener{_,on->data.put(key,on);save();changed();trackTintList=android.content.res.ColorStateList.valueOf(if(on)Design.orange else Design.border)}}
                body.addView(control,LinearLayout.LayoutParams(-2,dp(44)))
            };help(helpKey)
            if(key=="showSpeed")row{label("↓ ${bytes(rateDown)}/s",12f,Design.blue).layoutParams=LinearLayout.LayoutParams(0,-2,1f);label("↑ ${bytes(rateUp)}/s",12f,Design.orange).apply{gravity=Gravity.END;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}}
        }
    }
    private fun settingsPage() {
        if(page!="settings") row { button("general", page == "settings") { go("settings") }; button("connection", page == "connection") { go("connection") }; button("advanced_short", page == "advanced") { go("advanced") } }
        if(page=="settings"){
            title("general");help("display_preferences")
            card{row{
                body.addView(Symbol(this,"proxy",Design.muted),LinearLayout.LayoutParams(dp(23),dp(26)))
                val old=body;val info=vertical().apply{setPadding(dp(12),0,dp(6),0)};old.addView(info,LinearLayout.LayoutParams(0,-2,1f));body=info
                label(t("language"),14f);label(if(data.optString("lang")=="fa")"فارسی · راست‌به‌چپ" else "English · Left to right",11f,Design.muted);body=old
                label(if(data.optString("lang")=="fa")"فارسی" else "English",12f,Design.orange).layoutParams=LinearLayout.LayoutParams(-2,-2)
            }}.setOnClickListener{showDialog(AlertDialog.Builder(this).setTitle(t("language")).setItems(arrayOf("English","فارسی")){_,which->changeLanguage(if(which==0)"en" else "fa")})}
            preferenceCard("allowCapture","capture_allowed","capture_help","eye"){applyCapturePolicy()}
            preferenceCard("showSpeed","notification_speed","notification_speed_help","bell"){}
            card{row{
                body.addView(Symbol(this,"settings",Design.muted),LinearLayout.LayoutParams(dp(24),dp(26)))
                val old=body;val info=vertical().apply{setPadding(dp(12),0,dp(6),0)};old.addView(info,LinearLayout.LayoutParams(0,-2,1f));body=info;label(t("connection"),14f);label(t("connection_summary"),11f,Design.muted);body=old
                body.addView(Symbol(this,"next",Design.muted),LinearLayout.LayoutParams(dp(18),dp(20)))
            }}.setOnClickListener{go("connection")}
            card{row{
                body.addView(Symbol(this,"brand",Design.muted),LinearLayout.LayoutParams(dp(24),dp(26)))
                label(t("about"),14f).apply{setPadding(dp(12),0,0,0);layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
                label(BuildConfig.VERSION_NAME,12f,Design.orange).layoutParams=LinearLayout.LayoutParams(-2,-2)
            }}.setOnClickListener{go("about")}
            return
        }
        val draft = JSONObject(settings().toString())
        card {
            title(if (page == "connection") "mode" else "advanced")
            help("save_cancel")
            if (page == "connection") {
                val modeButtons = mutableListOf<Button>()
                row { for (mode in listOf("tunnel", "proxy")) modeButtons.add(button(mode, draft.optString("mode") == mode) { draft.put("mode", mode); for ((index,b) in modeButtons.withIndex()) { val on = listOf("tunnel", "proxy")[index] == mode; b.background = Design.button(this, on); b.setTextColor(if (on) Design.background else Design.muted) } }) }
                val port = field("port", draft.optString("port"), numeric = true); val bind = field("bind", draft.optString("bind")); val mtu = field("mtu", draft.optString("mtu"), numeric = true)
                val dns = field("dns", (0 until draft.getJSONArray("dns").length()).joinToString(",") { draft.getJSONArray("dns").getString(it) })
                help("android_ipv6_help"); help("proxy_help")
                row { button("cancel") { render() }; button("save", true) { idle { job {
                    val candidate = JSONObject(draft.toString()).put("port", port.text.toString().toIntOrNull() ?: -1).put("bind", bind.text.toString().trim()).put("mtu", mtu.text.toString().toIntOrNull() ?: -1).put("dns", JSONArray(dns.text.toString().split(',').map { it.trim() }))
                    Api.call(JSONObject().put("method", "settings").put("settings", candidate)); handler.post { data.put("settings", candidate); save(); Toast.makeText(this, t("applied"), Toast.LENGTH_SHORT).show(); render() }
                } } } }
            } else {
                help("rules_help"); val rules = field("rules", draft.optJSONArray("rules")?.toString(2) ?: "[]", true)
                button("save", true) { idle { job { val candidate = JSONObject(draft.toString()).put("rules", JSONArray(rules.text.toString())); Api.call(JSONObject().put("method", "settings").put("settings", candidate)); handler.post { data.put("settings", candidate); save(); Toast.makeText(this, t("applied"), Toast.LENGTH_SHORT).show() } } } }
                help("advanced_help"); button("profiles") { go("profiles") }; help("backup_help")
                button("export") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "RADVPN-3.0.3.radvpn"), 51) }
                button("restore") { idle { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream"), 52) } }
            }
        }
        row{button("advanced_short"){go("advanced")};button("logs"){go("logs")};button("traffic"){go("traffic")}}
        button("reset") { idle { confirm("confirm_reset") { job { val defaults = Api.call(JSONObject().put("method", "defaults")); handler.post { data.put("settings", defaults); save(); render() } } } } }
    }
    private fun traffic() {
        card { title("session"); help("android_traffic_help"); row { downRate = statistic("down", Design.blue); upRate = statistic("up", Design.orange); ping = statistic("latency", Design.purple) }; session = label("", 13f, Design.muted).apply { textDirection = View.TEXT_DIRECTION_LTR } }
        card { title("live_traffic"); plot = TrafficPlot(this, samples); body.addView(plot, LinearLayout.LayoutParams(-1, dp(180))); if (samples.isEmpty()) help("awaiting_traffic") }
        help("measured_only")
    }
    private fun safeDiagnostics() = JSONObject().put("app", "3.0.3").put("core", "26.10.21").put("state", active).put("mode", settings().optString("mode")).put("profileCount", profiles().length()).put("tunnelDeviceVerified", false).put("events", JSONArray(events.map { it.first + " " + it.second }))
    private fun diagnostics() {
        card {
            title("diagnostics"); help("safe_name"); help("logging_fixed")
            button("copy_diagnostics") { getSystemService(ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("RADVPN", safeDiagnostics().toString(2))); Toast.makeText(this, t("copied"), Toast.LENGTH_SHORT).show() }
            button("export_diagnostics") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "RADVPN-diagnostics.json"), 53) }
            button("check_ports") { idle { val bindAddress = settings().optString("bind"); val port = settings().optInt("port"); job { val sockets = mutableListOf<ServerSocket>(); var available = true; try { for (p in listOf(port, port + 1)) sockets.add(ServerSocket().apply { bind(InetSocketAddress(bindAddress, p)) }) } catch (_: Exception) { available = false } finally { sockets.forEach { it.close() } }; handler.post { report(if (available) "ports_free" else "ports_busy") } } } }
            button("clear_logs") { confirm("confirm_clear") { events.clear(); render() } }
        }
        card { if (events.isEmpty()) help("no_events") else for ((time, key) in events) { label(time, 11f, Design.subtle).textDirection = View.TEXT_DIRECTION_LTR; label(t(key), 13f, Design.muted) } }
    }
    private fun about() {
        card {
            body.addView(Symbol(this, "brand", Design.orange), LinearLayout.LayoutParams(dp(66), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) })
            label("RADVPN 3.0.3", 26f, Design.text, true).apply { gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            label("Xray 26.10.21", 16f, Design.orange).apply { gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR }
            help("android_testing_channel"); help("privacy"); help("not_full_protection")
            button("check_updates") { checkForUpdate() }
            button("license_notices") { val notice = assets.open("licenses/ANDROID-NOTICES.txt").bufferedReader().use { it.readText() }; prompt("license_notices", notice.take(180000), true, readOnly = true) {} }
        }
    }
    private fun confirm(key: String, action: () -> Unit) { showDialog(AlertDialog.Builder(this).setTitle("RADVPN").setMessage(t(key)).setPositiveButton(t("save")) { _, _ -> action() }.setNegativeButton(t("cancel"), null)) }
    private fun prompt(key: String, value: String, multiline: Boolean = false, readOnly: Boolean = false, action: (String) -> Unit) {
        val parent = vertical().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }; val old = body; body = parent; val input = field(key, value, multiline); body = old
        input.isFocusable = !readOnly
        val scroll = ScrollView(this).apply { addView(parent) }
        showDialog(AlertDialog.Builder(this).setTitle(t(key)).setView(scroll).setPositiveButton(t(if (readOnly) "close" else "save")) { _, _ -> if (!readOnly) action(input.text.toString()) }.setNegativeButton(t("cancel"), null))
    }
    private fun bounded(input: java.io.InputStream, limit: Int): ByteArray { val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); while (out.size() < limit) { val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size())); if (n < 0) break; out.write(buffer, 0, n) }; return out.toByteArray() }
    @Deprecated("Native Activity permission and document flow")
    override fun onActivityResult(code: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(code, resultCode, result)
        if (code == 42) { if (resultCode == RESULT_OK) startVpn() else report("permission_denied"); return }
        if (resultCode == RESULT_OK && code in 50..53) {
            val uri = result?.data ?: return
            job { when (code) {
                50 -> { val b = contentResolver.openInputStream(uri)!!.use { bounded(it, 2 * 1024 * 1024 + 1) }; if (b.size > 2 * 1024 * 1024) throw IllegalStateException("import_too_large"); handler.post { importText(String(b, Charsets.UTF_8)) } }
                51 -> { val copy = Vault.encrypt(data); contentResolver.openOutputStream(uri)!!.use { it.write(copy) } }
                52 -> { val b = contentResolver.openInputStream(uri)!!.use { bounded(it, 8 * 1024 * 1024 + 1) }; val restored = Vault.decrypt(b); check(restored.optJSONArray("profiles")!=null && restored.optJSONArray("subscriptions")!=null && restored.optString("lang") in listOf("en","fa")){"storage_failed"}; Api.call(JSONObject().put("method", "settings").put("settings", restored.getJSONObject("settings"))); handler.post { accountReady=false;accountVerifiedAt=0L;accountChecked=0L;page="account";if(active!="disconnected"||pending)startService(Intent(this,RadVpnService::class.java).setAction("stop")); data = restored; words = Words(this, data.optString("lang", "en")); repairSelection(); save(); render();restoreAccount() } }
                53 -> contentResolver.openOutputStream(uri)!!.use { it.write(safeDiagnostics().toString(2).toByteArray()) }
            } }; return
        }
        IntentIntegrator.parseActivityResult(code, resultCode, result)?.contents?.let { importText(it) }
    }
    @Deprecated("Native back navigation") override fun onBackPressed() { if (page != "home") go("home") else super.onBackPressed() }
    fun showErrorForCapture(): AlertDialog { check(qa); return showDialog(AlertDialog.Builder(this).setTitle("RADVPN").setMessage(t("invalid_dns")).setPositiveButton(t("close"), null)) }
    fun verifyGroupsForCapture() {
        check(qa)
        data.put("profiles",JSONArray()).put("selected","")
        val fixture=Api.call(JSONObject().put("method","import").put("text","socks://127.0.0.1:19876#Fixture")).getJSONArray("profiles")
        merge(fixture,"one");merge(fixture,"two")
        check(profiles().length()==2)
        val withMetadata=JSONObject(profiles().getJSONObject(0).toString()).put("userNamed",true).put("probeLatency",21)
        Api.call(JSONObject().put("method","validate").put("profile",withMetadata).put("settings",JSONObject(settings().toString()).put("mode","proxy")))
        check(items().map{it.optString("id")}.distinct().size==2)
        check(items().count{it.optString("group")=="one"}==1 && items().count{it.optString("group")=="two"}==1)
        profiles().getJSONObject(0).put("favorite",true).put("name","Custom").put("userNamed",true)
        merge(fixture,"one")
        check(profiles().length()==2 && profiles().getJSONObject(0).optBoolean("favorite") && profiles().getJSONObject(0).optString("name")=="Custom")
        check(selected()!=null)
    }
    fun showForCapture(target:String,language:String){
        check(qa);require(target in qaPages)
        data.put("lang",language);words=Words(this,language);data.put("profiles",JSONArray()).put("subscriptions",JSONArray()).put("selected","");favoritesOnly=target=="favorites";groupFilter=""
        if(target in listOf("home-populated","servers-populated","server-menu","favorites")){
            val subs=JSONArray().put(JSONObject().put("id","qa-personal").put("name",if(language=="fa")"ساب شخصی" else "Personal").put("url","https://example.invalid/personal")).put(JSONObject().put("id","qa-backup").put("name",if(language=="fa")"ساب پشتیبان" else "Backup").put("url","https://example.invalid/backup"));data.put("subscriptions",subs)
            val links=listOf("vless://aabbccdd-aabb-ccdd-aabb-aabbccddeeff@example.invalid:443?security=reality&sni=example.invalid&pbk=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY&fp=chrome&flow=xtls-rprx-vision&headerType=none#Netherlands%20%C2%B7%2002","trojan://local-test-only@example.invalid:443#Germany%20%C2%B7%2001","vmess://aabbccdd-aabb-ccdd-aabb-aabbccddeeff@example.invalid:443?security=tls&type=ws#France%20%C2%B7%2003")
            for((i,link)in links.withIndex()){val incoming=Api.call(JSONObject().put("method","import").put("text",link)).getJSONArray("profiles");merge(incoming,if(i==1)"qa-backup" else "qa-personal")}
            profiles().getJSONObject(0).put("favorite",true);repairSelection()
        }
        if(target=="subscriptions") data.put("subscriptions",JSONArray().put(JSONObject().put("id","qa-personal").put("name",if(language=="fa")"ساب شخصی" else "Personal").put("url","https://example.invalid/subscription").put("count",3)))
        page=when(target){"home-populated"->"home";"servers-populated","server-menu"->"servers";else->target};render()
        if(target=="server-menu")serverAnchor?.let{showServerMenu(it)}
    }
}
