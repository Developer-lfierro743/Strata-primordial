package strata

/** Actions the mod-list screen can trigger. */
enum class ModListAction {
    TOGGLE_MOD,  // Toggle a mod on/off (index passed via toggleIndex)
    BACK         // Return to native mods hub
}

/**
 * Installed mods list: shows all detected mods with their status
 * (loaded/unloaded) and a toggle button for each.
 *
 * Draws through the shared [Ui] renderer.
 */
object ModListScreen {

    /** State needed for one frame of the mod list. */
    data class ModListState(
        val time: Float,
        val mouseX: Float = -1f,
        val mouseY: Float = -1f,
        /** List of mod names currently detected. */
        val modNames: List<String> = emptyList(),
        /** Parallel list: true = loaded, false = unloaded. */
        val modEnabled: List<Boolean> = emptyList(),
        /** Scroll offset for the list (0 = top). */
        val scrollOffset: Int = 0
    )

    /** Result of a click — action + optional mod index. */
    data class ModListResult(val action: ModListAction, val modIndex: Int = -1)

    private const val ENTRY_H = 36f
    private const val ENTRY_GAP = 4f
    private const val TOGGLE_W = 80f
    private const val TOGGLE_H = 24f
    private const val BACK_W = 160f
    private const val BACK_H = 36f
    private const val MAX_VISIBLE = 12

    /**
     * Hit-test: which mod entry or back button was clicked?
     * Returns a [ModListResult] or null.
     */
    fun hitTest(mx: Float, my: Float, w: Int, h: Int, scrollOffset: Int): ModListResult? {
        val winW = w.toFloat()
        val listX = 60f
        val listW = winW - 120f

        // Check mod entries
        for (i in 0 until MAX_VISIBLE) {
            val ey = 120f + i * (ENTRY_H + ENTRY_GAP)
            val toggleX = listX + listW - TOGGLE_W - 20f
            // Toggle button hit
            if (mx in toggleX..(toggleX + TOGGLE_W) && my in (ey + (ENTRY_H - TOGGLE_H) / 2f)..(ey + (ENTRY_H + TOGGLE_H) / 2f)) {
                return ModListResult(ModListAction.TOGGLE_MOD, scrollOffset + i)
            }
        }

        // Back button
        val backPos = backLayout(w, h)
        if (mx in backPos.first..(backPos.first + BACK_W) &&
            my in backPos.second..(backPos.second + BACK_H)) {
            return ModListResult(ModListAction.BACK)
        }

        return null
    }

    private fun backLayout(w: Int, h: Int): Pair<Float, Float> {
        val winH = h.toFloat()
        return 30f to (winH - 30f - BACK_H)
    }

