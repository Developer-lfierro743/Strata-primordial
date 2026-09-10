package strata

/** Actions the native-mods screen buttons can trigger. */
enum class ModsAction {
    EDITOR,      // Opens the mod code editor
    MODS,        // Shows loaded/unloaded mods list
    BACK         // Return to title screen
}

/**
 * Native Mods hub screen: dark themed with two large buttons
 * (EDITOR and MODS) plus a back button.
 *
 * Draws through the shared [Ui] renderer — no textures or extra
 * pipeline work, same as TitleScreen.
 */
object NativeModsScreen {

    /** Everything the native-mods screen needs for one frame. */
    data class ModsState(
        val time: Float,
        val mouseX: Float = -1f,
        val mouseY: Float = -1f
    )

    /* ── Button layout ── */
    private data class ModsButton(val label: String, val action: ModsAction, val icon: String)

    private val BUTTONS = listOf(
        ModsButton("EDITOR", ModsAction.EDITOR, ">>"),
        ModsButton("MODS", ModsAction.MODS, "[]"),
    )

    private const val BUTTON_W = 400f
    private const val BUTTON_H = 64f
    private const val BUTTON_GAP = 24f
    private const val BACK_W = 160f
    private const val BACK_H = 36f

    /** Which button (if any) contains pixel (mx,my). Used for clicks. */
    fun buttonAt(mx: Float, my: Float, w: Int, h: Int): ModsAction? {
        val layout = buttonLayout(w, h)
        // Main buttons
        for (i in BUTTONS.indices) {
            val (bx, by) = layout[i]
            if (mx in bx..(bx + BUTTON_W) && my in by..(by + BUTTON_H)) {
                return BUTTONS[i].action
            }
        }
        // Back button
        val backLayout = backLayout(w, h)
        if (mx in backLayout.first..(backLayout.first + BACK_W) &&
            my in backLayout.second..(backLayout.second + BACK_H)) {
            return ModsAction.BACK
        }
        return null
    }

    /** Center-x / top-y of each button for the current window size. */
    private fun buttonLayout(w: Int, h: Int): List<Pair<Float, Float>> {
        val winW = w.toFloat()
        val winH = h.toFloat()
        val total = BUTTONS.size * BUTTON_H + (BUTTONS.size - 1) * BUTTON_GAP
        var y = winH * 0.48f - total / 2f
        return BUTTONS.map {
            val bx = winW / 2f - BUTTON_W / 2f
            val by = y
            y += BUTTON_H + BUTTON_GAP
            bx to by
        }
    }

    /** Back button position (bottom-left). */
    private fun backLayout(w: Int, h: Int): Pair<Float, Float> {
        val winH = h.toFloat()
        return 30f to (winH - 30f - BACK_H)
    }

