package com.exampletest.mobilestudiotest.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.exampletest.mobilestudiotest.core.PlaceFactory
import com.exampletest.mobilestudiotest.core.Recents
import com.exampletest.mobilestudiotest.core.Studio
import com.exampletest.mobilestudiotest.net.Accounts
import com.exampletest.mobilestudiotest.net.AuthRequiredException
import com.exampletest.mobilestudiotest.net.ImageLoader
import com.exampletest.mobilestudiotest.net.RobloxApi
import com.exampletest.mobilestudiotest.net.RobloxCloud
import java.util.concurrent.Executors

/**
 * The page Mobile Studio opens on, laid out like Roblox Studio's start page: Home (a greeting,
 * new place templates, recent files and your games), Recent, and the Creations pages — My Games,
 * Shared With Me (experiences you were added to as a collaborator) and Group Games — plus opening
 * files, About, and the account switcher at the top right.
 */
@SuppressLint("ViewConstructor")
class StartScreen(private val act: Activity, private val studio: Studio, private val actions: MainActions, private val host: Host) : LinearLayout(act) {
    interface Host {
        fun newFromTemplate(t: PlaceFactory.Template)
        fun openFileFromStart()
        fun openRecentFile(e: Recents.Entry)
        fun closeStart()
        fun hasOpenPlace(): Boolean
        fun showStudioSettings()
    }

    private val net = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val content = FrameLayout(act)
    private val nav = LinearLayout(act)
    private var page = HOME
    private val accountChip = LinearLayout(act)
    private val phone = act.resources.configuration.smallestScreenWidthDp < 600
    private var gen = 0
    private var selectedGroup: Long = 0L
    private val iconViews = HashMap<String, MutableList<ImageView>>()
    private val imgListener: (String) -> Unit = { k ->
        if (k.startsWith("url:")) main.post { iconViews[k]?.forEach { v -> ImageLoader.peekKey(k)?.let { b -> showBitmap(v, b) } } }
    }
    private val accountListener: () -> Unit = { main.post { refreshAccount(); if (page != RECENT) show(page) } }
    /** Card grids on the current page and the search text that filters them. */
    private val grids = ArrayList<FlowLayout>()
    private var query = ""

    private val classicBg = 0xFFF2F4F5.toInt()
    private val classicSurface = 0xFFFFFFFF.toInt()
    private val classicNav = 0xFFE3E5E7.toInt()
    private val classicHeader = 0xFF191919.toInt()
    private val classicBlue = 0xFF00A2FF.toInt()
    private val classicText = 0xFF393B3D.toInt()
    private val classicDim = 0xFF6D7175.toInt()
    private val classicBorder = 0xFFD6D9DC.toInt()
    private val linkColor get() = 0xFF0066A6.toInt()
    private val sideColor get() = classicNav

