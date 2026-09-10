package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryScreenTest {

    private val W = 1280
    private val H = 720

    @Test
    fun buildProducesValidVertexData() {
        val inv = PlayerInventory()
        val data = InventoryScreen.build(
            InventoryScreen.InventoryState(
                storage = inv.storage,
                hotbar = inv.hotbar,
                selectedSlot = 0
            ),
            W, H
        )
        assertTrue(data.isNotEmpty(), "inventory should draw something")
        assertEquals(0, data.size % 7, "inventory vertex data must be a multiple of 7 floats")
    }

    @Test
    fun storageHitTestingMapsGridToIndexes() {
        val inv = PlayerInventory()
        // Slot 0 sits at the grid origin; slot 49 at the far corner.
        // Recompute the layout the same way the screen does.
        val gridW = InventoryScreen.COLS * InventoryScreen.SLOT + (InventoryScreen.COLS - 1) * InventoryScreen.GAP
        val gridH = InventoryScreen.ROWS * InventoryScreen.SLOT + (InventoryScreen.ROWS - 1) * InventoryScreen.GAP
        val panelW = 640f
        val panelH = 352f
        val px = (W - panelW) / 2f
        val py = (H - panelH) / 2f
        val gx = px + (panelW - gridW) / 2f
        val gy = py + 58f

        assertEquals(0, InventoryScreen.storageIndexAt(gx + 5f, gy + 5f, W, H), "top-left slot should be 0")
        // Slot 49 = row 4, col 9.
        val x49 = gx + 9 * (InventoryScreen.SLOT + InventoryScreen.GAP)
        val y49 = gy + 4 * (InventoryScreen.SLOT + InventoryScreen.GAP)
        assertEquals(49, InventoryScreen.storageIndexAt(x49 + 5f, y49 + 5f, W, H), "bottom-right slot should be 49")
        // Between slots (in the gap) and outside the grid must be null.
        val betweenX = gx + InventoryScreen.SLOT + InventoryScreen.GAP / 2f
        assertNull(InventoryScreen.storageIndexAt(betweenX, gy + 5f, W, H), "gaps between slots are not slots")
        assertNull(InventoryScreen.storageIndexAt(gx - 10f, gy - 10f, W, H), "outside the grid is not a slot")
    }

    @Test
    fun hotbarHitTestingMapsToSlotIndexes() {
        val hotbarW = InventoryScreen.HOTBAR_SIZE * InventoryScreen.HOTBAR_SLOT +
            (InventoryScreen.HOTBAR_SIZE - 1) * InventoryScreen.HOTBAR_GAP
        val panelW = 640f
        val px = (W - panelW) / 2f
        val py = (H - 352f) / 2f
        val gridW = InventoryScreen.COLS * InventoryScreen.SLOT + (InventoryScreen.COLS - 1) * InventoryScreen.GAP
        val gridH = InventoryScreen.ROWS * InventoryScreen.SLOT + (InventoryScreen.ROWS - 1) * InventoryScreen.GAP
        val gx = px + (panelW - gridW) / 2f
        val gy = py + 58f
        val hx = px + (panelW - hotbarW) / 2f
        val hy = gy + gridH + 18f

        assertEquals(0, InventoryScreen.hotbarIndexAt(hx + 5f, hy + 5f, W, H), "first hotbar slot should be 0")
        val x10 = hx + 10 * (InventoryScreen.HOTBAR_SLOT + InventoryScreen.HOTBAR_GAP)
        assertEquals(10, InventoryScreen.hotbarIndexAt(x10 + 5f, hy + 5f, W, H), "last hotbar slot should be 10")
        assertNull(InventoryScreen.hotbarIndexAt(hx - 10f, hy - 10f, W, H), "outside the hotbar is not a slot")
    }
}
