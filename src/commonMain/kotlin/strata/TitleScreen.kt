package strata

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Actions the title-screen buttons can trigger. */
enum class TitleAction {
    SINGLEPLAYER,
    MULTIPLAYER,   // WIP (no screen yet)
    NATIVE_MODS,   // Opens Native Mods hub
    OPTIONS,       // no options screen yet — prints "coming soon"
    EXIT
}

/**
 * The title screen: an animated starfield background, the centered game
 * title, a column of menu buttons with hover highlighting, and the version /
 * copyright in opposite corners. Draws through the shared [Ui] renderer, so
 * it needs no textures or extra pipeline work.
 */
object TitleScreen {

    const val GAME_VERSION = "0.2.0"
    const val COPYRIGHT = "Copyright Novusforge Studios. Do not Distribute!"

    /** Everything the title screen needs for one frame. */
    data class TitleState(
        /** Seconds since app start — drives the starfield animation. */
        val time: Float,
        /** Cursor position in window pixels, for hover highlighting. */
        val mouseX: Float = -1f,
        val mouseY: Float = -1f
    )

    /* ── Deterministic starfield (fixed count, positions hashed from index) ── */
    private const val STAR_COUNT = 110

    /* ── Button layout data ── */
    private data class MenuButton(val label: String, val action: TitleAction, val wip: Boolean)

    private val BUTTONS = listOf(
        MenuButton("SINGLEPLAYER", TitleAction.SINGLEPLAYER, wip = false),
        MenuButton("MULTIPLAYER", TitleAction.MULTIPLAYER, wip = true),
        MenuButton("NATIVE MODS", TitleAction.NATIVE_MODS, wip = false),
        MenuButton("OPTIONS", TitleAction.OPTIONS, wip = false),
        MenuButton("EXIT GAME", TitleAction.EXIT, wip = false)
    )

    private const val BUTTON_W = 300f
    private const val BUTTON_H = 46f
    private const val BUTTON_GAP = 14f

    /** A deterministic 0..1 hash of an integer (stable star positions). */
    private fun hash01(i: Int): Float {
        var x = i * 374761393 + 668265263
        x = (x xor (x shr 13)) * 1274126177
        x = x xor (x shr 16)
        return (x and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF
    }

    /** Which button (if any) contains pixel (mx,my). Used for clicks. */
    fun buttonAt(mx: Float, my: Float, w: Int, h: Int): TitleAction? {
        val layout = buttonLayout(w, h)
        for (b in BUTTONS.indices) {
            val (bx, by) = layout[b]
            if (mx in bx..(bx + BUTTON_W) && my in by..(by + BUTTON_H)) {
                return BUTTONS[b].action
            }
        }
        return null
    }

    /** Center-x / top-y of each button for the current window size. */
    private fun buttonLayout(w: Int, h: Int): List<Pair<Float, Float>> {
        val winW = w.toFloat()
        val winH = h.toFloat()
        val total = BUTTONS.size * BUTTON_H + (BUTTONS.size - 1) * BUTTON_GAP
        var y = winH * 0.44f - total / 2f
        return BUTTONS.map {
            val bx = winW / 2f - BUTTON_W / 2f
            val by = y
            y += BUTTON_H + BUTTON_GAP
            bx to by
        }
    }

    /**
     * Assemble the title screen for one frame as clip-space vertex data.
     * Note: the first quads painted (background + stars) are drawn first but
     * the pipeline has no depth on the HUD pass — order in the buffer is
     * draw order, so background -> stars -> title -> buttons.
     */
    fun build(state: TitleState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        /* ── Deep-space gradient background ── */
        val bands = 24
        val bandH = winH / bands
        for (i in 0 until bands) {
            val t = i.toFloat() / (bands - 1)
            // top: dark navy-blue; bottom: near-black
            val r = 0.02f + t * 0.01f
            val g = 0.03f + t * 0.015f
            val b = 0.08f + t * 0.02f
            Ui.rect(0f, i * bandH, winW, bandH + 1f, r, g, b)
        }

        /* ── Animated starfield ──
         * Each star has a fixed hash position, a twinkle phase, and a slow
         * horizontal drift. Brightness pulses with time so the sky feels alive. */
        for (i in 0 until STAR_COUNT) {
            val px = hash01(i) * winW
            val py = hash01(i + 1000) * winH * 0.85f + winH * 0.02f
            val phase = hash01(i + 2000) * 2f * PI.toFloat()
            val speed = 0.5f + hash01(i + 3000) * 1.5f
            val drift = (state.time * speed * 2.5f) % winW
            val sx = (px + drift) % winW
            // twinkle: 0.35..1.0 brightness
            val tw = 0.65f + 0.35f * abs(sin(state.time * 1.7f + phase))
            val size = if (hash01(i + 4000) > 0.8f) 2f else 1f
            val bri = (0.5f + 0.5f * tw).coerceIn(0f, 1f)
            Ui.rect(sx, py, size, size, 0.7f * bri, 0.8f * bri, 1f * bri)
        }

        /* ── Title, centered ── */
        val titleY = winH * 0.13f
        val scale = 6f
        // dark drop shadow for depth
        Ui.textCentered(winW / 2f + 3f, titleY + 3f, scale, 0.05f, 0.1f, 0.2f, "STRATA PRIMORDIAL")
        // bright main title
        Ui.textCentered(winW / 2f, titleY, scale, 0.75f, 0.9f, 1f, "STRATA PRIMORDIAL")
        // subtitle accent
        Ui.textCentered(winW / 2f, titleY + scale * 8f, 1.5f, 0.4f, 0.55f, 0.75f, "~ PRIMORDIAL WORLDS ~")

        /* ── Menu buttons ── */
        val layout = buttonLayout(w, h)
        for (i in BUTTONS.indices) {
            val (bx, by) = layout[i]
            val btn = BUTTONS[i]
            val hovered = state.mouseX in bx..(bx + BUTTON_W) && state.mouseY in by..(by + BUTTON_H)

            if (hovered) {
                // bright hover border + lighter fill
                Ui.rect(bx - 2f, by - 2f, BUTTON_W + 4f, BUTTON_H + 4f, 0.8f, 0.9f, 1f)
                Ui.rect(bx, by, BUTTON_W, BUTTON_H, 0.22f, 0.28f, 0.38f)
            } else {
                Ui.rect(bx - 2f, by - 2f, BUTTON_W + 4f, BUTTON_H + 4f, 0.25f, 0.28f, 0.35f)
                Ui.rect(bx, by, BUTTON_W, BUTTON_H, 0.1f, 0.12f, 0.17f)
            }

            val labelScale = 2.4f
            val label = if (btn.wip) "${btn.label} (WIP)" else btn.label
            val textColor = when {
                btn.wip -> Triple(0.45f, 0.5f, 0.6f)
                hovered -> Triple(1f, 1f, 1f)
                else -> Triple(0.85f, 0.9f, 1f)
            }
            Ui.textCentered(
                bx + BUTTON_W / 2f,
                by + BUTTON_H / 2f - 3.5f * labelScale,
                labelScale,
                textColor.first, textColor.second, textColor.third,
                label
            )
        }

        /* ── Version + copyright in opposite corners ── */
        Ui.text(20f, winH - 28f, 1.6f, 0.5f, 0.55f, 0.65f, "Version $GAME_VERSION")
        Ui.textRight(winW - 20f, winH - 28f, 1.6f, 0.5f, 0.55f, 0.65f, COPYRIGHT)

        return Ui.end()
    }
}
