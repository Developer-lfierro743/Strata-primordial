package strata

import kotlin.math.floor
import kotlin.jvm.JvmInline

@JvmInline
value class BlockId(val value: UShort) {
    companion object {
        val Air = BlockId(0u)
        val Grass = BlockId(1u)
        val Dirt = BlockId(2u)
        val Stone = BlockId(3u)
        val Wood = BlockId(4u)
        val Water = BlockId(5u)
        val Sand = BlockId(6u)
        val Leaves = BlockId(7u)
        val Log = BlockId(8u)
        val CoalOre = BlockId(9u)
        val IronOre = BlockId(10u)
        val Bedrock = BlockId(11u)
        val Gravel = BlockId(12u)
    }
}

class Chunk(val size: Int = 32) {
    private val blocks = UShortArray(size * size * size)
    private fun index(x: Int, y: Int, z: Int) = (y * size + z) * size + x

    /** Fast copy of block data, safe to hand to a worker thread for meshing. */
    fun snapshot(): UShortArray = blocks.copyOf()

    /** Read a block by 1D index; indexes are pre-validated by callers. */
    fun getByIndex(i: Int): BlockId = BlockId(blocks[i])

    fun get(x: Int, y: Int, z: Int): BlockId =
        if (x !in 0 until size || y !in 0 until size || z !in 0 until size) BlockId.Air
        else BlockId(blocks[index(x, y, z)])

    fun set(x: Int, y: Int, z: Int, block: BlockId) {
        if (x in 0 until size && y in 0 until size && z in 0 until size) {
            blocks[index(x, y, z)] = block.value
        }
    }

    fun generateFlat(height: Int = 8) {
        for (x in 0 until size) for (z in 0 until size) for (y in 0 until height) {
            set(x, y, z, when {
                y == height - 1 -> BlockId.Grass
                y >= height - 3 -> BlockId.Dirt
                else -> BlockId.Stone
            })
        }
    }

    fun raycast(origin: Vec3, direction: Vec3, maxDistance: Float = 8f): BlockHit? {
        var p = origin
        var previous = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
        var distance = 0f
        while (distance <= maxDistance) {
            val current = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
            if (current != previous && get(current.x, current.y, current.z) != BlockId.Air) {
                return BlockHit(current, previous)
            }
            previous = current
            p += direction * 0.05f
            distance += 0.05f
        }
        return null
    }
}
