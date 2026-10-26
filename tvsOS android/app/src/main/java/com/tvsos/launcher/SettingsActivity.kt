package com.tvsos.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Ajustes do tvsOS: aparencia, salas, apps, top shelf e sistema. */
class SettingsActivity : Activity() {

    private lateinit var store: Store
    private lateinit var repo: AppsRepo
    private lateinit var root: FrameLayout
    private lateinit var bg: BackgroundView
    private lateinit var body: LinearLayout
    private lateinit var pane: LinearLayout
    private lateinit var paneScroll: ScrollView
    private lateinit var navHost: LinearLayout

    private val navItems = mutableListOf<NavRow>()
    private val sections = arrayOf("Aparência", "Salas", "Apps", "Top Shelf", "Sistema", "Sobre")

    private var section = -1
    private var editorRoomId: String? = null
    private var rooms: MutableList<Room> = mutableListOf()

    private var apps: List<AppItem> = emptyList()
    private var appsLoaded = false
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var sheet: Sheet? = null
    private var prompt: PromptSheet? = null
    private var lastFocused: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        repo = AppsRepo(this)
        UI.accent = store.accent
        UI.animations = store.animations
        setupWindow()

        rooms = store.rooms()

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        bg = BackgroundView(this)
        bg.style = store.bgStyle
        bg.setAccent(store.accent)
        root.addView(bg, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))

        body = LinearLayout(this)
        body.orientation = LinearLayout.HORIZONTAL
        body.setPadding(
            dpi(this, 44f), dpi(this, 38f), dpi(this, 40f), dpi(this, 34f)
        )

        val side = LinearLayout(this)
        side.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "Ajustes"
        styleText(title, 25f, C.TEXT, true)
        side.addView(title, LinearLayout.LayoutParams(LP_WRAP, LP_WRAP))

        val sub = TextView(this)
        sub.text = "TVSOS"
        styleText(sub, 10.5f, C.TEXT_FAINT, true)
        sub.letterSpacing = 0.22f
        side.addView(
            sub,
            LinearLayout.LayoutParams(LP_WRAP, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 4f) }
        )
        side.addView(View(this), LinearLayout.LayoutParams(LP_MATCH, dpi(this@SettingsActivity, 26f)))

        navHost = LinearLayout(this)
        navHost.orientation = LinearLayout.VERTICAL
        for (i in sections.indices) {
            val n = NavRow(this, sections[i])
            n.setOnClickListener { select(i) }
            n.onNavFocus = { select(i) }
            navHost.addView(
                n,
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 2f) }
            )
            navItems.add(n)
        }
        side.addView(navHost, LinearLayout.LayoutParams(LP_MATCH, LP_WRAP))
        body.addView(side, LinearLayout.LayoutParams(dpi(this, 226f), LP_MATCH))

        val divider = View(this)
        divider.setBackgroundColor(withAlpha(0xFFFFFFFF.toInt(), 16))
        body.addView(
            divider,
            LinearLayout.LayoutParams(dpi(this, 1f), LP_MATCH).apply {
                leftMargin = dpi(this@SettingsActivity, 24f)
                rightMargin = dpi(this@SettingsActivity, 28f)
            }
        )

        paneScroll = ScrollView(this)
        paneScroll.isVerticalScrollBarEnabled = false
        paneScroll.overScrollMode = View.OVER_SCROLL_NEVER
        pane = LinearLayout(this)
        pane.orientation = LinearLayout.VERTICAL
        pane.clipChildren = false
        pane.setPadding(0, 0, dpi(this, 26f), dpi(this, 10f))
        paneScroll.addView(pane, FrameLayout.LayoutParams(LP_MATCH, LP_WRAP))
        body.addView(paneScroll, LinearLayout.LayoutParams(0, LP_MATCH, 1f))

        root.addView(body, FrameLayout.LayoutParams(LP_MATCH, LP_MATCH))
        setContentView(root)

        select(0)
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
        rooms = store.rooms()
        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        io.shutdown()
    }

    private fun loadApps() {
        io.execute {
            val loaded = repo.load()
            repo.preload(loaded, dpi(this, 150f))
            ui.post {
                val changed = !appsLoaded ||
                    loaded.size != apps.size ||
                    loaded.zip(apps).any { (a, b) -> a.pkg != b.pkg }
                apps = loaded
                appsLoaded = true
                if (changed && section >= 0) renderPane()
            }
        }
    }

    // ------------------------------------------------------------- estrutura

    private fun select(index: Int) {
        if (index == section && pane.childCount > 0) return
        section = index
        editorRoomId = null
        for (i in navItems.indices) navItems[i].selected = i == index
        renderPane()
    }

    private fun renderPane() {
        pane.removeAllViews()
        if (section < 0) return
        when (section) {
            0 -> sectionAppearance()
            1 -> if (editorRoomId != null) sectionRoomEditor() else sectionRooms()
            2 -> sectionApps()
            3 -> sectionPinned()
            4 -> sectionSystem()
            else -> sectionAbout()
        }
        paneScroll.scrollTo(0, 0)
        pane.post {
            if (currentFocus == null) {
                for (i in 0 until pane.childCount) {
                    val c = pane.getChildAt(i)
                    if (c.isFocusable) {
                        c.requestFocus()
                        return@post
                    }
                }
                navItems.getOrNull(0)?.requestFocus()
            }
        }
    }

    private fun header(title: String, note: String? = null) {
        val t = TextView(this)
        t.text = title
        styleText(t, 23f, C.TEXT, true)
        pane.addView(t, LinearLayout.LayoutParams(LP_MATCH, LP_WRAP))
        if (note != null) {
            pane.addView(
                noteText(this, note),
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 8f) }
            )
        }
        pane.addView(View(this), LinearLayout.LayoutParams(LP_MATCH, dpi(this@SettingsActivity, 16f)))
    }

    private fun group(title: String) {
        pane.addView(
            sectionLabel(this, title),
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 20f) }
        )
    }

    private fun row(
        title: String,
        value: String,
        subtitle: String? = null,
        onClick: (SettingRow) -> Unit
    ): SettingRow {
        val r = SettingRow(this, title, subtitle, value)
        r.setOnClickListener { onClick(r) }
        pane.addView(
            r,
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 8f) }
        )
        return r
    }

    // -------------------------------------------------------------- secoes

    private fun sectionAppearance() {
        header("Aparência", "Cor, fundo, grade e movimento. As mudanças valem na hora.")
        group("Cores")
        row("Cor de destaque", C.ACCENT_NAMES[store.accentIndex]) { r ->
            store.accentIndex = (store.accentIndex + 1) % C.ACCENTS.size
            r.setValue(C.ACCENT_NAMES[store.accentIndex])
            UI.accent = store.accent
            bg.setAccent(store.accent)
            repaintRows()
        }
        row("Fundo", STYLES[store.bgStyle]) { r ->
            store.bgStyle = (store.bgStyle + 1) % STYLES.size
            r.setValue(STYLES[store.bgStyle])
            bg.style = store.bgStyle
            bg.setAccent(store.accent)
        }

        group("Grade")
        row("Colunas por linha", store.columns.toString()) { r ->
            val opts = intArrayOf(4, 5, 6)
            var i = opts.indexOf(store.columns)
            if (i < 0) i = 1
            val next = opts[(i + 1) % opts.size]
            store.columns = next
            r.setValue(next.toString())
        }
        row("Nomes dos apps", if (store.showLabels) "Mostrar" else "Ocultar") { r ->
            store.showLabels = !store.showLabels
            r.setValue(if (store.showLabels) "Mostrar" else "Ocultar")
        }
        row("Relógio na Home", if (store.showClock) "Mostrar" else "Ocultar") { r ->
            store.showClock = !store.showClock
            r.setValue(if (store.showClock) "Mostrar" else "Ocultar")
        }

        group("Top Shelf")
        row("Cards em destaque", if (store.topShelf) "Ativado" else "Desativado") { r ->
            store.topShelf = !store.topShelf
            r.setValue(if (store.topShelf) "Ativado" else "Desativado")
        }

        group("Movimento")
        row("Animações", if (store.animations) "Ativadas" else "Desativadas") { r ->
            store.animations = !store.animations
            UI.animations = store.animations
            r.setValue(if (store.animations) "Ativadas" else "Desativadas")
        }
        row("Screensaver", saverLabel(store.saverMinutes)) { r ->
            val opts = intArrayOf(0, 1, 3, 5, 10)
            var i = opts.indexOf(store.saverMinutes)
            if (i < 0) i = 3
            store.saverMinutes = opts[(i + 1) % opts.size]
            r.setValue(saverLabel(store.saverMinutes))
        }
    }

    private fun sectionRooms() {
        header(
            "Salas",
            "Cada sala mostra só os apps que você escolher. As pílulas no topo da Home trocam de sala."
        )
        for (room in rooms) {
            val active = room.id == store.activeRoom
            val sub = if (room.id == Room.ALL) {
                "Todos os apps instalados"
            } else {
                if (room.apps.isEmpty()) "Sem filtro (mostra tudo)" else "${room.apps.size} apps escolhidos"
            }
            row(room.name, if (active) "Ativa" else "Ativar", sub) {
                store.activeRoom = room.id
                renderPane()
                restoreFocusIndex()
            }
        }

        group("Gerenciar")
        row("Nova sala", "+", "Cria uma sala vazia e já abre o editor.") { createRoom() }
        val activeRoom = rooms.firstOrNull { it.id == store.activeRoom }
        if (activeRoom != null && activeRoom.id != Room.ALL) {
            row("Editar sala ativa", activeRoom.name, "Renomear e escolher os apps.") {
                editorRoomId = activeRoom.id
                renderPane()
            }
            row("Excluir sala ativa", "Excluir", "Os apps continuam instalados.") {
                confirmDelete(activeRoom)
            }
        }
    }

    private fun sectionRoomEditor() {
        val room = rooms.firstOrNull { it.id == editorRoomId }
        if (room == null) {
            editorRoomId = null
            sectionRooms()
            return
        }
        header(room.name, "Toque OK em um app para incluir ou tirar da sala. Sala sem apps mostra todos.")

        row("← Voltar para Salas", "") {
            editorRoomId = null
            renderPane()
        }
        row("Renomear sala", "Editar") { promptName(room) }
        row("Definir como ativa", if (store.activeRoom == room.id) "Ativa" else "Definir") {
            store.activeRoom = room.id
            renderPane()
        }
        row("Excluir sala", "Excluir") { confirmDelete(room) }

        group("Apps desta sala")
        if (apps.isEmpty()) {
            pane.addView(
                noteText(this, "Carregando lista de apps..."),
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 10f) }
            )
            return
        }
        for (app in apps) {
            val included = room.apps.contains(app.pkg)
            row(app.label, if (included) "Incluída" else "Fora") { r ->
                if (room.apps.contains(app.pkg)) {
                    room.apps.remove(app.pkg)
                } else {
                    room.apps.add(app.pkg)
                }
                store.setRooms(rooms)
                r.setValue(if (room.apps.contains(app.pkg)) "Incluída" else "Fora")
            }
        }
    }

    private fun sectionApps() {
        header("Apps", "OK mostra ou oculta um app da Home. Nada é desinstalado.")
        if (apps.isEmpty()) {
            pane.addView(
                noteText(this, "Carregando lista de apps..."),
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 10f) }
            )
            return
        }
        group("Todos os apps (${apps.size})")
        for (app in apps) {
            val hidden = store.hidden().contains(app.pkg)
            row(app.label, if (hidden) "Oculto" else "Visível") { r ->
                val h = store.hidden()
                if (h.contains(app.pkg)) h.remove(app.pkg) else h.add(app.pkg)
                store.setHidden(h)
                r.setValue(if (h.contains(app.pkg)) "Oculto" else "Visível")
            }
        }
    }

    private fun sectionPinned() {
        header(
            "Top Shelf",
            "Até 6 apps viram cards grandes no topo da Home. Sem nenhum escolhido, a faixa não aparece."
        )
        if (apps.isEmpty()) {
            pane.addView(
                noteText(this, "Carregando lista de apps..."),
                LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 10f) }
            )
            return
        }
        for (app in apps) {
            val pinned = store.pinned().contains(app.pkg)
            row(app.label, if (pinned) "Fixado" else "Livre") { r ->
                val p = store.pinned()
                if (p.contains(app.pkg)) {
                    p.remove(app.pkg)
                } else if (p.size < 6) {
                    p.add(app.pkg)
                } else {
                    toast("Máximo de 6 apps no Top Shelf")
                    return@row
                }
                store.setPinned(p)
                r.setValue(if (p.contains(app.pkg)) "Fixado" else "Livre")
            }
        }
    }

    private fun sectionSystem() {
        header("Sistema", "Integração com o Fire TV / Android TV.")
        row(
            "Definir tvsOS como Home",
            "Abrir",
            "Abre a tela de app inicial do sistema. No Fire OS a Amazon pode bloquear a troca."
        ) { openHomeSettings() }
        row("Configurações do sistema", "Abrir") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                toast("Indisponível neste aparelho")
            }
        }
        row("Recarregar lista de apps", "Agora") {
            loadApps()
            toast("Lista atualizada")
        }
        row("Restaurar padrões", "Resetar", "Apaga salas, destaques e preferências.") {
            confirmReset()
        }

        group("Atalhos do controle")
        pane.addView(
            noteText(
                this,
                "≡  abre estes ajustes\n" +
                    "OK  abre o app em foco\n" +
                    "segure OK  opções do app\n" +
                    "◁ ▷ △ ▽  navegação\n" +
                    "voltar  fecha painel ou volta para a Home"
            ),
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 10f) }
        )
    }

    private fun sectionAbout() {
        header("Sobre")
        pane.addView(
            noteText(
                this,
                "tvsOS 1.0 · launcher independente inspirado no visual do tvOS.\n\n" +
                    "Não é um sistema da Apple, não é uma ROM e não contém código ou assets da Apple. " +
                    "É um app Android (APK) que roda por cima do Fire OS / Android TV.\n\n" +
                    "Tudo foi desenhado para controle remoto: sem toque, só D-pad."
            ),
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP)
        )
        group("O que dá para personalizar")
        pane.addView(
            noteText(
                this,
                "• Cor de destaque e estilo do fundo\n" +
                    "• Colunas da grade, nomes e relógio\n" +
                    "• Salas com conjunto próprio de apps\n" +
                    "• Top Shelf com até 6 favoritos\n" +
                    "• Screensaver com relógio\n" +
                    "• Animação de foco e brilho que segue o app"
            ),
            LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply { topMargin = dpi(this@SettingsActivity, 10f) }
        )
    }

    // --------------------------------------------------------------- acoes

    private fun createRoom() {
        var n = 1
        for (r in rooms) if (r.id != Room.ALL) n++
        val room = Room("r" + System.currentTimeMillis(), "Sala $n")
        rooms.add(room)
        store.setRooms(rooms)
        store.activeRoom = room.id
        section = 1
        editorRoomId = room.id
        for (i in navItems.indices) navItems[i].selected = i == 1
        renderPane()
        toast("Sala criada")
    }

    private fun promptName(room: Room) {
        if (prompt != null) return
        lastFocused = currentFocus
        val p = PromptSheet(this, "Nome da sala", room.name) { name ->
            room.name = name
            store.setRooms(rooms)
            renderPane()
        }
        p.onClose = {
            prompt = null
            blockBackground(false)
            restoreFocus()
        }
        prompt = p
        blockBackground(true)
        p.open(root)
    }

    private fun confirmDelete(room: Room) {
        val s = Sheet(this)
            .title("Excluir ${room.name}?")
            .body("A sala é removida. Os apps continuam instalados.")
            .item("Excluir") {
                rooms.removeAll { it.id == room.id }
                store.setRooms(rooms)
                if (store.activeRoom == room.id) store.activeRoom = Room.ALL
                editorRoomId = null
                closeSheet()
                renderPane()
            }
            .item("Cancelar") { closeSheet() }
        openSheet(s)
    }

    private fun confirmReset() {
        val s = Sheet(this)
            .title("Restaurar padrões?")
            .body("Salas, destaques e ajustes voltam ao original.")
            .item("Restaurar") {
                store.reset()
                rooms = store.rooms()
                closeSheet()
                recreate()
            }
            .item("Cancelar") { closeSheet() }
        openSheet(s)
    }

    private fun openSheet(s: Sheet) {
        closeSheet()
        lastFocused = currentFocus
        s.onClose = {
            sheet = null
            blockBackground(false)
            restoreFocus()
        }
        sheet = s
        blockBackground(true)
        s.open(root)
    }

    private fun closeSheet() {
        val s = sheet ?: return
        sheet = null
        val parent = s.parent as? ViewGroup
        parent?.removeView(s)
        blockBackground(false)
        restoreFocus()
    }

    private fun blockBackground(block: Boolean) {
        body.descendantFocusability = if (block) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
    }

    private fun restoreFocus() {
        pane.post {
            val target = lastFocused
            if (target != null && target.parent != null) {
                target.requestFocus()
            } else {
                navItems.getOrNull(0)?.requestFocus()
            }
        }
    }

    private fun restoreFocusIndex() {
        pane.post {
            for (i in 0 until pane.childCount) {
                val c = pane.getChildAt(i)
                if (c.isFocusable) {
                    c.requestFocus()
                    return@post
                }
            }
        }
    }

    private fun repaintRows() {
        for (i in 0 until pane.childCount) {
            val child = pane.getChildAt(i)
            if (child is SettingRow) {
                child.refreshAccent()
            }
        }
    }

    private fun openHomeSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
            }
        }
        toast("Não foi possível abrir as configurações de Home")
    }

    private fun saverLabel(min: Int): String = when (min) {
        0 -> "Desligado"
        1 -> "1 minuto"
        3 -> "3 minutos"
        5 -> "5 minutos"
        else -> "10 minutos"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (prompt != null) {
            prompt?.dismiss()
            return
        }
        if (sheet != null) {
            closeSheet()
            return
        }
        if (editorRoomId != null) {
            editorRoomId = null
            renderPane()
            return
        }
        super.onBackPressed()
    }

    companion object {
        val STYLES = arrayOf("Aurora", "Meia-noite", "Neon")
    }
}
