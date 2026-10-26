package com.tvsos.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

/** Fundo com brilho colorido que reage ao app em foco. */
class BackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var colorA = C.ACCENTS[0]
    private var colorB = 0xFF5E5CE6.toInt()
    private var animator: ValueAnimator? = null

    var style: Int = 0
        set(value) {
            field = value.coerceIn(0, 2)
            invalidate()
        }

    fun setAccent(color: Int, animated: Boolean = false) {
        setGlow(color, blend(color, 0xFFFFFFFF.toInt(), 0.32f), animated)
    }

    fun setGlow(a: Int, b: Int, animated: Boolean = true) {
        if (a == colorA && b == colorB) return
        animator?.cancel()
        if (!animated || !UI.animations) {
            colorA = a
            colorB = b
            invalidate()
            return
        }
        val fromA = colorA
        val fromB = colorB
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 460L
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            val t = it.animatedFraction
            colorA = blend(fromA, a, t)
            colorB = blend(fromB, b, t)
            invalidate()
        }
        animator = anim
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        val base: IntArray = when (style) {
            1 -> intArrayOf(0xFF07070B.toInt(), 0xFF040406.toInt(), 0xFF000000.toInt())
            2 -> intArrayOf(0xFF12071A.toInt(), 0xFF080410.toInt(), 0xFF000000.toInt())
            else -> intArrayOf(0xFF101018.toInt(), 0xFF07070C.toInt(), 0xFF000000.toInt())
        }
        paint.shader = LinearGradient(
            0f, 0f, w * 0.3f, h, base,
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        val strength = when (style) {
            1 -> 0.42f
            2 -> 1.45f
            else -> 1f
        }

        paint.shader = RadialGradient(
            w * 0.27f, h * 0.18f, h * 1.25f,
            intArrayOf(
                withAlpha(colorA, (120 * strength).toInt()),
                withAlpha(colorA, (34 * strength).toInt()),
                withAlpha(colorA, 0)
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.92f, h * 1.05f, h * 1.0f,
            intArrayOf(withAlpha(colorB, (95 * strength).toInt()), withAlpha(colorB, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.5f, h * 0.42f, max(w, h) * 0.8f,
            intArrayOf(0, 0, 0xE0000000.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

/** Borda/glow animado usado no foco dos tiles. */
class BorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var progress = 0f
    private var animator: ValueAnimator? = null

    var radiusPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var color: Int = C.ACCENTS[0]
        set(value) {
            field = value
            invalidate()
        }

    fun activate(on: Boolean, animated: Boolean = true) {
        animator?.cancel()
        val target = if (on) 1f else 0f
        if (!animated || !UI.animations) {
            progress = target
            invalidate()
            return
        }
        val anim = ValueAnimator.ofFloat(progress, target)
        anim.duration = if (on) 200L else 140L
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        animator = anim
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0.02f) return
        val inset = dpf(context, 2f)
        if (width <= inset * 2f || height <= inset * 2f) return
        val r = RectF(inset, inset, width - inset, height - inset)
        val rad = if (radiusPx > 0f) radiusPx else dpf(context, 22f)
        for (i in 3 downTo 1) {
            paint.color = withAlpha(color, ((26 * progress) / i).toInt() + 4)
            paint.strokeWidth = dpf(context, 1.5f + i * 2.4f)
            canvas.drawRoundRect(r, rad, rad, paint)
        }
        paint.color = withAlpha(color, (235 * progress).toInt())
        paint.strokeWidth = dpf(context, 2.4f)
        canvas.drawRoundRect(r, rad, rad, paint)
    }
}

/** Brilho diagonal que varre o icone quando ele ganha foco. */
class SheenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pos = 2f
    private var animator: ValueAnimator? = null

    fun sweep() {
        if (!UI.animations) return
        animator?.cancel()
        val anim = ValueAnimator.ofFloat(-1.2f, 1.2f)
        anim.duration = 640L
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        animator = anim
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (pos <= -1.2f || pos > 1.1f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val x = pos * w
        val band = w * 0.55f
        paint.shader = LinearGradient(
            x, 0f, x + band, h,
            intArrayOf(
                withAlpha(0xFFFFFFFF.toInt(), 0),
                withAlpha(0xFFFFFFFF.toInt(), 46),
                withAlpha(0xFFFFFFFF.toInt(), 0)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

/** Icone de app na grade com foco estilo tvOS. */
class TileView(
    context: Context,
    private val boxPx: Int,
    private val withLabel: Boolean
) : FrameLayout(context) {

    private val radius = dpf(context, 23f)
    private val inner = LinearLayout(context)
    private val box = FrameLayout(context)
    private val icon = ImageView(context)
    private val sheen = SheenView(context)
    private val border = BorderView(context)
    private val label = TextView(context)

    var pkg: String = ""
        private set

    var onTileFocus: ((String) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        makeFocusable()
        isClickable = true

        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER_HORIZONTAL
        inner.clipChildren = false
        addView(inner, LayoutParams(boxPx, LP_WRAP))

        box.background = roundedBg(
            withAlpha(0xFFFFFFFF.toInt(), 16),
            radius,
            withAlpha(0xFFFFFFFF.toInt(), 24),
            dpf(context, 1.2f)
        )
        box.roundOutline(radius)
        inner.addView(box, LinearLayout.LayoutParams(boxPx, boxPx))

        val pad = (boxPx * 0.10f).toInt()
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.setPadding(pad, pad, pad, pad)
        box.addView(icon, LayoutParams(LP_MATCH, LP_MATCH))

        sheen.isFocusable = false
        box.addView(sheen, LayoutParams(LP_MATCH, LP_MATCH))

        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        styleText(label, 12.5f, C.TEXT)
        label.alpha = 0.62f
        label.visibility = if (withLabel) View.VISIBLE else View.GONE
        inner.addView(
            label,
            LinearLayout.LayoutParams(boxPx, LP_WRAP).apply { topMargin = dpi(context, 9f) }
        )

        border.radiusPx = radius
        addView(border, LayoutParams(boxPx, boxPx))

        setOnFocusChangeListener { _, focused ->
            focusScale(focused, 1.10f)
            border.activate(focused)
            label.alpha = if (focused) 1f else 0.62f
            if (focused) {
                sheen.sweep()
                onTileFocus?.invoke(pkg)
            }
        }
    }

    fun bind(item: AppItem, repo: AppsRepo, accent: Int) {
        pkg = item.pkg
        border.color = accent
        label.text = item.label
        val bmp = repo.iconBitmap(item.pkg, boxPx)
        if (bmp != null) icon.setImageBitmap(bmp) else icon.setImageResource(R.drawable.ic_app)
    }

    fun setAccent(color: Int) {
        border.color = color
    }
}

/** Card grande do Top Shelf com fundo desfocado do proprio icone. */
class HeroTile(
    context: Context,
    private val wPx: Int,
    private val hPx: Int
) : FrameLayout(context) {

    private val radius = dpf(context, 26f)
    private val back = ImageView(context)
    private val scrim = View(context)
    private val icon = ImageView(context)
    private val title = TextView(context)
    private val sub = TextView(context)
    private val border = BorderView(context)

    var pkg: String = ""
        private set

    var onTileFocus: ((String) -> Unit)? = null

    init {
        makeFocusable()
        isClickable = true
        background = roundedBg(C.CARD, radius, withAlpha(0xFFFFFFFF.toInt(), 22), dpf(context, 1f))
        roundOutline(radius)

        back.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(back, LayoutParams(LP_MATCH, LP_MATCH))

        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xF2000000.toInt(), 0x8A000000.toInt(), 0x00000000)
        )
        addView(scrim, LayoutParams(LP_MATCH, LP_MATCH))

        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        val iconPx = dpi(context, 42f)
        bottom.addView(icon, LinearLayout.LayoutParams(iconPx, iconPx))

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dpi(context, 12f), 0, 0, 0)
        styleText(title, 16f, C.TEXT, true)
        styleText(sub, 11f, withAlpha(C.TEXT, 170))
        col.addView(title, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        col.addView(sub, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        bottom.addView(col, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        val lp = LayoutParams(LP_MATCH, LP_WRAP, Gravity.BOTTOM)
        lp.setMargins(dpi(context, 16f), 0, dpi(context, 16f), dpi(context, 14f))
        addView(bottom, lp)

        border.radiusPx = radius
        addView(border, LayoutParams(LP_MATCH, LP_MATCH))

        setOnFocusChangeListener { _, focused ->
            focusScale(focused, 1.035f, 200)
            border.activate(focused)
            if (focused) onTileFocus?.invoke(pkg)
        }
    }

    fun bind(item: AppItem, repo: AppsRepo, accent: Int, srcPx: Int) {
        pkg = item.pkg
        border.color = accent
        title.text = item.label
        sub.text = "Top Shelf"
        val bmp = repo.iconBitmap(item.pkg, srcPx)
        if (bmp != null) {
            back.setImageBitmap(fakeBlur(bmp, wPx, hPx, 255))
            icon.setImageBitmap(bmp)
        } else {
            back.setImageResource(R.drawable.ic_app)
            icon.setImageResource(R.drawable.ic_app)
        }
    }
}

/** Pilula de selecao de sala. */
class ChipView(context: Context, label: String) : TextView(context) {

    var selected: Boolean = false
        set(value) {
            field = value
            paint()
        }

    init {
        text = label
        makeFocusable()
        isClickable = true
        gravity = Gravity.CENTER
        styleText(this, 12f, C.TEXT)
        letterSpacing = 0.04f
        setPadding(dpi(context, 15f), dpi(context, 7f), dpi(context, 15f), dpi(context, 7f))
        paint()
        setOnFocusChangeListener { _, focused ->
            focusScale(focused, 1.06f, 160)
            paint()
        }
    }

    private fun paint() {
        val focused = isFocused
        background = when {
            selected -> roundedBg(withAlpha(UI.accent, 235), dpf(context, 14f))
            focused -> roundedBg(
                withAlpha(0xFFFFFFFF.toInt(), 26),
                dpf(context, 14f),
                withAlpha(UI.accent, 200),
                dpf(context, 1.2f)
            )
            else -> roundedBg(withAlpha(0xFFFFFFFF.toInt(), 12), dpf(context, 14f))
        }
        setTextColor(
            when {
                selected -> 0xFF0B0B10.toInt()
                focused -> C.TEXT
                else -> C.TEXT_DIM
            }
        )
        typeface = Typeface.create(
            "sans-serif",
            if (selected || focused) Typeface.BOLD else Typeface.NORMAL
        )
    }
}

/** Linha de ajuste: titulo + valor, com destaque no foco. */
class SettingRow(
    context: Context,
    title: String,
    subtitle: String?,
    value: String
) : LinearLayout(context) {

    private val valueView = TextView(context)
    private val radius = dpf(context, 16f)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        makeFocusable()
        isClickable = true
        setPadding(dpi(context, 18f), dpi(context, 13f), dpi(context, 18f), dpi(context, 13f))
        background = roundedBg(withAlpha(0xFFFFFFFF.toInt(), 10), radius)

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        val titleView = TextView(context)
        styleText(titleView, 15f, C.TEXT)
        titleView.text = title
        col.addView(titleView, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        if (subtitle != null) {
            val subView = TextView(context)
            styleText(subView, 11.5f, C.TEXT_FAINT)
            subView.text = subtitle
            col.addView(
                subView,
                LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { topMargin = dpi(context, 3f) }
            )
        }
        addView(col, LinearLayout.LayoutParams(0, LP_WRAP, 1f))

        styleText(valueView, 13f, C.TEXT_DIM)
        valueView.text = value
        valueView.gravity = Gravity.END
        addView(valueView, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        setOnFocusChangeListener { _, focused ->
            refreshAccent()
            valueView.setTextColor(if (focused) C.TEXT else C.TEXT_DIM)
            focusScale(focused, 1.012f, 130)
        }
    }

    fun setValue(v: String) {
        valueView.text = v
    }

    /** Redesenha o fundo (usado quando a cor de destaque muda com a linha em foco). */
    fun refreshAccent() {
        val focused = isFocused
        background = roundedBg(
            if (focused) withAlpha(UI.accent, 46) else withAlpha(0xFFFFFFFF.toInt(), 10),
            radius,
            if (focused) withAlpha(UI.accent, 185) else 0,
            if (focused) dpf(context, 1.2f) else 0f
        )
    }
}

/** Item da barra lateral de ajustes. */
class NavRow(context: Context, label: String) : LinearLayout(context) {

    private val bar = View(context)
    private val tv = TextView(context)

    var selected: Boolean = false
        set(value) {
            field = value
            paint()
        }

    /** Chamado quando a linha ganha foco (usado para trocar de secao). */
    var onNavFocus: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        makeFocusable()
        isClickable = true
        setPadding(dpi(context, 12f), dpi(context, 12f), dpi(context, 12f), dpi(context, 12f))

        bar.setBackgroundColor(UI.accent)
        addView(bar, LinearLayout.LayoutParams(dpi(context, 3f), dpi(context, 18f)))

        styleText(tv, 14.5f, C.TEXT_DIM)
        tv.text = label
        addView(tv, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { leftMargin = dpi(context, 12f) })

        paint()
        setOnFocusChangeListener { _, focused ->
            focusScale(focused, 1.02f, 120)
            paint()
            if (focused) onNavFocus?.invoke()
        }
    }

    fun setLabel(t: String) {
        tv.text = t
    }

    private fun paint() {
        bar.alpha = if (selected) 1f else 0f
        tv.setTextColor(if (selected || isFocused) C.TEXT else C.TEXT_DIM)
        tv.typeface = Typeface.create(
            "sans-serif",
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
        background = if (isFocused) {
            roundedBg(withAlpha(0xFFFFFFFF.toInt(), 16), dpf(context, 12f))
        } else {
            null
        }
    }
}
