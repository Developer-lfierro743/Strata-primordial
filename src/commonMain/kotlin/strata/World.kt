package strata

import kotlin.math.abs
import kotlin.math.floor

/** Truncating-free integer division (rounds toward negative infinity). */
fun floorDiv(a: Int, b: Int): Int {
    var r = a / b
    if ((a xor b) < 0 && r * b != a) r--
    return r
}

/** Non-negative remainder, consistent with [floorDiv]. */
fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b

/**
 * A streamed, infinite world of cubic [Chunk]s addressed by (cx, cy, cz).
 *
 * Each chunk is a 32³ cube of blocks. The world is divided into cubic chunks
 * in all three dimensions, allowing independent loading/unloading of vertical
 * slices — critical for caves, deep underground, and sky features.
 *
 * Generation is deterministic per seed, so chunks can be produced on worker
 * threads independently. The chunk map itself is owned by the main thread;
 * worker threads hand back finished [Chunk]s through a [WorldStreamer].
 */
class World(val seed: Int = 0, val size: Int = 32) {
    private val chunks = HashMap<Long, Chunk>()

    /**
     * Encode 3 chunk coordinates into a single Long key.
     * Uses zigzag encoding to handle negative coords, then packs 21 bits per axis.
     * Range per axis: ±1,048,576 — more than enough for infinite worlds.
     */
    fun chunkKey(cx: Int, cy: Int, cz: Int): Long {
        fun zigzag(x: Int): Long = ((x.toLong() shl 1) xor (x.toLong() shr 31)) and 0x1FFFFFL
        return (zigzag(cx) shl 42) or (zigzag(cy) shl 21) or zigzag(cz)
    }

    fun chunkX(key: Long): Int {
        val x = ((key shr 42) and 0x1FFFFFL).toInt()
        return (x ushr 1) xor -(x and 1)
    }

    fun chunkY(key: Long): Int {
        val y = ((key shr 21) and 0x1FFFFFL).toInt()
        return (y ushr 1) xor -(y and 1)
    }

    fun chunkZ(key: Long): Int {
        val z = (key and 0x1FFFFFL).toInt()
        return (z ushr 1) xor -(z and 1)
    }

    fun hasChunk(cx: Int, cy: Int, cz: Int): Boolean = chunks.containsKey(chunkKey(cx, cy, cz))
    fun hasChunk(key: Long): Boolean = chunks.containsKey(key)

    val loadedKeys: Set<Long> get() = chunks.keys

    /** Number of loaded chunks (for the HUD info panel). */
    val loadedChunkCount: Int get() = chunks.size

    // ─── Chunk generation ─────────────────────────────────────────────

    /**
     * Generate a 32³ chunk at cubic chunk coordinates (cx, cy, cz).
     * The chunk covers world y-range [cy * size, (cy + 1) * size).
     */
    fun generateChunk(cx: Int, cy: Int, cz: Int): Chunk {
        val chunk = Chunk(size)
        val yMin = cy * size
        val yMax = yMin + size

        for (lx in 0 until size) {
            for (lz in 0 until size) {
                val wx = cx * size + lx
                val wz = cz * size + lz
                val surfaceH = Terrain.heightAt(seed, wx, wz)
                val beach = surfaceH <= Terrain.SEA_LEVEL + 1

                for (ly in 0 until size) {
                    val wy = yMin + ly

                    // Below world bottom: air (or bedrock at the very bottom)
                    if (wy < Terrain.WORLD_BOTTOM) {
                        chunk.set(lx, ly, lz, BlockId.Air)
                        continue
                    }

                    // Bedrock layer at world bottom
                    if (wy <= Terrain.WORLD_BOTTOM + 1) {
                        chunk.set(lx, ly, lz, BlockId.Bedrock)
                        continue
                    }

                    // Cave carving check (before placing terrain blocks)
                    val isCave = Terrain.hasCave(seed, wx, wy, wz)

                    if (isCave && wy < surfaceH) {
                        // Cave: check for ores on cave walls
                        val ore = Terrain.oreAt(seed, wx, wy, wz)
                        chunk.set(lx, ly, lz, ore ?: BlockId.Air)
                        continue
                    }

                    // Terrain blocks
                    if (wy <= surfaceH) {
                        val block = when {
                            wy == surfaceH -> if (beach) BlockId.Sand else BlockId.Grass
                            wy == surfaceH - 1 -> if (beach) BlockId.Sand else BlockId.Dirt
                            wy >= surfaceH - 3 -> BlockId.Dirt
                            else -> {
                                // Check for ores in stone
                                val ore = Terrain.oreAt(seed, wx, wy, wz)
                                ore ?: BlockId.Stone
                            }
                        }
                        chunk.set(lx, ly, lz, block)
                    } else if (wy <= Terrain.SEA_LEVEL && surfaceH < Terrain.SEA_LEVEL) {
                        // Water fill: low valleys become lakes/oceans
                        chunk.set(lx, ly, lz, BlockId.Water)
                    } else {
                        chunk.set(lx, ly, lz, BlockId.Air)
                    }
                }
            }
        }

        // Tree generation: only for chunks that contain the surface layer
        if (yMin <= Terrain.WORLD_TOP && yMax > 0) {
            generateTrees(chunk, cx, cy, cz)
        }

        return chunk
    }