    init {
        orientation = VERTICAL
        setBackgroundColor(classicBg)
        isClickable = true
        // Top bar: logo, name and version, back to the open place, About, Settings, account.
        val top = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(classicHeader); setPadding(dp(12f), dp(7f), dp(8f), dp(7f)) }
        top.addView(ImageView(act).apply {
            setImageDrawable(Icons.general(act, "Place"))
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            background = GradientDrawable().apply { setColor(0xFFE2231A.toInt()); cornerRadius = dp(3f).toFloat() }
        }, Ui.lp(dp(30f), dp(30f)).apply { rightMargin = dp(10f) })
        top.addView(Ui.text(act, "ROBLOX", 17f, 0xFFFFFFFF.toInt(), true))
        top.addView(Ui.text(act, "v" + AboutDialog.version(act).first, 11f, Ui.DIM).apply {
            setPadding(dp(7f), dp(1f), dp(7f), dp(1f))
            background = GradientDrawable().apply { setColor(0xFF343638.toInt()); cornerRadius = dp(3f).toFloat() }
        }, Ui.lp(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(8f) })
        top.addView(View(act), Ui.lp(0, 1, 1f))
        if (host.hasOpenPlace()) top.addView(Ui.iconTextButton(act, Icons.general(act, "Back"), if (phone) "Place" else "Back to place") { host.closeStart() },
            Ui.lp(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(6f) })
        top.addView(Ui.iconButton(act, Icons.general(act, "Info")) { AboutDialog.show(act) }.apply { contentDescription = "About Mobile Studio" })
        top.addView(Ui.iconButton(act, Icons.general(act, "Settings")) { host.showStudioSettings() }.apply { contentDescription = "Studio settings" })
        accountChip.apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(3f), dp(10f), dp(3f))
            background = Ui.rippleBg(act, Ui.HOVER, 16f)
            setOnClickListener { AccountUi.show(act, studio, actions) }
        }
        top.addView(accountChip, Ui.lp(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(6f) })
        addView(top, Ui.lp(Ui.MATCH, Ui.WRAP))
        addView(Ui.divider(act))

        val body = LinearLayout(act).apply { orientation = if (phone) VERTICAL else HORIZONTAL; setBackgroundColor(classicBg) }
        nav.orientation = if (phone) HORIZONTAL else VERTICAL
        if (phone) {
            nav.setPadding(dp(8f), dp(7f), dp(8f), dp(7f))
            body.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(sideColor); addView(nav) }, Ui.lp(Ui.MATCH, Ui.WRAP))
            body.addView(Ui.divider(act))
        } else {
            nav.setPadding(dp(10f), dp(12f), dp(10f), dp(10f))
            body.addView(ScrollView(act).apply { isFillViewport = true; setBackgroundColor(sideColor); addView(nav, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)) }, Ui.lp(dp(190f), Ui.MATCH))
            body.addView(Ui.divider(act, true))
        }
        body.addView(content, Ui.lp(if (phone) Ui.MATCH else 0, if (phone) 0 else Ui.MATCH, 1f))
        addView(body, Ui.lp(Ui.MATCH, 0, 1f))
        refreshAccount()
        show(HOME)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ImageLoader.listeners.add(imgListener); Accounts.listeners.add(accountListener)
    }
    override fun onDetachedFromWindow() {
        ImageLoader.listeners.remove(imgListener); Accounts.listeners.remove(accountListener)
        gen++
        net.shutdown()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float) = Ui.dp(act, v)

    // ------------------------------------------------------------------ navigation

    private fun buildNav() {
        nav.removeAllViews()
        fun item(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
            val v = LinearLayout(act).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10f), dp(if (phone) 8f else 10f), dp(14f), dp(if (phone) 8f else 10f))
                background = Ui.rippleBg(act, if (selected) 0xFFC7E8F8.toInt() else sideColor, if (phone) 4f else 3f)
                isClickable = true
                setOnClickListener { onClick() }
                contentDescription = label
            }
            v.addView(ImageView(act).apply { setImageDrawable(Icons.general(act, icon)) }, Ui.lp(dp(20f), dp(20f)).apply { rightMargin = dp(10f) })
            v.addView(Ui.text(act, label, 13.5f, if (selected) 0xFF0066A6.toInt() else classicText, selected))
            nav.addView(v, if (phone) Ui.lp(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(4f) } else Ui.lp(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(2f) })
        }
        fun label(text: String) {
            if (phone) return
            nav.addView(Ui.text(act, text.uppercase(), 10.5f, classicDim, true).apply { letterSpacing = 0.08f; setPadding(dp(12f), dp(14f), 0, dp(6f)) })
        }
        for ((p, icon) in listOf(HOME to "Home", RECENT to "History")) item(p, icon, page == p) { show(p) }
        label("Creations")
        for ((p, icon) in listOf(MY_GAMES to "Account", SHARED to "Shared", GROUPS to "Group")) item(p, icon, page == p) { show(p) }
        label("Open")
        item("Open File…", "Open", false) { host.openFileFromStart() }
        item("Open by Place ID…", "Download", false) { AccountUi.openById(act, studio, actions) }
        if (!phone) nav.addView(View(act), Ui.lp(Ui.MATCH, 0, 1f))
        item(if (phone) "About" else "About  ·  v${AboutDialog.version(act).first}", "Info", false) { AboutDialog.show(act) }
    }

    private fun refreshAccount() {
        accountChip.removeAllViews()
        val img = ImageView(act)
        accountChip.addView(img, Ui.lp(dp(26f), dp(26f)).apply { rightMargin = dp(6f) })
        val a = Accounts.active(act)
        if (a != null) AccountUi.loadHeadshot(img, a.userId) else img.setImageDrawable(Icons.general(act, "Account"))
        val name = a?.displayName?.ifEmpty { a.name } ?: "Guest"
        accountChip.addView(Ui.text(act, (if (phone) name.take(10) else name) + "  ▾", 13f, Ui.TEXT))
    }

    private fun show(p: String) {
        page = p
        gen++
        grids.clear()
        query = ""
        buildNav()
        content.removeAllViews()
        iconViews.clear()
        when (p) {
            HOME -> home()
            RECENT -> recent()
            MY_GAMES -> games(Kind.MINE)
            SHARED -> games(Kind.SHARED)
            GROUPS -> groupGames()
        }
    }

    // ------------------------------------------------------------------ building blocks

    private fun scrollColumn(): LinearLayout {
        val col = LinearLayout(act).apply { orientation = VERTICAL; setBackgroundColor(classicBg); setPadding(dp(if (phone) 14f else 22f), dp(12f), dp(if (phone) 14f else 22f), dp(28f)) }
        content.addView(ScrollView(act).apply { addView(col) }, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        return col
    }

    /** A section title, with an optional "See all" link to [seeAll]. */
    private fun heading(col: LinearLayout, text: String, seeAll: String? = null) {
        val r = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(20f), 0, dp(10f)) }
        r.addView(Ui.text(act, text, 17f, classicText, true), Ui.lp(0, Ui.WRAP, 1f))
        if (seeAll != null) r.addView(Ui.text(act, "See all  ›", 13f, linkColor, true).apply {
            setPadding(dp(10f), dp(4f), dp(6f), dp(4f))
            background = Ui.rippleBg(act, android.graphics.Color.TRANSPARENT, 12f)
            setOnClickListener { show(seeAll) }
        })
        col.addView(r, Ui.lp(Ui.MATCH, Ui.WRAP))
    }

    /** Title, subtitle, refresh and (optionally) a search box at the top of a list page. */
    private fun pageHeader(col: LinearLayout, title: String, sub: String, search: Boolean, onRefresh: (() -> Unit)?) {
        val r = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12f), 0, dp(4f)) }
        val t = LinearLayout(act).apply { orientation = VERTICAL }
        t.addView(Ui.text(act, title, 22f, classicText, true))
        t.addView(Ui.text(act, sub, 12.5f, classicDim).apply { isSingleLine = false; setPadding(0, dp(2f), 0, 0) })
        r.addView(t, Ui.lp(0, Ui.WRAP, 1f))
        if (onRefresh != null) r.addView(Ui.iconButton(act, Icons.general(act, "PluginsFolder"), 36f) { onRefresh() }.apply { contentDescription = "Refresh" })
        col.addView(r, Ui.lp(Ui.MATCH, Ui.WRAP))
        if (!search) return
        val box = Ui.input(act, "", "Search").apply {
            val ic = Icons.general(act, "FindAll"); ic.setBounds(0, 0, dp(16f), dp(16f))
            setCompoundDrawables(ic, null, null, null); compoundDrawablePadding = dp(8f)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply { setColor(Ui.INPUT); setStroke(dp(1f), Ui.blend(Ui.INPUT, Ui.TEXT, 0.18f)); cornerRadius = dp(20f).toFloat() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim().orEmpty(); applyFilter() }
            })
        }
        col.addView(box, Ui.lp(if (phone) Ui.MATCH else dp(340f), Ui.WRAP).apply { topMargin = dp(10f); bottomMargin = dp(6f) })
    }

    private fun applyFilter() {
        val q = query.lowercase()
        for (g in grids) for (i in 0 until g.childCount) {
            val c = g.getChildAt(i)
            val name = (c.tag as? String)?.lowercase() ?: continue
            c.visibility = if (q.isEmpty() || q in name) View.VISIBLE else View.GONE
        }
    }

    private fun message(col: LinearLayout, text: String, button: String? = null, icon: String = "Account", onClick: (() -> Unit)? = null) {
        val card = LinearLayout(act).apply {
            orientation = VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = GradientDrawable().apply { setColor(Ui.CARD); cornerRadius = dp(12f).toFloat() }
        }
        card.addView(Ui.text(act, text, 13.5f, Ui.TEXT).apply { isSingleLine = false; setLineSpacing(dp(2f).toFloat(), 1f) })
        if (button != null && onClick != null) card.addView(Ui.iconTextButton(act, Icons.general(act, icon), button, Ui.ACCENT) { onClick() }.apply { setTextColor(0xFFFFFFFF.toInt()) },
            Ui.lp(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(10f) })
        col.addView(card, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(6f); bottomMargin = dp(6f) })
    }

    private fun showBitmap(img: ImageView, b: android.graphics.Bitmap) {
        img.setPadding(0, 0, 0, 0)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setImageBitmap(b)
    }

    /** Shows the image at [url] in [img] once it's loaded, a centered [fallback] icon until then. */
    private fun bindIcon(img: ImageView, url: String?, fallback: String) {
        val pad = dp(if (phone) 30f else 36f)
        fun placeholder() { img.scaleType = ImageView.ScaleType.FIT_CENTER; img.setPadding(pad, pad, pad, pad); img.setImageDrawable(Icons.general(act, fallback)) }
        if (url == null) { placeholder(); return }
        val key = "url:$url"
        val bmp = ImageLoader.getKey(key)
        if (bmp != null) showBitmap(img, bmp) else { placeholder(); iconViews.getOrPut(key) { ArrayList() }.add(img) }
    }

    /** A Studio-like tile: rounded thumbnail with the name and a detail line under it. */
    private fun card(title: String, sub: String, iconUrl: String?, fallback: String, onClick: () -> Unit, onLong: (() -> Unit)? = null, tint: Int? = null,
                     iconDrawable: android.graphics.drawable.Drawable? = null): LinearLayout {
        val w = dp(if (phone) 140f else 168f)
        val r = dp(4f).toFloat()
        val c = LinearLayout(act).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply { setColor(classicSurface); setStroke(dp(1f), classicBorder); cornerRadius = dp(4f).toFloat() }
            isClickable = true
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
            tag = title
            contentDescription = title
        }
        val base = tint ?: classicNav
        val thumb = FrameLayout(act).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(if (tint != null) Ui.blend(tint, 0xFFFFFFFF.toInt(), 0.18f) else base, if (tint != null) Ui.blend(tint, 0xFF000000.toInt(), 0.22f) else base)).apply {
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
        }
        val img = ImageView(act)
        thumb.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        bindIcon(img, iconUrl, fallback)
        if (iconDrawable != null) img.setImageDrawable(iconDrawable)
        c.addView(thumb, Ui.lp(w, w))
        val texts = LinearLayout(act).apply { orientation = VERTICAL; setPadding(dp(10f), dp(8f), dp(10f), dp(10f)) }
        texts.addView(Ui.text(act, title, 13f, classicText, true).apply { ellipsize = android.text.TextUtils.TruncateAt.END })
        texts.addView(Ui.text(act, sub, 11f, classicDim).apply { ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2f), 0, 0) })
        c.addView(texts, Ui.lp(w, Ui.WRAP))
        c.layoutParams = LayoutParams(w, Ui.WRAP)
        return c
    }

    private fun grid(col: LinearLayout): FlowLayout {
        val f = FlowLayout(act).apply { gap = dp(12f) }
        col.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP))
        grids.add(f)
        return f
    }

    /** A sideways-scrolling row of cards (Home). */
    private fun row(col: LinearLayout): LinearLayout {
        val r = LinearLayout(act).apply { orientation = HORIZONTAL }
        col.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(r) }, Ui.lp(Ui.MATCH, Ui.WRAP))
        return r
    }
    private fun addToRow(r: LinearLayout, v: View) = r.addView(v, LayoutParams(v.layoutParams?.width ?: Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(12f) })

    private fun loading(col: LinearLayout): View {
        val p = ProgressBar(act).apply { isIndeterminate = true }
        col.addView(p, Ui.lp(dp(36f), dp(36f)).apply { topMargin = dp(20f); gravity = Gravity.CENTER_HORIZONTAL })
        return p
    }

    // ------------------------------------------------------------------ Home

    private fun home() {
        val col = scrollColumn()
        greeting(col)

        val looks = mapOf(
            PlaceFactory.Template.BASEPLATE to (0xFF4C7BD9.toInt() to "Part_Block"),
            PlaceFactory.Template.CLASSIC to (0xFF4E9A52.toInt() to "Surface"),
            PlaceFactory.Template.FLAT_TERRAIN to (0xFF5E8C3A.toInt() to "Terrain"),
            PlaceFactory.Template.EMPTY to (0xFF8A6FD1.toInt() to "Place"),
            PlaceFactory.Template.OBBY to (0xFFD9534F.toInt() to "Spawn"),
            PlaceFactory.Template.ARENA to (0xFFE08A2E.toInt() to "Team"),
            PlaceFactory.Template.ISLAND to (0xFF2F8FB5.toInt() to "Terrain"),
            PlaceFactory.Template.VILLAGE to (0xFF8C6A45.toInt() to "Model"),
        )
        // Like Studio's New tab: the basic templates, then gameplay and theme templates.
        for ((i, group) in PlaceFactory.Template.values().groupBy { it.category }.entries.withIndex()) {
            heading(col, if (i == 0) "Start creating" else group.key)
            val tr = row(col)
            for (t in group.value) {
                val (tint, icon) = looks[t] ?: (0xFF6B6B6B.toInt() to "Place")
                val d = if (icon == "Terrain" || icon == "Team" || icon == "Model") Icons.instance(act, icon) else null
                addToRow(tr, card(t.title, t.subtitle, null, icon, { host.newFromTemplate(t) }, tint = tint, iconDrawable = d))
            }
        }

        heading(col, "Recent", RECENT)
        val recents = recentEntries(12)
        if (recents.isEmpty()) message(col, "Places and files you open show up here, so you can jump right back in.")
        else {
            val rr = row(col)
            for ((_, v) in recentCards(recents)) addToRow(rr, v)
        }

        if (!RobloxApi.isGuest(act)) {
            heading(col, "My Games", MY_GAMES)
            val holder = LinearLayout(act).apply { orientation = VERTICAL }
            col.addView(holder, Ui.lp(Ui.MATCH, Ui.WRAP))
            val uid = RobloxApi.userId(act)
            fun fill(list: List<RobloxCloud.Game>) {
                holder.removeAllViews()
                if (list.isEmpty()) { message(holder, "You don't own any experiences yet. Make one here and publish it with File → Publish to Roblox As."); return }
                val gr = row(holder)
                for (g in list.take(12)) addToRow(gr, gameCard(g))
            }
            val cached = StartCache.games(act, uid, Kind.MINE.key)
            if (cached != null) fill(cached) else {
                val spin = loading(holder)
                val myGen = gen
                net.execute {
                    val r = try { loadGames(Kind.MINE) } catch (_: Exception) { null }
                    main.post { if (myGen == gen) { holder.removeView(spin); if (r != null) fill(r) else message(holder, "Couldn't load your games right now. Open My Games to try again.") } }
                }
            }
        }

        tip(col)
    }

    private fun greeting(col: LinearLayout) {
        val a = Accounts.active(act)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hi there" }
        val name = a?.displayName?.ifEmpty { a.name }
        val box = LinearLayout(act).apply {
            orientation = VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            background = GradientDrawable().apply { setColor(classicSurface); setStroke(dp(1f), classicBorder); cornerRadius = dp(4f).toFloat() }
        }
        val top = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val img = ImageView(act).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE3E5E7.toInt()) }
            clipToOutline = true
        }
        if (a != null) AccountUi.loadHeadshot(img, a.userId) else img.setImageDrawable(Icons.general(act, "Account"))
        top.addView(img, Ui.lp(dp(56f), dp(56f)).apply { rightMargin = dp(14f) })
        val t = LinearLayout(act).apply { orientation = VERTICAL }
        t.addView(Ui.text(act, hello + (name?.let { ", $it" } ?: "") + "!", 20f, classicText, true).apply { isSingleLine = false })
        t.addView(Ui.text(act, if (a == null) "Sign in to open your Roblox games, or start something new below." else "What are we building today?", 13f, classicDim).apply {
            isSingleLine = false; alpha = 0.8f; setPadding(0, dp(2f), 0, 0)
        })
        top.addView(t, Ui.lp(0, Ui.WRAP, 1f))
        box.addView(top)
        val buttons = FlowLayout(act).apply { gap = dp(8f) }
        buttons.addView(Ui.iconTextButton(act, Icons.general(act, "Part_Block"), "New Baseplate", classicBlue) { host.newFromTemplate(PlaceFactory.Template.BASEPLATE) }.apply {
            setTextColor(0xFFFFFFFF.toInt()); background = Ui.rippleBg(act, classicBlue, 4f); setPadding(dp(14f), dp(8f), dp(16f), dp(8f))
        })
        buttons.addView(Ui.iconTextButton(act, Icons.general(act, "Open"), "Open File") { host.openFileFromStart() }.apply {
            background = Ui.rippleBg(act, classicNav, 4f); setPadding(dp(14f), dp(8f), dp(16f), dp(8f))
        })
        if (a == null) buttons.addView(Ui.iconTextButton(act, Icons.general(act, "Account"), "Sign in") { AccountUi.signIn(act) }.apply {
            background = Ui.rippleBg(act, classicNav, 4f); setPadding(dp(14f), dp(8f), dp(16f), dp(8f))
        })
        box.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(14f) })
        col.addView(box, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8f) })
    }

    private fun tip(col: LinearLayout) {
        val tips = listOf(
            "Long-press an object in the Explorer to drag it into another parent or the Toolbox.",
            "File → Playtest Avatar lets you play as R6, R15, your own avatar or any model you save.",
            "Double-tap a panel's header to float it, or drag it to dock it on another side.",
            "Hold Ctrl or Shift (on a keyboard) and tap to select more than one part.",
            "Test → Play Here starts the playtest right where the camera is looking.",
            "The Toolbox's Creator Store tab can insert free models, meshes, audio and plugins.",
        )
        val box = LinearLayout(act).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = GradientDrawable().apply { setColor(Ui.CARD); cornerRadius = dp(12f).toFloat() }
        }
        box.addView(ImageView(act).apply { setImageDrawable(Icons.general(act, "Info")) }, Ui.lp(dp(22f), dp(22f)).apply { rightMargin = dp(12f) })
        box.addView(Ui.text(act, "Tip: " + tips[(System.currentTimeMillis() / 60_000 % tips.size).toInt()], 12.5f, Ui.TEXT).apply { isSingleLine = false }, Ui.lp(0, Ui.WRAP, 1f))
        col.addView(box, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(24f) })
    }

    // ------------------------------------------------------------------ Recent

    private fun recentEntries(limit: Int): List<Recents.Entry> {
        val myId = RobloxApi.userId(act)
        return Recents.list(act).filter { !it.isCloud || it.userId == 0L || it.userId == myId || RobloxApi.isGuest(act) }.take(limit)
    }

    private fun recent() {
        val col = scrollColumn()
        pageHeader(col, "Recent", "Places and files you opened lately. Long-press one to remove it.", true, null)
        val list = recentEntries(60)
        if (list.isEmpty()) { message(col, "Places and files you open show up here."); return }
        val g = grid(col)
        recentCards(list).forEach { g.addView(it.second) }
    }

    /** Cards for recent places; Roblox places get their game icons once they're known. */
    private fun recentCards(list: List<Recents.Entry>): List<Pair<Recents.Entry, LinearLayout>> {
        val cards = list.map { e ->
            val sub = (if (e.isCloud) "Roblox place" else "On this device") + "  ·  " + android.text.format.DateUtils.getRelativeTimeSpanString(e.time)
            e to card(e.name.removeSuffix(".rbxl").removeSuffix(".rbxlx"), sub, StartCache.icons[e.universeId].takeIf { e.isCloud }, if (e.isCloud) "Place" else "Open", { openRecent(e) }, {
                Dialogs.themed(act).setTitle(e.name).setItems(arrayOf("Open", "Remove from Recent")) { _, i ->
                    if (i == 0) openRecent(e) else { Recents.remove(act, e); show(page) }
                }.show()
            })
        }
        val ids = list.filter { it.isCloud && it.universeId != 0L && it.universeId !in StartCache.icons }.map { it.universeId }.distinct()
        if (ids.isNotEmpty()) {
            val myGen = gen
            net.execute {
                val icons = try { RobloxCloud.gameIcons(ids) } catch (_: Exception) { emptyMap() }
                StartCache.icons.putAll(icons)
                main.post {
                    if (myGen != gen) return@post
                    for ((e, v) in cards) {
                        val url = icons[e.universeId] ?: continue
                        bindIcon(((v.getChildAt(0) as FrameLayout).getChildAt(0) as ImageView), url, "Place")
                    }
                }
            }
        }
        return cards
    }

    private fun openRecent(e: Recents.Entry) {
        if (e.isCloud) {
            if (RobloxApi.isGuest(act)) { AccountUi.signIn(act) { openRecent(e) }; return }
            AccountUi.openPlace(act, actions, null, RobloxCloud.Place(e.placeId, e.name, e.universeId), null)
        } else host.openRecentFile(e)
    }

    // ------------------------------------------------------------------ My Games / Shared With Me

    private enum class Kind(val key: String) { MINE("games"), SHARED("shared") }

    private fun loadGames(kind: Kind): List<RobloxCloud.Game> {
        val user = RobloxCloud.cachedUser(act) ?: RobloxCloud.currentUser(act)
        val list = if (kind == Kind.MINE) RobloxCloud.userGames(act, user) else RobloxCloud.sharedGames(act, user)
        StartCache.icons.putAll(RobloxCloud.gameIcons(list.map { it.universeId }.filter { it !in StartCache.icons }))
        StartCache.putGames(act, RobloxApi.userId(act), list, kind.key)
        return list
    }

    private fun gameCard(game: RobloxCloud.Game, showOwner: Boolean = false): LinearLayout {
        val sub = when {
            showOwner && game.owner.isNotEmpty() -> "by " + game.owner
            game.updated.isNotEmpty() -> "Updated " + AccountUi.prettyDate(game.updated).substringBefore("  ")
            else -> game.owner
        }
        return card(game.name, sub, StartCache.icons[game.universeId], "Place", {
            // Like Studio: opening a game opens its start place (pick one if there are several).
            AccountUi.places(act, studio, actions, game) { gm, p -> AccountUi.openPlace(act, actions, gm, p, null) }
        }, { AccountUi.places(act, studio, actions, game, null) })
    }

    private fun games(kind: Kind) {
        val col = scrollColumn()
        val mine = kind == Kind.MINE
        pageHeader(col, if (mine) "My Games" else "Shared With Me",
            if (mine) "Experiences you own. Tap one to edit it; long-press for downloads and version history."
            else "Experiences other creators added you to as a collaborator.", true) { show(page) }
        if (RobloxApi.isGuest(act)) {
            message(col, "You're using Mobile Studio as a guest. Sign in to see " + (if (mine) "the experiences you own." else "the experiences shared with you."), "Sign in with Roblox") { AccountUi.signIn(act) }
            return
        }
        val myGen = gen
        val uid = RobloxApi.userId(act)
        // Show the last list right away (no spinner wait), then refresh it.
        val cached = StartCache.games(act, uid, kind.key)
        val holder = LinearLayout(act).apply { orientation = VERTICAL }
        col.addView(holder, Ui.lp(Ui.MATCH, Ui.WRAP))
        val empty = if (mine) "You don't own any experiences yet. Create one on create.roblox.com (or File → Publish to Roblox As → Create new game) and it will show up here."
            else "Nothing has been shared with you yet. When someone adds you as a collaborator on their experience (Game Settings → Permissions / Collaborators), it shows up here."
        if (cached != null) showGames(holder, cached, empty, !mine)
        val spinner = if (cached == null) loading(col) else null
        net.execute {
            val r = try { Result.success(loadGames(kind)) } catch (e: Exception) { Result.failure(e) }
            main.post {
                if (myGen != gen) return@post
                spinner?.let { col.removeView(it) }
                r.fold({ list ->
                    holder.removeAllViews()
                    grids.removeAll { it.parent == null || it.parent === holder }
                    showGames(holder, list, empty, !mine)
                    applyFilter()
                }) { e -> if (cached == null) error(col, e) }
            }
        }
    }

    private fun error(col: LinearLayout, e: Throwable) {
        if (e is AuthRequiredException) message(col, e.message ?: "Sign in again.", "Sign in") { AccountUi.signIn(act) }
        else message(col, "Couldn't load this from Roblox.\n${e.message ?: e}", "Try again", "PluginsFolder") { show(page) }
    }

    private fun showGames(col: LinearLayout, list: List<RobloxCloud.Game>, empty: String, showOwner: Boolean = false) {
        if (list.isEmpty()) { message(col, empty); return }
        val g = grid(col)
        for (game in list) g.addView(gameCard(game, showOwner))
    }

    // ------------------------------------------------------------------ Group Games

    private fun groupGames() {
        val col = scrollColumn()
        pageHeader(col, "Group Games", "Experiences in your groups that you can edit.", true) { show(page) }
        if (RobloxApi.isGuest(act)) {
            message(col, "You're using Mobile Studio as a guest. Sign in to see the group experiences you can edit.", "Sign in with Roblox") { AccountUi.signIn(act) }
            return
        }
        val myGen = gen
        val user = RobloxCloud.cachedUser(act)
        val uid = RobloxApi.userId(act)
        val view = GroupsView(col)
        val cached = StartCache.groups(act, uid)
        if (cached != null) view.set(cached.second, cached.first, done = false)
        else view.status("Loading your groups…", spin = true)
        net.execute {
            try {
                val gl = RobloxCloud.allGroups(act, user ?: RobloxCloud.currentUser(act))
                val groupList = gl.groups.take(150)
                // Group icons for the chips, while the games load.
                net.execute {
                    val icons = try { RobloxCloud.groupIcons(groupList.map { it.id }.filter { it !in StartCache.groupIcons }.take(100)) } catch (_: Exception) { emptyMap() }
                    StartCache.groupIcons.putAll(icons)
                    if (icons.isNotEmpty()) main.post { if (myGen == gen) view.redrawChips() }
                }
                // Each group is loaded and checked on its own, a few at once (Roblox rate-limits
                // bigger bursts), and shows up as soon as it's ready.
                val found = java.util.concurrent.ConcurrentHashMap<Long, List<RobloxCloud.Game>>()
                val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
                val checked = java.util.concurrent.atomic.AtomicInteger()
                val authError = java.util.concurrent.atomic.AtomicReference<Exception?>()
                val pool = Executors.newFixedThreadPool(4)
                val previous = cached?.second.orEmpty().associate { it.first.id to it.second }
                fun snapshot() = groupList.mapNotNull { gp ->
                    // A group that couldn't be checked this time keeps what it showed before.
                    (found[gp.id] ?: previous[gp.id]?.takeIf { gp.id in failed })?.takeIf { it.isNotEmpty() }?.let { gp to it }
                }
                for (gp in groupList) pool.execute {
                    if (authError.get() != null || myGen != gen) return@execute
                    try {
                        found[gp.id] = RobloxCloud.editableGroupGames(act, gp, gp.id in gl.studioListed)
                    } catch (e: AuthRequiredException) { authError.set(e) } catch (_: Exception) { failed.add(gp.id) }
                    val n = checked.incrementAndGet()
                    val snap = snapshot()
                    main.post {
                        if (myGen != gen) return@post
                        // With a cached list on screen, keep it until the refresh is complete.
                        if (cached == null) view.set(snap, groupList.size, done = false)
                        if (n < groupList.size) view.status("Checked $n of ${groupList.size} groups…", spin = true)
                    }
                }
                pool.shutdown()
                pool.awaitTermination(4, java.util.concurrent.TimeUnit.MINUTES)
                authError.get()?.let { throw it }
                val result = snapshot()
                StartCache.putGroups(act, uid, groupList.size, result)
                val missing = (groupList.indices).count { groupList[it].id in failed && found[groupList[it].id] == null }
                main.post {
                    if (myGen != gen) return@post
                    view.set(result, groupList.size, done = true)
                    if (missing > 0) view.status("Couldn't check $missing group${if (missing == 1) "" else "s"} — Roblox was busy. Tap to try again.", spin = false) { show(page) }
                }
            } catch (e: Exception) {
                main.post { if (myGen == gen) { view.status(null, false); if (cached == null) error(col, e) } }
            }
        }
    }

    /**
     * Groups with at least one experience this account can edit. Groups are added as they finish
     * loading; the selected group's games stay put while more groups come in.
     */
    private inner class GroupsView(col: LinearLayout) {
        private val statusRow = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6f), 0, dp(6f)) }
        private val statusText = Ui.text(act, "", 12f, Ui.DIM).apply { isSingleLine = false }
        private val statusSpin = ProgressBar(act).apply { isIndeterminate = true }
        private val chips = LinearLayout(act).apply { orientation = HORIZONTAL }
        private val area = LinearLayout(act).apply { orientation = VERTICAL }
        private var groups: List<RobloxCloud.Group> = emptyList()
        private var gamesOf: Map<Long, List<RobloxCloud.Game>> = emptyMap()
        private var shownGroup = 0L
        private var shownGames: List<Long> = emptyList()

        init {
            statusRow.addView(statusSpin, Ui.lp(dp(16f), dp(16f)).apply { rightMargin = dp(8f) })
            statusRow.addView(statusText, Ui.lp(0, Ui.WRAP, 1f))
            col.addView(statusRow, Ui.lp(Ui.MATCH, Ui.WRAP))
            col.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, Ui.lp(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(4f) })
            col.addView(area, Ui.lp(Ui.MATCH, Ui.WRAP))
            status(null, false)
        }

        fun status(text: String?, spin: Boolean, onTap: (() -> Unit)? = null) {
            statusRow.visibility = if (text == null) View.GONE else View.VISIBLE
            statusText.text = text ?: ""
            statusText.setTextColor(if (onTap != null) linkColor else Ui.DIM)
            if (onTap != null) statusRow.setOnClickListener { onTap() } else { statusRow.setOnClickListener(null); statusRow.isClickable = false }
            statusSpin.visibility = if (spin) View.VISIBLE else View.GONE
        }

        fun set(editable: List<Pair<RobloxCloud.Group, List<RobloxCloud.Game>>>, inGroups: Int, done: Boolean) {
            groups = editable.map { it.first }
            gamesOf = editable.associate { it.first.id to it.second }
            if (done) status(null, false)
            if (groups.isEmpty()) {
                chips.removeAllViews()
                if (done) {
                    area.removeAllViews()
                    message(area, if (inGroups == 0) "You aren't in any groups yet." else "None of your groups have experiences you can edit. Ask a group owner to give your role permission to edit group experiences.")
                }
                return
            }
            if (selectedGroup == 0L || groups.none { it.id == selectedGroup }) selectedGroup = groups.first().id
            drawChips()
            val now = gamesOf[selectedGroup].orEmpty().map { it.universeId }
            if (shownGroup != selectedGroup || now != shownGames) select(groups.first { it.id == selectedGroup })
        }

        fun redrawChips() { if (groups.isNotEmpty()) drawChips() }

        private fun drawChips() {
            chips.removeAllViews()
            for (x in groups) {
                val on = x.id == selectedGroup
                val chip = LinearLayout(act).apply {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(5f), dp(5f), dp(14f), dp(5f))
                    background = Ui.rippleBg(act, if (on) Ui.ACCENT else Ui.CARD, 18f)
                    isClickable = true
                    setOnClickListener { select(x) }
                }
                val ic = ImageView(act).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.HOVER) }
                    clipToOutline = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                val url = StartCache.groupIcons[x.id]
                val bmp = url?.let { ImageLoader.getKey("url:$it") }
                if (bmp != null) ic.setImageBitmap(bmp) else {
                    ic.setImageDrawable(Icons.general(act, "Group")); ic.setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                    if (url != null) iconViews.getOrPut("url:$url") { ArrayList() }.add(ic)
                }
                chip.addView(ic, Ui.lp(dp(26f), dp(26f)).apply { rightMargin = dp(8f) })
                chip.addView(Ui.text(act, x.name, 13f, if (on) 0xFFFFFFFF.toInt() else Ui.TEXT, on))
                chips.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(8f); bottomMargin = dp(6f) })
            }
        }

        fun select(gp: RobloxCloud.Group) {
            selectedGroup = gp.id
            shownGroup = gp.id
            val mine = gamesOf[gp.id].orEmpty()
            shownGames = mine.map { it.universeId }
            drawChips()
            area.removeAllViews()
            grids.clear()
            area.addView(Ui.text(act, (if (gp.role.isNotEmpty()) "Your role: ${gp.role}  ·  " else "") + "${mine.size} experience${if (mine.size == 1) "" else "s"}", 12f, Ui.DIM).apply {
                setPadding(0, dp(6f), 0, dp(10f))
            })
            val holder = LinearLayout(act).apply { orientation = VERTICAL }
            area.addView(holder, Ui.lp(Ui.MATCH, Ui.WRAP))
            // Draw at once with the icons we already have; fetch the rest and redraw.
            showGames(holder, mine, "This group has no experiences you can edit.")
            applyFilter()
            val missing = mine.map { it.universeId }.filter { it !in StartCache.icons }
            if (missing.isEmpty()) return
            val myGen = gen
            net.execute {
                val icons = try { RobloxCloud.gameIcons(missing) } catch (_: Exception) { emptyMap() }
                StartCache.icons.putAll(icons)
                main.post {
                    if (myGen != gen || selectedGroup != gp.id || icons.isEmpty()) return@post
                    holder.removeAllViews()
                    grids.clear()
                    showGames(holder, mine, "This group has no experiences you can edit.")
                    applyFilter()
                }
            }
        }
    }

    companion object {
        const val HOME = "Home"
        const val RECENT = "Recent"
        const val MY_GAMES = "My Games"
        const val SHARED = "Shared With Me"
        const val GROUPS = "Group Games"
    }
}

