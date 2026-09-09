package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryTest {

    @Test
    fun hotbarHasElevenSlots() {
        val inv = PlayerInventory()
        assertEquals(11, inv.hotbar.size)
    }

    @Test
    fun storageHasFiftySlots() {
        val inv = PlayerInventory()
        assertEquals(50, inv.storage.size)
    }

    @Test
    fun hotbarStartsWithDefaultLayout() {
        val inv = PlayerInventory()
        for (i in Hud.HOTBAR.indices) {
            assertEquals(Hud.HOTBAR[i], inv.hotbar[i], "hotbar slot $i should match the default layout")
        }
    }

    @Test
    fun starterStashFillsMostOfStorage() {
        val inv = PlayerInventory()
        val filled = inv.storage.count { it != null }
        assertTrue(filled >= 40, "starter stash should mostly fill storage (filled=$filled)")
        // First slot should hold the first stash item (grass).
        assertEquals(Hud.HotbarItem(BlockId.Grass), inv.storage[0])
    }

    @Test
    fun selectedItemReturnsHotbarContent() {
        val inv = PlayerInventory()
        assertEquals(Hud.HotbarItem(BlockId.Grass), inv.selectedItem(0))
        assertEquals(Hud.HotbarItem(null, food = 8), inv.selectedItem(10))
        // Out-of-range returns null rather than throwing.
        assertNull(inv.selectedItem(99))
    }

    @Test
    fun swapExchangesStorageAndHotbarSlots() {
        val inv = PlayerInventory()
        val hotBefore = inv.hotbar[0]
        // Find a storage slot whose content differs from hotbar slot 0.
        val targetStorage = (0 until 50).first { inv.storage[it] != inv.hotbar[0] }
        val before = inv.storage[targetStorage]
        inv.swap(targetStorage, 0)
        assertEquals(before, inv.hotbar[0], "hotbar slot should now hold the storage item")
        assertEquals(hotBefore, inv.storage[targetStorage], "storage slot should now hold the hotbar item")
        // Swapping back restores the original contents.
        inv.swap(targetStorage, 0)
        assertEquals(hotBefore, inv.hotbar[0])
        assertEquals(before, inv.storage[targetStorage])
    }

    @Test
    fun swapIgnoresOutOfRangeIndexes() {
        val inv = PlayerInventory()
        val before = inv.hotbar[0]
        inv.swap(-1, 0)
        inv.swap(50, 0)
        inv.swap(0, 11)
        assertEquals(before, inv.hotbar[0], "out-of-range swaps must be no-ops")
    }
}
