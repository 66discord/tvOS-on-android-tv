package com.tvsos.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

val LP_MATCH: Int = ViewGroup.LayoutParams.MATCH_PARENT
val LP_WRAP: Int = ViewGroup.LayoutParams.WRAP_CONTENT

/** Paleta e tipografia base do tvsOS. */
object C {
    val BG = 0xFF05050A.toInt()
    val CARD = 0xF7151520.toInt()
    val CARD_SOFT = 0x9910101A.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val TEXT_DIM = 0x9EFFFFFF.toInt()
    val TEXT_FAINT = 0x5EFFFFFF.toInt()

    val ACCENTS = intArrayOf(
        0xFF0A84FF.toInt(),
        0xFFBF5AF2.toInt(),
        0xFFFF375F.toInt(),
        0xFFFF9F0A.toInt(),
        0xFF30D158.toInt(),
        0xFF64D2FF.toInt(),
        0xFF98989D.toInt()
    )
    val ACCENT_NAMES = arrayOf("Azul", "Roxo", "Rosa", "Laranja", "Verde", "Ciano", "Grafite")
}

/** Estado visual global (cor de destaque e animacoes) usado pelas Views. */
object UI {
    var accent: Int = C.ACCENTS[0]
    var animations: Boolean = true
}

fun dpi(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

fun withAlpha(color: Int, a: Int): Int {
    val aa = when {
        a < 0 -> 0
        a > 255 -> 255
        else -> a
    }
    return (color and 0x00FFFFFF) or (aa shl 24)
}

fun blend(from: Int, to: Int, t: Float): Int {
    val k = when {
        t < 0f -> 0f
        t > 1f -> 1f
        else -> t
    }
    val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * k).toInt()
    val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * k).toInt()
    val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * k).toInt()
    val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * k).toInt()
    return Color.argb(a, r, g, b)
}

fun roundedBg(color: Int, radiusPx: Float, stroke: Int = 0, strokePx: Float = 0f): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = radiusPx
    g.setColor(color)
    if (strokePx > 0f) g.setStroke(strokePx.toInt().coerceAtLeast(1), stroke)
    return g
}

fun styleText(tv: TextView, sp: Float, color: Int, bold: Boolean = false) {
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    tv.setTextColor(color)
    tv.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    tv.includeFontPadding = false
}

fun View.makeFocusable() {
    isFocusable = true
    isFocusableInTouchMode = true
}

fun View.roundOutline(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    clipToOutline = true
}

fun View.focusScale(focused: Boolean, scale: Float = 1.08f, ms: Long = 190) {
    animate().cancel()
    val duration = if (UI.animations) ms else 0L
    animate()
        .scaleX(if (focused) scale else 1f)
        .scaleY(if (focused) scale else 1f)
        .setDuration(duration)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

fun sectionLabel(ctx: Context, text: String): TextView {
    val tv = TextView(ctx)
    tv.text = text.uppercase(Locale("pt", "BR"))
    styleText(tv, 11f, withAlpha(C.TEXT, 130), true)
    tv.letterSpacing = 0.18f
    return tv
}

fun noteText(ctx: Context, text: String): TextView {
    val tv = TextView(ctx)
    tv.text = text
    styleText(tv, 12.5f, C.TEXT_DIM)
    tv.setLineSpacing(dpf(ctx, 3f), 1f)
    return tv
}

fun Drawable.toBitmapSized(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}

/** "Blur" barato: reduz, amplia com filtro bilinear e aplica alfa. */
fun fakeBlur(src: Bitmap, w: Int, h: Int, alpha: Int): Bitmap {
    val sw = max(1, src.width / 12)
    val sh = max(1, src.height / 12)
    val small = Bitmap.createScaledBitmap(src, sw, sh, true)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val scale = max(w.toFloat() / sw, h.toFloat() / sh)
    val dw = sw * scale
    val dh = sh * scale
    val left = (w - dw) / 2f
    val top = (h - dh) / 2f
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    paint.alpha = alpha
    canvas.drawBitmap(small, null, RectF(left, top, left + dw, top + dh), paint)
    return out
}

/** Cor dominante "viva" de um icone, usada no brilho do fundo. */
fun dominantColor(bmp: Bitmap): Int {
    val w = min(bmp.width, 24)
    val h = min(bmp.height, 24)
    if (w <= 0 || h <= 0) return C.ACCENTS[0]
    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (y in 0 until h) {
        for (x in 0 until w) {
            val px = small.getPixel(x, y)
            if (Color.alpha(px) < 40) continue
            val rr = Color.red(px)
            val gg = Color.green(px)
            val bb = Color.blue(px)
            val mx = max(rr, max(gg, bb))
            val mn = min(rr, min(gg, bb))
            if (mx - mn < 20) continue
            r += rr
            g += gg
            b += bb
            n++
        }
    }
    if (n == 0L) return C.ACCENTS[0]
    val avg = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    val hsv = FloatArray(3)
    Color.colorToHSV(avg, hsv)
    hsv[1] = max(hsv[1], 0.55f)
    hsv[2] = max(hsv[2], 0.80f)
    return Color.HSVToColor(hsv)
}