/**
 * The start page's last lists of games, kept in memory and on disk so My Games / Shared With Me /
 * Group Games show immediately (then refresh) instead of waiting on Roblox every time.
 */
object StartCache {
    val icons: MutableMap<Long, String> = java.util.concurrent.ConcurrentHashMap()
    val groupIcons: MutableMap<Long, String> = java.util.concurrent.ConcurrentHashMap()
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("start_cache", android.content.Context.MODE_PRIVATE)

    /** Drops the saved lists (a game was created, so they're out of date). */
    fun forget(ctx: android.content.Context) = prefs(ctx).edit().clear().apply()

    private fun gameJson(g: RobloxCloud.Game) = org.json.JSONObject().put("u", g.universeId).put("n", g.name).put("r", g.rootPlaceId)
        .put("t", g.updated).put("o", g.owner).put("g", g.groupId)
    private fun gameOf(o: org.json.JSONObject) = RobloxCloud.Game(o.optLong("u"), o.optString("n"), o.optLong("r"), o.optString("t"), o.optString("o"), o.optLong("g"))

    /** A saved list ([kind] "games" = owned, "shared" = shared with you), or null if never loaded. */
    fun games(ctx: android.content.Context, uid: Long, kind: String = "games"): List<RobloxCloud.Game>? = try {
        val a = org.json.JSONArray(prefs(ctx).getString("${kind}_$uid", null) ?: return null)
        (0 until a.length()).map { gameOf(a.getJSONObject(it)) }
    } catch (_: Exception) { null }

