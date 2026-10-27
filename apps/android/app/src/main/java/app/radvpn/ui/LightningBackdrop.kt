package app.radvpn.ui

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.widget.FrameLayout

/** Exact approved background artwork, center-cropped behind native controls. */
class LightningBackdrop(context: Context) : FrameLayout(context) {
    companion object { private var cached: Bitmap? = null }
    init { setWillNotDraw(false) }
    private val image: Bitmap = cached ?: run {
        val encoded = context.assets.list("lightning")!!.sorted().joinToString("") { name -> context.assets.open("lightning/$name").bufferedReader().use { it.readText() } }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.also { cached = it }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Design.background)
        val scale = maxOf(width.toFloat()/image.width, height.toFloat()/image.height)
        val w = image.width*scale; val h=image.height*scale
        paint.shader=null
        canvas.drawBitmap(image,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint)
        paint.shader=LinearGradient(0f,0f,0f,height.toFloat(),Color.argb(51,7,11,15),Color.argb(119,7,11,15),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
    }
}
