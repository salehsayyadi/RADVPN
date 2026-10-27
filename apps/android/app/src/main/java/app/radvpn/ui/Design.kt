package app.radvpn.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.LinearLayout

/** Native design tokens shared by every Android screen. No artwork is used as a UI screenshot. */
object Design {
    val background = Color.rgb(5, 8, 11)
    val app = Color.rgb(7, 11, 15)
    val panel = Color.argb(240, 11, 17, 24)
    val secondary = Color.rgb(14, 22, 32)
    val input = Color.rgb(16, 26, 37)
    val border = Color.rgb(30, 44, 59)
    val orange = Color.rgb(255, 106, 0)
    val highlight = Color.rgb(255, 133, 10)
    val text = Color.rgb(243, 246, 250)
    val muted = Color.rgb(155, 174, 194)
    val subtle = Color.rgb(120, 142, 163)
    val green = Color.rgb(25, 212, 154)
    val blue = Color.rgb(66, 165, 255)
    val purple = Color.rgb(155, 123, 255)
    val amber = Color.rgb(255, 181, 71)
    val red = Color.rgb(255, 79, 100)
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density + .5f).toInt()
    fun surface(c: Context, color: Int = panel, stroke: Int = border, radius: Int = 12) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, radius).toFloat(); setStroke(dp(c, 1), stroke)
    }
    fun icon(c: Context, kind: String, tint: Int): android.graphics.drawable.Drawable {
        val n = dp(c, 20)
        val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Symbol(c, kind, tint).apply { layout(0, 0, n, n); draw(Canvas(bitmap)) }
        return android.graphics.drawable.BitmapDrawable(c.resources, bitmap)
    }
    fun button(c: Context, primary: Boolean = false): StateListDrawable {
        fun shape(pressed: Boolean) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (primary) intArrayOf(if (pressed) orange else highlight, orange)
            else intArrayOf(if (pressed) input else secondary, secondary)).apply {
            cornerRadius = dp(c, 10).toFloat(); setStroke(dp(c, 1), if (primary) orange else border)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(true))
            addState(intArrayOf(android.R.attr.state_focused), surface(c, if (primary) orange else input, highlight, 10))
            addState(intArrayOf(), shape(false))
        }
    }
}

/** Consistent original vector stroke icons, including the RADVPN shield/lightning mark. */
class Symbol(context: Context, private val kind: String, var tint: Int = Design.muted) : View(context) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); val scale = minOf(width, height) / 24f
        canvas.translate((width - 24 * scale) / 2, (height - 24 * scale) / 2); canvas.scale(scale, scale)
        if (kind in listOf("back", "next") && layoutDirection == View.LAYOUT_DIRECTION_RTL) canvas.scale(-1f, 1f, 12f, 12f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 1.65f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun line(a: Float, b: Float, c: Float, d: Float) = canvas.drawLine(a, b, c, d, p)
        fun path(vararg points: Float) { val v = Path(); v.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) v.lineTo(points[i], points[i + 1]); canvas.drawPath(v, p) }
        when (kind) {
            "brand", "tunnel" -> { path(12f, 2f, 22f, 6f, 21f, 14f, 18f, 19f, 12f, 23f, 6f, 19f, 3f, 14f, 2f, 6f, 12f, 2f); path(13f, 6f, 8f, 13f, 13f, 13f, 10f, 19f, 17f, 10f, 12f, 10f, 13f, 6f) }
            "power" -> { canvas.drawArc(4f, 4f, 20f, 21f, -47f, 274f, false, p); line(12f, 2f, 12f, 12f) }
            "home" -> { path(3f, 10f, 12f, 3f, 21f, 10f); path(5f, 9f, 5f, 21f, 10f, 21f, 10f, 14f, 14f, 14f, 14f, 21f, 19f, 21f, 19f, 9f) }
            "servers", "profiles" -> { for (y in listOf(4f, 14f)) { canvas.drawRoundRect(3f, y, 21f, y + 6f, 1f, 1f, p); line(6f, y + 3f, 7f, y + 3f); line(16f, y + 3f, 18f, y + 3f) } }
            "settings" -> { canvas.drawCircle(12f, 12f, 4f, p); for (i in 0..7) { val r = Math.toRadians(i * 45.0); line((12 + 8 * kotlin.math.cos(r)).toFloat(), (12 + 8 * kotlin.math.sin(r)).toFloat(), (12 + 10 * kotlin.math.cos(r)).toFloat(), (12 + 10 * kotlin.math.sin(r)).toFloat()) }; canvas.drawCircle(12f, 12f, 8f, p) }
            "proxy", "subscriptions" -> { canvas.drawCircle(12f, 12f, 9f, p); canvas.drawOval(8f, 3f, 16f, 21f, p); line(3f, 12f, 21f, 12f) }
            "down", "up" -> { val top = if (kind == "down") 4f else 18f; val end = if (kind == "down") 18f else 4f; line(12f, top, 12f, end); path(7f, if (kind == "down") 13f else 9f, 12f, end, 17f, if (kind == "down") 13f else 9f); line(5f, 22f, 19f, 22f) }
            "clock" -> { canvas.drawCircle(12f, 12f, 9f, p); path(12f, 6f, 12f, 12f, 16f, 15f) }
            "plus", "add" -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
            "star" -> { val v = Path(); for (i in 0..9) { val r = if (i % 2 == 0) 10.0 else 4.5; val a = Math.toRadians(i * 36.0 - 90); val x = (12 + r * kotlin.math.cos(a)).toFloat(); val y = (12 + r * kotlin.math.sin(a)).toFloat(); if (i == 0) v.moveTo(x, y) else v.lineTo(x, y) }; v.close(); canvas.drawPath(v, p) }
            "qr" -> { for ((x,y) in listOf(3f to 3f, 15f to 3f, 3f to 15f)) { canvas.drawRect(x,y,x+6,y+6,p) }; path(15f,15f,18f,15f,18f,18f,21f,18f,21f,21f,15f,21f) }
            "back" -> path(14f, 5f, 7f, 12f, 14f, 19f)
            "next" -> path(9f, 5f, 16f, 12f, 9f, 19f)
            "more" -> { for(y in listOf(5f,12f,19f))canvas.drawCircle(12f,y,1f,p) }
            "eye" -> { canvas.drawOval(2f,6f,22f,18f,p); canvas.drawCircle(12f,12f,3f,p) }
            "bell" -> { path(5f,17f,7f,14f,7f,8f,9f,4f,15f,4f,17f,8f,17f,14f,19f,17f,5f,17f); canvas.drawArc(9f,17f,15f,22f,0f,180f,false,p) }
            "copy" -> { canvas.drawRoundRect(8f,7f,21f,22f,1f,1f,p);path(5f,17f,3f,17f,3f,2f,16f,2f,16f,4f) }
            "search" -> { canvas.drawCircle(10f, 10f, 6f, p); line(15f, 15f, 21f, 21f) }
            "traffic" -> { line(3f, 21f, 21f, 21f); line(6f, 17f, 6f, 10f); line(12f, 17f, 12f, 3f); line(18f, 17f, 18f, 7f) }
            "refresh" -> { canvas.drawArc(4f, 4f, 20f, 20f, 30f, 285f, false, p); path(15f, 3f, 20f, 4f, 20f, 9f) }
            "check" -> path(4f, 12f, 10f, 18f, 21f, 5f)
            else -> { canvas.drawRoundRect(5f, 2f, 19f, 22f, 2f, 2f, p); line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 13f, 16f) }
        }
        canvas.restore()
    }
}

