package strata

/**
 * The inventory overlay (opened with E): a 10x5 grid of 50 storage slots
 * above the 11-slot hotbar, all inside a framed panel. Clicking a hotbar slot
 * selects it; clicking a storage slot swaps it with the selected hotbar slot.
 * Drawn through the shared [Ui] renderer like every other screen.
 */
object InventoryScreen {

    const val COLS = 10
    const val ROWS = 5
    const val STORAGE_SIZE = COLS * ROWS   // 50

    const val SLOT = 36f       // storage slot size
    const val GAP = 4f
    const val HOTBAR_SLOT = 42f
    const val HOTBAR_GAP = 4f
    const val HOTBAR_SIZE = 11

    private const val PANEL_W = 640f
    private const val PANEL_H = 352f

    /** Everything the inventory needs to draw for one frame. */
    data class InventoryState(
        val storage: List<Hud.HotbarItem?>,
        val hotbar: List<Hud.HotbarItem?>,
        val selectedSlot: Int,
        /** Cursor position in window pixels, for hover highlighting. */
        val mouseX: Float = -1f,
        val mouseY: Float = -1f
    )

    private fun panelX(w: Int): Float = (w - PANEL_W) / 2f
    private fun panelY(h: Int): Float = (h - PANEL_H) / 2f

    /** Top-left of the 10x5 storage grid. */
    private fun gridOrigin(w: Int, h: Int): Pair<Float, Float> {
        val gridW = COLS * SLOT + (COLS - 1) * GAP
        return (panelX(w) + (PANEL_W - gridW) / 2f) to (panelY(h) + 58f)
    }

    /** Top-left of the hotbar row (inside the panel). */
    private fun hotbarOrigin(w: Int, h: Int): Pair<Float, Float> {
        val (gx, gy) = gridOrigin(w, h)
        val gridH = ROWS * SLOT + (ROWS - 1) * GAP
        val hotbarW = HOTBAR_SIZE * HOTBAR_SLOT + (HOTBAR_SIZE - 1) * HOTBAR_GAP
        return (panelX(w) + (PANEL_W - hotbarW) / 2f) to (gy + gridH + 18f)
    }

    /** Which storage slot (0..49) contains pixel (mx,my), or null. */
    fun storageIndexAt(mx: Float, my: Float, w: Int, h: Int): Int? {
        val (gx, gy) = gridOrigin(w, h)
        val col = ((mx - gx) / (SLOT + GAP)).toInt()
        val row = ((my - gy) / (SLOT + GAP)).toInt()
        if (col in 0 until COLS && row in 0 until ROWS) {
            val x = gx + col * (SLOT + GAP)
            val y = gy + row * (SLOT + GAP)
            if (mx in x..(x + SLOT) && my in y..(y + SLOT)) return row * COLS + col
        }
        return null
    }

    /** Which hotbar slot (0..10) contains pixel (mx,my), or null. */
    fun hotbarIndexAt(mx: Float, my: Float, w: Int, h: Int): Int? {
        val (hx, hy) = hotbarOrigin(w, h)
        val i = ((mx - hx) / (HOTBAR_SLOT + HOTBAR_GAP)).toInt()
        if (i in 0 until HOTBAR_SIZE) {
            val x = hx + i * (HOTBAR_SLOT + HOTBAR_GAP)
            if (mx in x..(x + HOTBAR_SLOT) && my in hy..(hy + HOTBAR_SLOT)) return i
        }
        return null
    }

    /** Assemble the inventory overlay for one frame as clip-space vertex data. */
    fun build(state: InventoryState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH
        val px = panelX(w)
        val py = panelY(h)

        /* ── Panel frame + backdrop ── */
        Ui.rect(px - 2f, py - 2f, PANEL_W + 4f, PANEL_H + 4f, 0.3f, 0.34f, 0.42f)
        Ui.rect(px, py, PANEL_W, PANEL_H, 0.07f, 0.08f, 0.11f)

        /* ── Title + hint ── */
        Ui.textCentered(px + PANEL_W / 2f, py + 16f, 2.2f, 0.85f, 0.9f, 1f, "INVENTORY")
        Ui.textCentered(px + PANEL_W / 2f, py + 40f, 1.2f, 0.5f, 0.55f, 0.65f, "CLICK A SLOT TO SWAP WITH SELECTED HOTBAR SLOT")

        /* ── Storage grid (50 slots) ── */
        val (gx, gy) = gridOrigin(w, h)
        for (i in 0 until STORAGE_SIZE) {
            val row = i / COLS
            val col = i % COLS
            val x = gx + col * (SLOT + GAP)
            val y = gy + row * (SLOT + GAP)
            val hovered = state.mouseX in x..(x + SLOT) && state.mouseY in y..(y + SLOT)
            if (hovered) {
                Ui.rect(x - 2f, y - 2f, SLOT + 4f, SLOT + 4f, 0.8f, 0.9f, 1f)
            } else {
                Ui.rect(x - 2f, y - 2f, SLOT + 4f, SLOT + 4f, 0.22f, 0.24f, 0.3f)
            }
            Ui.rect(x, y, SLOT, SLOT, 0.12f, 0.13f, 0.18f)
            state.storage.getOrNull(i)?.let { item ->
                Hud.itemIcon(x + SLOT / 2f, y + SLOT / 2f, SLOT - 10f, item)
            }
        }

        /* ── Hotbar row (11 slots, selection highlighted) ── */
        val (hbx, hby) = hotbarOrigin(w, h)
        for (i in 0 until HOTBAR_SIZE) {
            val x = hbx + i * (HOTBAR_SLOT + HOTBAR_GAP)
            Ui.rect(x - 2f, hby - 2f, HOTBAR_SLOT + 4f, HOTBAR_SLOT + 4f, 0.22f, 0.24f, 0.3f)
            Ui.rect(x, hby, HOTBAR_SLOT, HOTBAR_SLOT, 0.12f, 0.13f, 0.18f)
            state.hotbar.getOrNull(i)?.let { item ->
                Hud.itemIcon(x + HOTBAR_SLOT / 2f, hby + HOTBAR_SLOT / 2f, HOTBAR_SLOT - 22f, item)
            }
            if (i == state.selectedSlot) {
                Ui.rect(x - 2f, hby - 2f, HOTBAR_SLOT + 4f, 2f, 1f, 1f, 1f)
                Ui.rect(x - 2f, hby + HOTBAR_SLOT, HOTBAR_SLOT + 4f, 2f, 1f, 1f, 1f)
                Ui.rect(x - 2f, hby - 2f, 2f, HOTBAR_SLOT + 4f, 1f, 1f, 1f)
                Ui.rect(x + HOTBAR_SLOT, hby - 2f, 2f, HOTBAR_SLOT + 4f, 1f, 1f, 1f)
            }
        }
        val hotkeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-")
        for (i in 0 until HOTBAR_SIZE) {
            Ui.text(hbx + i * (HOTBAR_SLOT + HOTBAR_GAP) + 3f, hby + HOTBAR_SLOT + 5f, 1.2f, 0.75f, 0.78f, 0.85f, hotkeys[i])
        }

        return Ui.end()
    }
}