    fun putGames(ctx: android.content.Context, uid: Long, list: List<RobloxCloud.Game>, kind: String = "games") {
        val a = org.json.JSONArray(); list.forEach { a.put(gameJson(it)) }
        prefs(ctx).edit().putString("${kind}_$uid", a.toString()).apply()
    }

    /** (groups the user is in, editable groups with their games), or null if never loaded. */
    fun groups(ctx: android.content.Context, uid: Long): Pair<Int, List<Pair<RobloxCloud.Group, List<RobloxCloud.Game>>>>? = try {
        val o = org.json.JSONObject(prefs(ctx).getString("groups_$uid", null) ?: return null)
        val a = o.getJSONArray("list")
        o.optInt("in") to (0 until a.length()).map { i ->
            val g = a.getJSONObject(i)
            val games = g.getJSONArray("games")
            RobloxCloud.Group(g.optLong("id"), g.optString("name"), g.optString("role"), g.optInt("rank")) to (0 until games.length()).map { gameOf(games.getJSONObject(it)) }
        }
    } catch (_: Exception) { null }

    fun putGroups(ctx: android.content.Context, uid: Long, inGroups: Int, list: List<Pair<RobloxCloud.Group, List<RobloxCloud.Game>>>) {
        val a = org.json.JSONArray()
        for ((g, games) in list) a.put(org.json.JSONObject().put("id", g.id).put("name", g.name).put("role", g.role).put("rank", g.rank)
            .put("games", org.json.JSONArray().apply { games.forEach { put(gameJson(it)) } }))
        prefs(ctx).edit().putString("groups_$uid", org.json.JSONObject().put("in", inGroups).put("list", a).toString()).apply()
    }
}