class PowerControl(context: Context) : LinearLayout(context) {
    var state = "disconnected"
        set(value) { field = value; invalidate() }
    init { setWillNotDraw(false); gravity = android.view.Gravity.CENTER; isClickable = true; isFocusable = true; minimumWidth = Design.dp(context, 164); minimumHeight = Design.dp(context, 164) }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val x = width / 2f; val y = height / 2f; val r = minOf(x,y) - Design.dp(context,8)
        val color = if (state == "connected") Design.green else if (state == "error") Design.red else Design.orange
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(x,y,r*1.28f,intArrayOf(Color.TRANSPARENT,Color.argb(38,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT),floatArrayOf(0f,.78f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r*1.28f,p);p.shader=null;p.color=Design.app;c.drawCircle(x,y,r,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=Design.dp(context,2).toFloat();p.color=color;c.drawCircle(x,y,r,p)
        p.strokeWidth=Design.dp(context,3).toFloat();p.strokeCap=Paint.Cap.ROUND
        c.drawArc(x-r*.43f,y-r*.38f,x+r*.43f,y+r*.46f,-47f,274f,false,p);c.drawLine(x,y-r*.48f,x,y+r*.05f,p)
        if(isPressed){p.color=Color.argb(70,255,133,10);p.style=Paint.Style.FILL;c.drawCircle(x,y,r,p)}
    }
    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
}

/** Samples are actual foreground counter deltas, never simulated history. */
class TrafficPlot(context: Context, private val samples: List<Pair<Long,Long>>) : View(context) {
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=Design.dp(context,1).toFloat();style=Paint.Style.STROKE;color=Design.border }
        for(i in 1..3)c.drawLine(0f,height*i/4f,width.toFloat(),height*i/4f,p)
        if(samples.size<2)return
        val max=samples.maxOf{maxOf(it.first,it.second)}.coerceAtLeast(1).toFloat()
        for(up in listOf(false,true)){
            p.color=if(up)Design.orange else Design.blue;p.strokeWidth=Design.dp(context,2).toFloat();val path=Path()
            samples.forEachIndexed { i,s -> val x=width*i.toFloat()/(samples.size-1);val y=height-(if(up)s.first else s.second)/max*(height-Design.dp(context,8));if(i==0)path.moveTo(x,y)else path.lineTo(x,y) };c.drawPath(path,p)
        }
    }
}