    /**
     * Place trees on the surface of this chunk. Trees are generated based on
     * deterministic noise — no randomness that could desync across threads.
     *
     * Trees that cross chunk boundaries are handled: if a tree's canopy extends
     * into a neighboring chunk, those leaves are placed into that chunk if loaded.
     */
    private fun generateTrees(chunk: Chunk, cx: Int, cy: Int, cz: Int) {
        val yMin = cy * size
        val yMax = yMin + size

        for (lx in 0 until size) {
            for (lz in 0 until size) {
                val wx = cx * size + lx
                val wz = cz * size + lz

                if (!Terrain.shouldPlaceTree(seed, wx, wz)) continue

                val surfaceH = Terrain.heightAt(seed, wx, wz)
                // Tree base must be on grass in this chunk
                if (surfaceH < yMin || surfaceH >= yMax) continue
                if (chunk.get(lx, surfaceH - yMin, lz) != BlockId.Grass) continue

                // Generate tree blocks
                val treeBlocks = Terrain.treeBlocks(seed, wx, wz)
                for ((dx, blockId, dy) in treeBlocks) {
                    val dz = 0 // trees are centered on their column
                    val treeX = lx + dx
                    val treeY = (surfaceH - yMin) + dy
                    val treeZ = lz + dz

                    // Place within this chunk
                    if (treeX in 0 until size && treeZ in 0 until size && treeY in 0 until size) {
                        if (chunk.get(treeX, treeY, treeZ) == BlockId.Air) {
                            chunk.set(treeX, treeY, treeZ, blockId)
                        }
                    }
                    // Cross-chunk leaves: will be handled when neighbor chunks generate
                }
            }
        }
    }

    // ─── Chunk storage ────────────────────────────────────────────────

    fun putChunk(cx: Int, cy: Int, cz: Int, chunk: Chunk) {
        chunks[chunkKey(cx, cy, cz)] = chunk
    }

    fun getChunk(cx: Int, cy: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cy, cz)]

    fun removeChunk(key: Long) {
        chunks.remove(key)
    }

    // ─── Block access ─────────────────────────────────────────────────

    /**
     * The plane of blocks in the neighbor chunk adjacent in [face]'s
     * direction, or null when that neighbor isn't loaded. Used by the
     * mesher for cross-chunk face culling.
     */
    fun borderSlice(cx: Int, cy: Int, cz: Int, face: BlockFace): UShortArray? {
        val neighbor = when (face) {
            BlockFace.EAST -> getChunk(cx + 1, cy, cz)
            BlockFace.WEST -> getChunk(cx - 1, cy, cz)
            BlockFace.NORTH -> getChunk(cx, cy, cz + 1)
            BlockFace.SOUTH -> getChunk(cx, cy, cz - 1)
            BlockFace.TOP -> getChunk(cx, cy + 1, cz)
            BlockFace.BOTTOM -> getChunk(cx, cy - 1, cz)
        } ?: return null

        return when (face) {
            BlockFace.EAST -> planeX(neighbor, 0)
            BlockFace.WEST -> planeX(neighbor, size - 1)
            BlockFace.NORTH -> planeZ(neighbor, 0)
            BlockFace.SOUTH -> planeZ(neighbor, size - 1)
            BlockFace.TOP -> planeY(neighbor, 0)
            BlockFace.BOTTOM -> planeY(neighbor, size - 1)
        }
    }

    private fun planeX(chunk: Chunk, x: Int): UShortArray {
        val out = UShortArray(size * size)
        var i = 0
        for (y in 0 until size) for (z in 0 until size) {
            out[i++] = chunk.getByIndex((y * size + z) * size + x).value
        }
        return out
    }

    private fun planeZ(chunk: Chunk, z: Int): UShortArray {
        val out = UShortArray(size * size)
        var i = 0
        for (x in 0 until size) for (y in 0 until size) {
            out[i++] = chunk.getByIndex((y * size + z) * size + x).value
        }
        return out
    }

    private fun planeY(chunk: Chunk, y: Int): UShortArray {
        val out = UShortArray(size * size)
        var i = 0
        for (x in 0 until size) for (z in 0 until size) {
            out[i++] = chunk.getByIndex((y * size + z) * size + x).value
        }
        return out
    }

    fun worldToChunkX(wx: Int): Int = floorDiv(wx, size)
    fun worldToChunkY(wy: Int): Int = floorDiv(wy, size)
    fun worldToChunkZ(wz: Int): Int = floorDiv(wz, size)

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockId {
        val cx = floorDiv(wx, size)
        val cy = floorDiv(wy, size)
        val cz = floorDiv(wz, size)
        return getChunk(cx, cy, cz)?.get(floorMod(wx, size), floorMod(wy, size), floorMod(wz, size)) ?: BlockId.Air
    }

    /** Sets a block in world coordinates. Returns false if nothing changed. */
    fun setBlock(wx: Int, wy: Int, wz: Int, block: BlockId): Boolean {
        val cx = floorDiv(wx, size)
        val cy = floorDiv(wy, size)
        val cz = floorDiv(wz, size)
        val chunk = getChunk(cx, cy, cz) ?: return false
        val lx = floorMod(wx, size)
        val ly = floorMod(wy, size)
        val lz = floorMod(wz, size)
        if (chunk.get(lx, ly, lz) == block) return false
        chunk.set(lx, ly, lz, block)
        return true
    }

    /** World-space raycast against loaded blocks. */
    fun raycast(origin: Vec3, direction: Vec3, maxDistance: Float = 8f): BlockHit? {
        var p = origin
        var previous = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
        var distance = 0f
        while (distance <= maxDistance) {
            val current = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
            if (current != previous && getBlock(current.x, current.y, current.z) != BlockId.Air) {
                return BlockHit(current, previous)
            }
            previous = current
            p += direction * 0.05f
            distance += 0.05f
        }
        return null
    }
}