    /**
     * Assemble the mod list screen for one frame.
     */
    fun build(state: ModListState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        /* ── Dark background ── */
        val bands = 20
        val bandH = winH / bands
        for (i in 0 until bands) {
            val t = i.toFloat() / (bands - 1)
            val r = 0.012f + t * 0.008f
            val g = 0.018f + t * 0.01f
            val b = 0.035f + t * 0.012f
            Ui.rect(0f, i * bandH, winW, bandH + 1f, r, g, b)
        }

        /* ── Title ── */
        val titleY = 30f
        val titleScale = 4f
        Ui.textCentered(winW / 2f + 2f, titleY + 2f, titleScale, 0.0f, 0.0f, 0.0f, "INSTALLED MODS")
        Ui.textCentered(winW / 2f, titleY, titleScale, 0.3f, 0.8f, 0.5f, "INSTALLED MODS")

        /* ── Accent line ── */
        val lineY = titleY + titleScale * 10f + 6f
        Ui.rect(winW / 2f - 80f, lineY, 160f, 2f, 0.3f, 0.7f, 0.4f)

        /* ── Status line ── */
        val loaded = state.modEnabled.count { it }
        val total = state.modNames.size
        Ui.text(60f, lineY + 10f, 1.4f, 0.3f, 0.6f, 0.4f,
            "Loaded: $loaded / $total")

        /* ── Column headers ── */
        val headerY = lineY + 24f
        val listX = 60f
        val listW = winW - 120f
        Ui.text(listX, headerY, 1.4f, 0.4f, 0.5f, 0.6f, "MOD NAME")
        Ui.text(listX + listW - TOGGLE_W - 20f, headerY, 1.4f, 0.4f, 0.5f, 0.6f, "STATUS")

        Ui.rect(listX, headerY + 18f, listW, 1f, 0.15f, 0.25f, 0.35f)

        /* ── Mod entries ── */
        val entryStartY = headerY + 26f
        val visibleCount = minOf(MAX_VISIBLE, state.modNames.size - state.scrollOffset)

        for (i in 0 until visibleCount) {
            val modIdx = state.scrollOffset + i
            val name = state.modNames[modIdx]
            val enabled = state.modEnabled[modIdx]
            val ey = entryStartY + i * (ENTRY_H + ENTRY_GAP)

            // Row background
            val rowHovered = state.mouseX in listX..(listX + listW) &&
                    state.mouseY in ey..(ey + ENTRY_H)
            val bgR = if (rowHovered) 0.08f else 0.04f
            val bgG = if (rowHovered) 0.1f else 0.06f
            val bgB = if (rowHovered) 0.15f else 0.09f
            Ui.rect(listX, ey, listW, ENTRY_H, bgR, bgG, bgB)

            // Mod name
            val nameColor = if (enabled) Triple(0.8f, 0.9f, 1f) else Triple(0.45f, 0.5f, 0.6f)
            Ui.text(listX + 12f, ey + ENTRY_H / 2f - 3.5f * 1.8f, 1.8f,
                nameColor.first, nameColor.second, nameColor.third, name.uppercase())

            // Toggle button
            val toggleX = listX + listW - TOGGLE_W - 20f
            val toggleY = ey + (ENTRY_H - TOGGLE_H) / 2f
            val toggleHovered = state.mouseX in toggleX..(toggleX + TOGGLE_W) &&
                    state.mouseY in toggleY..(toggleY + TOGGLE_H)

            if (enabled) {
                // Green ON toggle
                Ui.rect(toggleX, toggleY, TOGGLE_W, TOGGLE_H, 0.1f, 0.35f, 0.15f)
                Ui.textCentered(toggleX + TOGGLE_W / 2f, toggleY + TOGGLE_H / 2f - 3.5f * 1.4f,
                    1.4f, 0.3f, 0.9f, 0.4f, "LOADED")
            } else {
                // Red OFF toggle
                Ui.rect(toggleX, toggleY, TOGGLE_W, TOGGLE_H, 0.3f, 0.1f, 0.1f)
                Ui.textCentered(toggleX + TOGGLE_W / 2f, toggleY + TOGGLE_H / 2f - 3.5f * 1.4f,
                    1.4f, 0.8f, 0.3f, 0.3f, "UNLOADED")
            }

            if (toggleHovered) {
                Ui.rect(toggleX - 1f, toggleY - 1f, TOGGLE_W + 2f, TOGGLE_H + 2f,
                    0.5f, 0.5f, 0.6f)
            }
        }

        /* ── Scroll indicators ── */
        if (state.scrollOffset > 0) {
            Ui.textCentered(winW / 2f, entryStartY - 16f, 1.5f, 0.4f, 0.5f, 0.6f, "^ MORE ^")
        }
        if (state.scrollOffset + MAX_VISIBLE < state.modNames.size) {
            Ui.textCentered(winW / 2f, entryStartY + visibleCount * (ENTRY_H + ENTRY_GAP) + 4f,
                1.5f, 0.4f, 0.5f, 0.6f, "v MORE v")
        }

        /* ── Empty state ── */
        if (state.modNames.isEmpty()) {
            Ui.textCentered(winW / 2f, winH * 0.45f, 2.5f, 0.4f, 0.45f, 0.55f,
                "NO MODS INSTALLED")
            Ui.textCentered(winW / 2f, winH * 0.45f + 22f, 1.5f, 0.3f, 0.35f, 0.45f,
                "Place .jar or .strata_mod files in the mods/ folder")
        }

        /* ── Back button ── */
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

        /* ── Bottom bar ── */
        Ui.rect(0f, winH - 2f, winW, 2f, 0.2f, 0.5f, 0.3f)
        Ui.text(20f, winH - 24f, 1.2f, 0.3f, 0.4f, 0.5f,
            "Scroll: Up/Down arrows | Toggle: Click button")

        return Ui.end()
    }
}
