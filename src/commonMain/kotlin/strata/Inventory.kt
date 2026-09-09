package strata

/**
 * The player's inventory: an 11-slot hotbar (shown in-game, used for placing
 * and eating) plus a 50-slot storage grid (opened with E). Slots are nullable
 * — an empty slot holds nothing. Items are [Hud.HotbarItem]: a placeable
 * block or a food that restores hunger.
 */
class PlayerInventory(
    hotbar: List<Hud.HotbarItem?> = Hud.HOTBAR,
    storageSize: Int = 50
) {
    /** The 11 hotbar slots (indices 0..10, keys 1-9, 0 and -). */
    val hotbar: MutableList<Hud.HotbarItem?> = hotbar.toMutableList()

    /** The 50 storage slots (10x5 grid) opened with E. */
    val storage: MutableList<Hud.HotbarItem?> = MutableList(storageSize) { null }

    init {
        // Starter stash: a healthy spread of blocks + food so the storage
        // isn't empty on a fresh world (48 items, leaving 2 slots free).
        val starter = buildList {
            repeat(10) { add(Hud.HotbarItem(BlockId.Grass)) }
            repeat(10) { add(Hud.HotbarItem(BlockId.Dirt)) }
            repeat(10) { add(Hud.HotbarItem(BlockId.Stone)) }
            repeat(6) { add(Hud.HotbarItem(BlockId.Wood)) }
            repeat(6) { add(Hud.HotbarItem(BlockId.Sand)) }
            repeat(2) { add(Hud.HotbarItem(BlockId.Water)) }
            repeat(2) { add(Hud.HotbarItem(null, drink = 7)) }  // water bottles
            add(Hud.HotbarItem(null, food = 6))   // bread
            add(Hud.HotbarItem(null, food = 8))   // steak
        }
        starter.forEachIndexed { i, item ->
            if (i < storage.size) storage[i] = item
        }
    }

    /** Item in the hotbar slot currently in hand, or null if the slot is empty. */
    fun selectedItem(index: Int): Hud.HotbarItem? = hotbar.getOrNull(index)

    /** Swap a storage slot with a hotbar slot (inventory management). */
    fun swap(storageIndex: Int, hotbarIndex: Int) {
        if (storageIndex !in storage.indices || hotbarIndex !in hotbar.indices) return
        val tmp = storage[storageIndex]
        storage[storageIndex] = hotbar[hotbarIndex]
        hotbar[hotbarIndex] = tmp
    }
}
