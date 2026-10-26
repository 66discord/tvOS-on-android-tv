package com.tvsos.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Home do tvsOS: grade de apps com foco estilo tvOS, salas personalizaveis,
 * Top Shelf e screensaver.
 */
class HomeActivity : Activity() {

    private lateinit var store: Store
    private lateinit var repo: AppsRepo
    private lateinit var root: FrameLayout
    private lateinit var bg: BackgroundView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView

    private var clockText: TextView? = null
    private var dateText: TextView? = null
    private var greetingText: TextView? = null

    private val tiles = mutableListOf<View>()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var apps: List<AppItem> = emptyList()
    private var sheet: Sheet? = null
    private var saver: View? = null
    private var saverClock: TextView? = null
    private var focusedTag: String? = null
    private var fallbackFocus: View? = null
    private var lastInteraction = System.currentTimeMillis()
    private var lastBack = 0L
    private var generation = 0

    private val timeFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        repo = AppsRepo(this)
        setupWindow()

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())

        bg = BackgroundView(this)
        root.addView(bg, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))

        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        content.setPadding(
            dpi(this, 52f), dpi(this, 34f), dpi(this, 52f), dpi(this, 46f)
        )
        scroll.addView(content, FrameLayout.LayoutParams(LP_MATCH, LP_WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))

        setContentView(root)
        lastInteraction = System.currentTimeMillis()
    }

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onResume() {
        super.onResume()
        UI.accent = store.accent
        UI.animations = store.animations
        bg.style = store.bgStyle
        bg.setAccent(store.accent)
        refresh(null)
        renderClock()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
        ui.removeCallbacks(idleCheck)
        ui.postDelayed(idleCheck, 10_000L)
        lastInteraction = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(idleCheck)
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        io.shutdown()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        refresh(null)
    }

    // ---------------------------------------------------------------- dados

    private fun refresh(preserve: String?) {
        generation++
        val token = generation
        val cols = store.columns
        io.execute {
            val loaded = repo.load()
            repo.preload(loaded, tilePx(cols))
            ui.post {
                if (token != generation) return@post
                apps = loaded
                build(preserve)
            }
        }
    }

    private fun rooms(): MutableList<Room> = store.rooms()

    private fun visibleApps(): List<AppItem> {
        val hidden = store.hidden()
        val active = rooms().firstOrNull { it.id == store.activeRoom }
        val allowed: Set<String>? =
            if (active == null || active.id == Room.ALL) null else active.apps.toSet()
        return apps.filter { item ->
            item.pkg !in hidden && (allowed == null || item.pkg in allowed)
        }
    }

    private fun tilePx(cols: Int): Int {
        val screen = resources.displayMetrics.widthPixels
        val side = dpi(this, 52f)
        val trailing = dpi(this, 40f)
        val gap = dpi(this, 16f)
        val available = screen - side * 2 - trailing
        val raw = (available - gap * (cols - 1)) / cols
        return raw.coerceIn(dpi(this, 72f), dpi(this, 186f))
    }

    // ------------------------------------------------------------------ UI

    private fun build(preserve: String?) {
        tiles.clear()
        content.removeAllViews()
        clockText = null
        dateText = null
        greetingText = null
        fallbackFocus = null

        buildHeader()
        if (store.topShelf) buildShelf()
        buildGrid()
        buildFooter()

        val keep = preserve ?: focusedTag
        content.post {
            val target: View? = if (keep != null) content.findViewWithTag<View>(keep) else null
            val focus = target ?: tiles.firstOrNull() ?: fallbackFocus
            focus?.requestFocus()
            focusedTag = focus?.tag as? String
        }
    }

    private fun buildHeader() {
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.clipChildren = false

        val brand = LinearLayout(this)
        brand.orientation = LinearLayout.VERTICAL
        val name = TextView(this)
        name.text = "tvsOS"
        styleText(name, 17f, C.TEXT, true)
        name.letterSpacing = 0.16f
        brand.addView(name, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        greetingText = TextView(this).also {
            styleText(it, 10.5f, store.accent, true)
            it.letterSpacing = 0.12f
            brand.addView(it, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        }
        header.addView(brand, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        val chipsScroll = HorizontalScrollView(this)
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.overScrollMode = View.OVER_SCROLL_NEVER
        chipsScroll.isFocusable = false
        chipsScroll.clipChildren = false

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.gravity = Gravity.CENTER_VERTICAL
        chips.clipChildren = false
        chips.setPadding(dpi(this, 6f), 0, dpi(this, 6f), 0)

        for (room in rooms()) {
            val chip = ChipView(this, room.name)
            chip.selected = room.id == store.activeRoom
            chip.tag = "chip:${room.id}"
            chip.setOnClickListener { selectRoom(room.id) }
            chips.addView(
                chip,
                LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { rightMargin = dpi(this@HomeActivity, 8f) }
            )
        }
        chipsScroll.addView(chips, FrameLayout.LayoutParams(LP_WRAP, LP_WRAP))
        header.addView(
            chipsScroll,
            LinearLayout.LayoutParams(0, LP_WRAP, 1f).apply { leftMargin = dpi(this@HomeActivity, 22f) }
        )

        val clockCol = LinearLayout(this)
        clockCol.orientation = LinearLayout.VERTICAL
        clockCol.gravity = Gravity.END

        clockText = TextView(this).also {
            styleText(it, 24f, C.TEXT, true)
            it.letterSpacing = 0.02f
            it.gravity = Gravity.END
            clockCol.addView(it, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        }
        dateText = TextView(this).also {
            styleText(it, 10.5f, C.TEXT_DIM)
            it.gravity = Gravity.END
            clockCol.addView(it, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))
        }
        clockCol.visibility = if (store.showClock) View.VISIBLE else View.GONE
        header.addView(clockCol, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        val settings = TextView(this)
        settings.text = "Ajustes"
        styleText(settings, 12.5f, C.TEXT)
        settings.gravity = Gravity.CENTER
        settings.setPadding(dpi(this, 16f), dpi(this, 9f), dpi(this, 16f), dpi(this, 9f))
        settings.background = roundedBg(withAlpha(0xFFFFFFFF.toInt(), 14), dpf(this, 12f))
        settings.makeFocusable()
        settings.isClickable = true
        settings.setOnFocusChangeListener { v, focused ->
            v.focusScale(focused, 1.05f, 140)
            v.background = if (focused) {
                roundedBg(
                    withAlpha(UI.accent, 210),
                    dpf(this, 12f),
                    withAlpha(UI.accent, 255),
                    dpf(this, 1f)
                )
            } else {
                roundedBg(withAlpha(0xFFFFFFFF.toInt(), 14), dpf(this, 12f))
            }
        }
        settings.setOnClickListener { openSettings() }
        fallbackFocus = settings
        header.addView(
            settings,
            LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { leftMargin = dpi(this@HomeActivity, 18f) }
        )

        content.addView(header, LinearLayout.LayoutParams(LP_MATCH, LP_WRAP))
    }

    private fun buildShelf() {
        val pinned = store.pinned().filter { pkg -> apps.any { it.pkg == pkg } }
        if (pinned.isEmpty()) return

        content.addView(
            sectionLabel(this, "Em destaque").also { it.letterSpacing = 0.14f },
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 26f) }
        )

        val strip = HorizontalScrollView(this)
        strip.isHorizontalScrollBarEnabled = false
        strip.overScrollMode = View.OVER_SCROLL_NEVER
        strip.isFocusable = false
        strip.clipChildren = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.setPadding(0, dpi(this, 10f), dpi(this, 30f), dpi(this, 6f))

        val heroW = dpi(this, 318f)
        val heroH = dpi(this, 176f)
        val gap = dpi(this, 16f)
        val size = tilePx(store.columns)

        for (pkg in pinned.take(6)) {
            val item = apps.firstOrNull { it.pkg == pkg } ?: continue
            val hero = HeroTile(this, heroW, heroH)
            hero.bind(item, repo, store.accent, size)
            hero.tag = pkg
            hero.onTileFocus = { p ->
                focusedTag = p
                bg.setGlow(repo.dominant(p), store.accent)
            }
            hero.setOnClickListener { launch(item.pkg) }
            hero.setOnLongClickListener {
                showOptions(item)
                true
            }
            row.addView(hero, LinearLayout.LayoutParams(heroW, heroH).apply { rightMargin = gap })
            tiles.add(hero)
        }
        strip.addView(row, FrameLayout.LayoutParams(LP_WRAP, LP_WRAP))
        content.addView(strip, LinearLayout.LayoutParams(LP_MATCH, LP_WRAP))
    }

    private fun buildGrid() {
        val items = visibleApps()
        content.addView(
            sectionLabel(this, roomName()),
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 28f) }
        )

        if (items.isEmpty()) {
            content.addView(
                noteText(
                    this,
                    "Nenhum app nesta sala. Abra Ajustes > Salas para escolher quais apps aparecem aqui."
                ),
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 10f) }
            )
            return
        }

        val cols = store.columns
        val size = tilePx(cols)
        val gap = dpi(this, 16f)

        for (chunk in items.chunked(cols)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.clipChildren = false
            row.setPadding(0, dpi(this, 6f), 0, dpi(this, 6f))
            for (item in chunk) {
                val tile = TileView(this, size, store.showLabels)
                tile.bind(item, repo, store.accent)
                tile.tag = item.pkg
                tile.onTileFocus = { p ->
                    focusedTag = p
                    bg.setGlow(repo.dominant(p), store.accent)
                }
                tile.setOnClickListener { launch(item.pkg) }
                tile.setOnLongClickListener {
                    showOptions(item)
                    true
                }
                row.addView(tile, LinearLayout.LayoutParams(size, LP_WRAP).apply { rightMargin = gap })
                tiles.add(tile)
            }
            content.addView(row, LinearLayout.LayoutParams(LP_MATCH, LP_WRAP))
        }
    }

    private fun buildFooter() {
        val hint = noteText(
            this,
            "OK abrir   ·   segure OK para opções   ·   ≡ ajustes   ·   voltar sai do tvsOS"
        )
        hint.setTextColor(C.TEXT_FAINT)
        content.addView(
            hint,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 34f) }
        )
    }

    private fun roomName(): String =
        rooms().firstOrNull { it.id == store.activeRoom }?.name ?: "Todos"

    private fun selectRoom(id: String) {
        if (store.activeRoom == id) return
        store.activeRoom = id
        build(null)
    }

    // ------------------------------------------------------------- acoes

    private fun launch(pkg: String) {
        closeSheet()
        val intent = repo.launchIntent(pkg)
        if (intent == null) {
            toast("Não foi possível abrir este app")
            return
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Não foi possível abrir este app")
        }
    }

    private fun showOptions(item: AppItem) {
        closeSheet()
        val pinned = store.pinned()
        val isPinned = pinned.contains(item.pkg)

        val s = Sheet(this)
            .title(item.label)
            .body(item.pkg)
            .item("Abrir") { launch(item.pkg) }
            .item(if (isPinned) "Remover do Top Shelf" else "Fixar no Top Shelf") {
                if (isPinned) pinned.remove(item.pkg) else pinned.add(item.pkg)
                store.setPinned(pinned)
                closeSheet()
                refresh(item.pkg)
            }
            .item("Ocultar da Home") {
                val hidden = store.hidden()
                hidden.add(item.pkg)
                store.setHidden(hidden)
                closeSheet()
                refresh(null)
            }
            .item("Informações do app") {
                closeSheet()
                repo.openInfo(item.pkg)
            }
            .item("Fechar") { closeSheet() }

        s.onClose = { finishSheet(s) }
        sheet = s
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        s.open(root)
    }

    private fun finishSheet(s: Sheet) {
        if (sheet === s) sheet = null
        content.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        val parent = s.parent as? ViewGroup
        parent?.removeView(s)
        restoreFocus()
    }

    private fun closeSheet() {
        val s = sheet ?: return
        sheet = null
        content.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        val parent = s.parent as? ViewGroup
        parent?.removeView(s)
        restoreFocus()
    }

    private fun restoreFocus() {
        val tag = focusedTag
        content.post {
            var v: View? = null
            if (tag != null) v = content.findViewWithTag<View>(tag)
            if (v == null) v = tiles.firstOrNull()
            v?.requestFocus()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, SettingsActivity::class.java))
        } catch (e: Exception) {
            toast("Não foi possível abrir os ajustes")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------- relogio

    private val ticker = object : Runnable {
        override fun run() {
            renderClock()
            ui.postDelayed(this, 10_000L)
        }
    }

    private val idleCheck = object : Runnable {
        override fun run() {
            val mins = store.saverMinutes
            val idle = System.currentTimeMillis() - lastInteraction
            if (saver == null && sheet == null && mins > 0 && idle > mins * 60_000L) {
                showSaver()
            }
            ui.postDelayed(this, 10_000L)
        }
    }

    private fun renderClock() {
        val now = Date()
        clockText?.text = timeFmt.format(now)
        dateText?.text = prettyDate(now)
        greetingText?.text = greeting(now)
        saverClock?.text = timeFmt.format(now)
    }

    private fun prettyDate(d: Date): String {
        val raw = dateFmt.format(d)
        if (raw.isEmpty()) return ""
        return raw.substring(0, 1).uppercase(Locale("pt", "BR")) + raw.substring(1)
    }

    private fun greeting(d: Date): String {
        val cal = Calendar.getInstance()
        cal.time = d
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            h in 5..11 -> "BOM DIA"
            h in 12..17 -> "BOA TARDE"
            else -> "BOA NOITE"
        }
    }

    // ---------------------------------------------------------- screensaver

    private fun showSaver() {
        if (saver != null) return
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xFF000000.toInt())

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.clipChildren = false

        val mark = TextView(this)
        styleText(mark, 11f, withAlpha(UI.accent, 210), true)
        mark.letterSpacing = 0.42f
        mark.text = "TVSOS"
        col.addView(mark, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        val clock = TextView(this)
        styleText(clock, 104f, C.TEXT, false)
        clock.text = timeFmt.format(Date())
        col.addView(
            clock,
            LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 8f) }
        )

        val date = TextView(this)
        styleText(date, 16f, withAlpha(C.TEXT, 150))
        date.text = prettyDate(Date())
        col.addView(
            date,
            LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { topMargin = dpi(this@HomeActivity, 6f) }
        )

        overlay.addView(col, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))
        overlay.alpha = 0f
        root.addView(overlay, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))
        overlay.animate().alpha(1f).setDuration(420L).start()

        saver = overlay
        saverClock = clock
    }

    private fun hideSaver() {
        val v = saver ?: return
        saver = null
        saverClock = null
        root.removeView(v)
        lastInteraction = System.currentTimeMillis()
    }

    // ---------------------------------------------------------------- teclas

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInteraction = System.currentTimeMillis()
            if (saver != null) {
                hideSaver()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteraction = System.currentTimeMillis()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (sheet == null) {
                openSettings()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (sheet != null) {
            closeSheet()
            return
        }
        if (saver != null) {
            hideSaver()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2200L) {
            super.onBackPressed()
            return
        }
        lastBack = now
        toast("Pressione voltar de novo para sair do tvsOS")
    }
}
