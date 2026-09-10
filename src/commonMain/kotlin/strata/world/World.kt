package strata.world

import strata.ecs.Component
import strata.math.*

/**
 * Block IDs - using shorts to support more than 256 block types
 */
object Blocks {
    const val AIR: Short = 0
    const val STONE: Short = 1
    const val DIRT: Short = 2
    const val GRASS: Short = 3
    const val SAND: Short = 4
    const val WATER: Short = 5
    const val WOOD: Short = 6
    const val LEAVES: Short = 7
    const val BEDROCK: Short = 8
    const val GREENSTONE: Short = 9  // Strata counterpart of redstone
    const val INFERNITE: Short = 10  // Strata counterpart of netherite

    fun isSolid(id: Short): Boolean = id != AIR && id != WATER
    fun isOpaque(id: Short): Boolean = id != AIR && id != WATER && id != LEAVES
}

/**
 * 16x16x16 chunk using 1D array for cache-friendly access
 * Index = (y * 16 + z) * 16 + x
 */
class Chunk(val position: ChunkPos) {
    private val data = ShortArray(4096)  // 16^3
    private var dirty = true

    companion object {
        const val SIZE = 16
        const val HEIGHT = 16
    }

    private fun index(x: Int, y: Int, z: Int): Int =
        (y * SIZE + z) * SIZE + x

    operator fun get(x: Int, y: Int, z: Int): Short {
        if (x !in 0 until SIZE || y !in 0 until HEIGHT || z !in 0 until SIZE) return Blocks.AIR
        return data[index(x, y, z)]
    }

    operator fun set(x: Int, y: Int, z: Int, block: Short) {
        if (x !in 0 until SIZE || y !in 0 until HEIGHT || z !in 0 until SIZE) return
        data[index(x, y, z)] = block
        dirty = true
    }

    fun isDirty(): Boolean = dirty
    fun markClean() { dirty = false }

    fun getRawData(): ShortArray = data
}

/**
 * Infinite world with hashmap-based chunk storage
 */
class World {
    private val chunks = mutableMapOf<ChunkPos, Chunk>()

    fun getChunk(pos: ChunkPos): Chunk? = chunks[pos]

    fun getOrCreateChunk(pos: ChunkPos): Chunk {
        return chunks.getOrPut(pos) { Chunk(pos) }
    }

    fun removeChunk(pos: ChunkPos) {
        chunks.remove(pos)
    }

    fun setBlock(worldX: Int, worldY: Int, worldZ: Int, block: Short) {
        val cx = worldX shr 4
        val cy = worldY shr 4
        val cz = worldZ shr 4
        val lx = worldX and 0xF
        val ly = worldY and 0xF
        val lz = worldZ and 0xF
        val chunk = getOrCreateChunk(ChunkPos(cx, cz))
        chunk[lx, ly, lz] = block
    }

    fun getBlock(worldX: Int, worldY: Int, worldZ: Int): Short {
        if (worldY < 0 || worldY >= 256) return Blocks.AIR
        val cx = worldX shr 4
        val cy = worldY shr 4
        val cz = worldZ shr 4
        val chunk = chunks[ChunkPos(cx, cz)] ?: return Blocks.AIR
        return chunk[worldX and 0xF, worldY and 0xF, worldZ and 0xF]
    }

    fun isSolid(worldX: Int, worldY: Int, worldZ: Int): Boolean =
        Blocks.isSolid(getBlock(worldX, worldY, worldZ))

    fun allChunks(): Map<ChunkPos, Chunk> = chunks.toMap()
}