    /**
     * Assemble the native mods screen for one frame as clip-space vertex data.
     * Dark industrial theme — blue-grey accents on near-black background.
     */
    fun build(state: ModsState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        /* ── Dark background gradient ── */
        val bands = 20
        val bandH = winH / bands
        for (i in 0 until bands) {
            val t = i.toFloat() / (bands - 1)
            // top: deep dark blue; bottom: near-black
            val r = 0.015f + t * 0.01f
            val g = 0.02f + t * 0.012f
            val b = 0.04f + t * 0.015f
            Ui.rect(0f, i * bandH, winW, bandH + 1f, r, g, b)
        }

        /* ── Subtle grid pattern (industrial feel) ── */
        for (gx in 0 until (winW / 40f).toInt()) {
            Ui.rect(gx * 40f, 0f, 1f, winH, 0.04f, 0.05f, 0.07f)
        }
        for (gy in 0 until (winH / 40f).toInt()) {
            Ui.rect(0f, gy * 40f, winW, 1f, 0.04f, 0.05f, 0.07f)
        }

        /* ── Title ── */
        val titleY = winH * 0.10f
        val titleScale = 5f
        // Glow/shadow
        Ui.textCentered(winW / 2f + 2f, titleY + 2f, titleScale, 0.0f, 0.0f, 0.0f, "NATIVE MODS")
        // Main title — bright cyan
        Ui.textCentered(winW / 2f, titleY, titleScale, 0.3f, 0.7f, 1f, "NATIVE MODS")
        // Subtitle
        Ui.textCentered(winW / 2f, titleY + titleScale * 8f, 1.5f, 0.25f, 0.5f, 0.7f,
            "~ MODDING API ~")

        /* ── Accent line under title ── */
        val lineY = titleY + titleScale * 10f + 8f
        Ui.rect(winW / 2f - 100f, lineY, 200f, 2f, 0.3f, 0.6f, 1f)

        /* ── Main buttons: EDITOR + MODS ── */
        val layout = buttonLayout(w, h)
        for (i in BUTTONS.indices) {
            val (bx, by) = layout[i]
            val btn = BUTTONS[i]
            val hovered = state.mouseX in bx..(bx + BUTTON_W) && state.mouseY in by..(by + BUTTON_H)

            // Button border glow
            if (hovered) {
                Ui.rect(bx - 3f, by - 3f, BUTTON_W + 6f, BUTTON_H + 6f, 0.3f, 0.6f, 1f)
                Ui.rect(bx, by, BUTTON_W, BUTTON_H, 0.08f, 0.14f, 0.25f)
            } else {
                Ui.rect(bx - 2f, by - 2f, BUTTON_W + 4f, BUTTON_H + 4f, 0.12f, 0.18f, 0.28f)
                Ui.rect(bx, by, BUTTON_W, BUTTON_H, 0.04f, 0.07f, 0.12f)
            }

            // Icon prefix
            val iconScale = 2.8f
            Ui.text(bx + 20f, by + BUTTON_H / 2f - 3.5f * iconScale, iconScale,
                0.3f, 0.7f, 1f, btn.icon)

            // Button label
            val labelScale = 3f
            val labelColor = when {
                hovered -> Triple(1f, 1f, 1f)
                else -> Triple(0.75f, 0.85f, 1f)
            }
            Ui.textCentered(
                bx + BUTTON_W / 2f + 20f,
                by + BUTTON_H / 2f - 3.5f * labelScale,
                labelScale,
                labelColor.first, labelColor.second, labelColor.third,
                btn.label
            )

            // Subtle accent line on right side of button
            Ui.rect(bx + BUTTON_W - 4f, by + 8f, 3f, BUTTON_H - 16f, 0.3f, 0.6f, 1f)
        }

        /* ── Back button (bottom-left) ── */
        val backPos = backLayout(w, h)
        val bx = backPos.first
        val by = backPos.second
        val backHovered = state.mouseX in bx..(bx + BACK_W) && state.mouseY in by..(by + BACK_H)

        if (backHovered) {
            Ui.rect(bx - 2f, by - 2f, BACK_W + 4f, BACK_H + 4f, 0.5f, 0.5f, 0.6f)
            Ui.rect(bx, by, BACK_W, BACK_H, 0.15f, 0.15f, 0.2f)
        } else {
            Ui.rect(bx - 1f, by - 1f, BACK_W + 2f, BACK_H + 2f, 0.2f, 0.2f, 0.25f)
            Ui.rect(bx, by, BACK_W, BACK_H, 0.08f, 0.08f, 0.12f)
        }

        val backLabelColor = if (backHovered) Triple(1f, 1f, 1f) else Triple(0.6f, 0.65f, 0.75f)
        Ui.textCentered(
            bx + BACK_W / 2f,
            by + BACK_H / 2f - 3.5f * 2f,
            2f,
            backLabelColor.first, backLabelColor.second, backLabelColor.third,
            "< BACK"
        )

        /* ── Bottom info bar ── */
        Ui.rect(0f, winH - 2f, winW, 2f, 0.2f, 0.4f, 0.7f)
        Ui.text(20f, winH - 24f, 1.2f, 0.3f, 0.4f, 0.55f, "Strata Modding API v0.1.0")
        Ui.textRight(winW - 20f, winH - 24f, 1.2f, 0.3f, 0.4f, 0.55f,
            "Editor: Write Mods | Mods: Manage")

        return Ui.end()
    }
}
