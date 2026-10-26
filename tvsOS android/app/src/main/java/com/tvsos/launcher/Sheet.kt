package com.tvsos.launcher

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Painel modal de opcoes, navegavel apenas com o controle remoto. */
class Sheet(context: Context) : FrameLayout(context) {

    private val card = LinearLayout(context)
    private val titleView = TextView(context)
    private val bodyView = TextView(context)
    private val items = LinearLayout(context)

    var onClose: (() -> Unit)? = null

    init {
        setBackgroundColor(0xB8000000.toInt())
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener { dismiss() }

        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.background = roundedBg(
            C.CARD,
            dpf(context, 26f),
            withAlpha(0xFFFFFFFF.toInt(), 26),
            dpf(context, 1f)
        )
        card.setPadding(
            dpi(context, 26f), dpi(context, 22f), dpi(context, 26f), dpi(context, 14f)
        )
        addView(card, LayoutParams(dpi(context, 470f), LP_WRAP, Gravity.CENTER))

        titleView.visibility = View.GONE
        styleText(titleView, 17f, C.TEXT, true)
        card.addView(titleView, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        bodyView.visibility = View.GONE
        styleText(bodyView, 12.5f, C.TEXT_DIM)
        bodyView.setLineSpacing(dpf(context, 3f), 1f)
        card.addView(
            bodyView,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(context, 8f) }
        )

        card.addView(
            items,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(context, 12f) }
        )
    }

    fun title(t: String): Sheet {
        titleView.text = t
        titleView.visibility = View.VISIBLE
        return this
    }

    fun body(t: String): Sheet {
        bodyView.text = t
        bodyView.visibility = View.VISIBLE
        return this
    }

    fun item(label: String, action: () -> Unit): Sheet {
        val row = SheetItem(context, label)
        row.setOnClickListener { action() }
        items.addView(
            row,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(context, 4f) }
        )
        return this
    }

    fun open(parent: ViewGroup) {
        parent.addView(this, LayoutParams(LP_MATCH, LP_MATCH))
        post { focusFirst() }
    }

    fun focusFirst() {
        if (items.childCount == 0) return
        items.getChildAt(0).requestFocus()
    }

    fun dismiss() {
        val p = parent as? ViewGroup
        p?.removeView(this)
        onClose?.invoke()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

/** Linha de opcao dentro de um Sheet. */
class SheetItem(context: Context, label: String) : TextView(context) {

    init {
        text = label
        makeFocusable()
        isClickable = true
        gravity = Gravity.CENTER_VERTICAL
        styleText(this, 14.5f, C.TEXT)
        setPadding(dpi(context, 14f), dpi(context, 12f), dpi(context, 14f), dpi(context, 12f))
        setOnFocusChangeListener { _, focused ->
            background = if (focused) {
                roundedBg(
                    withAlpha(UI.accent, 60),
                    dpf(context, 12f),
                    withAlpha(UI.accent, 170),
                    dpf(context, 1f)
                )
            } else {
                null
            }
            setTextColor(if (focused) C.TEXT else C.TEXT_DIM)
            focusScale(focused, 1.01f, 120)
        }
    }
}

/** Caixa de texto com teclado, usada para renomear salas. */
class PromptSheet(
    context: Context,
    title: String,
    initial: String,
    private val onSave: (String) -> Unit
) : FrameLayout(context) {

    private val input = EditText(context)

    var onClose: (() -> Unit)? = null

    init {
        setBackgroundColor(0xB8000000.toInt())
        isClickable = true
        setOnClickListener { dismiss() }

        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.background = roundedBg(
            C.CARD,
            dpf(context, 26f),
            withAlpha(0xFFFFFFFF.toInt(), 26),
            dpf(context, 1f)
        )
        card.setPadding(
            dpi(context, 26f), dpi(context, 22f), dpi(context, 26f), dpi(context, 18f)
        )
        addView(card, LayoutParams(dpi(context, 520f), LP_WRAP, Gravity.CENTER))

        val t = TextView(context)
        t.text = title
        styleText(t, 16f, C.TEXT, true)
        card.addView(t, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        input.setText(initial)
        input.setSingleLine(true)
        input.hint = "Nome"
        input.setTextColor(C.TEXT)
        input.setHintTextColor(C.TEXT_FAINT)
        input.background = roundedBg(
            withAlpha(0xFFFFFFFF.toInt(), 22),
            dpf(context, 14f),
            withAlpha(0xFFFFFFFF.toInt(), 40),
            dpf(context, 1f)
        )
        input.setPadding(
            dpi(context, 14f), dpi(context, 12f), dpi(context, 14f), dpi(context, 12f)
        )
        styleText(input, 15f, C.TEXT)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addView(
            input,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(context, 16f) }
        )
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else {
                false
            }
        }

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val cancel = SheetItem(context, "Cancelar")
        val ok = SheetItem(context, "Salvar")
        cancel.setOnClickListener { dismiss() }
        ok.setOnClickListener { save() }
        row.addView(
            cancel,
            LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { rightMargin = dpi(context, 8f) }
        )
        row.addView(ok, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        card.addView(
            row,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(context, 14f) }
        )
    }

    private fun save() {
        val v = input.text?.toString()?.trim().orEmpty()
        if (v.isNotEmpty()) onSave(v)
        dismiss()
    }

    fun open(parent: ViewGroup) {
        parent.addView(this, LayoutParams(LP_MATCH, LP_MATCH))
        post {
            input.requestFocus()
            val n = input.text?.length ?: 0
            input.setSelection(n)
        }
    }

    fun dismiss() {
        val p = parent as? ViewGroup
        p?.removeView(this)
        onClose?.invoke()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
