package strata

/**
 * A block mesh. [floatData] holds interleaved x,y,z,r,g,b,material;
 * [vertexCount] is floatData.size / 7.
 */
class ChunkMesh(
    val floatData: FloatArray,
    val vertexCount: Int,
    /** LOD level: 0 = full detail, 1 = half detail, 2 = quarter detail */
    val lodLevel: Int = 0
)

/** LOD levels for iGPU optimization */
enum class LodLevel(val step: Int, val label: String) {
    /** Full detail: every face rendered. Best quality, most vertices. */
    FULL(1, "LOD0"),
    /** Half detail: render every 2nd block. 4x fewer vertices. */
    HALF(2, "LOD1"),
    /** Quarter detail: render every 4th block. 16x fewer vertices. */
    QUARTER(4, "LOD2"),
    /** Eighth detail: render every 8th block. 64x fewer vertices. Far terrain only. */
    EIGHTH(8, "LOD3");

    companion object {
        /** Choose LOD based on distance from player (in chunks). */
        fun forDistance(distanceChunks: Int): LodLevel = when {
            distanceChunks <= 2 -> FULL
            distanceChunks <= 5 -> HALF
            distanceChunks <= 10 -> QUARTER
            else -> EIGHTH
        }
    }
}

enum class BlockFace(val normalX: Int, val normalY: Int, val normalZ: Int) {
    NORTH(0, 0, 1),
    SOUTH(0, 0, -1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    TOP(0, 1, 0),
    BOTTOM(0, -1, 0)
}

object ChunkMesher {
    private const val FLOATS_PER_VERTEX = 7

    /**
     * The water surface is drawn this far below the block grid so the shore
     * bank rises above the water line, like Minecraft's sunken water.
     */
    private const val WATER_SURFACE_OFFSET = -0.2f

    /** Per-vertex material flag (7th float), read by the vertex shader. */
    const val MATERIAL_SOLID = 0f
    const val MATERIAL_WATER = 1f

    /**
     * The base RGB for a block. Shared with the HUD so hotbar icons, the
     * minimap, and the world all use the exact same palette.
     */
    fun blockColor(block: BlockId): Triple<Float, Float, Float> = when (block) {
        BlockId.Grass -> Triple(0.2f, 0.75f, 0.25f)
        BlockId.Dirt -> Triple(0.5f, 0.35f, 0.2f)
        BlockId.Stone -> Triple(0.6f, 0.6f, 0.6f)
        BlockId.Wood -> Triple(0.55f, 0.4f, 0.15f)
        BlockId.Sand -> Triple(0.85f, 0.78f, 0.55f)
        BlockId.Water -> Triple(0.15f, 0.4f, 0.9f)
        BlockId.Leaves -> Triple(0.15f, 0.6f, 0.15f)
        BlockId.Log -> Triple(0.45f, 0.3f, 0.12f)
        BlockId.CoalOre -> Triple(0.35f, 0.35f, 0.35f)
        BlockId.IronOre -> Triple(0.65f, 0.55f, 0.45f)
        BlockId.Bedrock -> Triple(0.2f, 0.2f, 0.2f)
        BlockId.Gravel -> Triple(0.55f, 0.52f, 0.5f)
        else -> Triple(0.8f, 0.8f, 0.8f)
    }

    /** Mesh from a chunk; treats everything outside the chunk as air. */
    fun generateMesh(chunk: Chunk, lodLevel: Int = 0): ChunkMesh =
        generateMesh(chunk.size, chunk.snapshot(), lodLevel) { _, _, _ -> BlockId.Air }

    /**
     * Mesh a chunk from an immutable block snapshot (safe for worker threads).
     * [neighbor] resolves the block at a position given relative to this
     * chunk's origin, which may fall outside [0, size); a face against a
     * solid block there is culled. Default neighbor returns air (no
     * cross-chunk culling).
     *
     * @param lodLevel LOD level: 0=full, 1=half, 2=quarter, 3=eighth
     */
    fun generateMesh(
        size: Int,
        blocks: UShortArray,
        lodLevel: Int = 0,
        neighbor: (nx: Int, ny: Int, nz: Int) -> BlockId = { _, _, _ -> BlockId.Air }
    ): ChunkMesh {
        val lod = when {
            lodLevel <= 0 -> LodLevel.FULL
            lodLevel == 1 -> LodLevel.HALF
            lodLevel == 2 -> LodLevel.QUARTER
            else -> LodLevel.EIGHTH
        }
        val step = lod.step

        var buffer = FloatArray(1024)
        var offset = 0

        fun ensure(capacity: Int) {
            if (capacity > buffer.size) {
                var newSize = buffer.size
                while (newSize < capacity) newSize = newSize * 2
                buffer = buffer.copyOf(newSize)
            }
        }

        fun index(x: Int, y: Int, z: Int) = (y * size + z) * size + x

        // For LOD > 0, we step through blocks at intervals
        // This massively reduces vertex count for distant chunks
        val lodSize = (size / step) * step // round down to multiple of step

        for (x in 0 until size step step) {
            for (y in 0 until size step step) {
                for (z in 0 until size step step) {
                    // For LOD > 0, use the block at the start of this LOD cell
                    val block = BlockId(blocks[index(x, y, z)])
                    if (block == BlockId.Air) continue

                    // Water renders as a single sunken surface plane: only the top
                    // face is drawn, and only where the block above is air (deeper
                    // water is skipped). No side/bottom faces are emitted, and water
                    // never occludes solid blocks (handled below) so lake beds and
                    // sandy banks stay visible under the surface.
                    if (block == BlockId.Water) {
                        val checkY = (y + step).coerceAtMost(size - 1)
                        val above = if (checkY < size) BlockId(blocks[index(x, checkY, z)]) else neighbor(x, checkY, z)
                        if (above == BlockId.Air) {
                            ensure(offset + 36)
                            // Lower the surface 0.2 blocks so the shore bank shows
                            // above the water like Minecraft's sunken water line.
                            // For LOD, scale the face size by step
                            offset = addFaceVertices(
                                buffer, offset,
                                x.toFloat(), y.toFloat() + WATER_SURFACE_OFFSET, z.toFloat(),
                                BlockFace.TOP, 0.15f, 0.4f, 0.9f, MATERIAL_WATER,
                                step.toFloat()
                            )
                        }
                        continue
                    }

                    val (r, g, b) = blockColor(block)

                    for (face in BlockFace.entries) {
                        val nx = x + face.normalX * step
                        val ny = y + face.normalY * step
                        val nz = z + face.normalZ * step

                        val neighborBlock = if (nx in 0 until size && ny in 0 until size && nz in 0 until size) {
                            BlockId(blocks[index(nx, ny, nz)])
                        } else {
                            neighbor(nx, ny, nz)
                        }

                        // Faces exposed to air are drawn. Water doesn't occlude:
                        // lake beds and banks must show beneath/beside the water
                        // plane, since water itself is only a thin surface.
                        if (neighborBlock == BlockId.Air || neighborBlock == BlockId.Water) {
                            ensure(offset + 36)
                            // Classic Minecraft cube shading: bright tops, dim bottoms,
                            // medium sides — gives the terrain depth instead of flat slabs.
                            val shade = when (face) {
                                BlockFace.TOP -> 1.0f
                                BlockFace.BOTTOM -> 0.5f
                                BlockFace.NORTH, BlockFace.SOUTH -> 0.8f
                                BlockFace.EAST, BlockFace.WEST -> 0.6f
                            }
                            offset = addFaceVertices(
                                buffer, offset,
                                x.toFloat(), y.toFloat(), z.toFloat(),
                                face, r * shade, g * shade, b * shade, MATERIAL_SOLID,
                                step.toFloat()
                            )
                        }
                    }
                }
            }
        }

        val result = buffer.copyOf(offset)
        return ChunkMesh(result, offset / FLOATS_PER_VERTEX, lodLevel)
    }

    private fun addFaceVertices(
        buffer: FloatArray,
        offset: Int,
        x: Float, y: Float, z: Float,
        face: BlockFace,
        r: Float, g: Float, b: Float,
        material: Float,
        scale: Float = 1f
    ): Int {
        var idx = offset
        fun vertex(vx: Float, vy: Float, vz: Float) {
            buffer[idx++] = x + vx * scale
            buffer[idx++] = y + vy * scale
            buffer[idx++] = z + vz * scale
            buffer[idx++] = r
            buffer[idx++] = g
            buffer[idx++] = b
            buffer[idx++] = material
        }

        when (face) {
            BlockFace.TOP -> {
                vertex(0f, 1f, 1f); vertex(1f, 1f, 1f); vertex(1f, 1f, 0f)
                vertex(0f, 1f, 1f); vertex(1f, 1f, 0f); vertex(0f, 1f, 0f)
            }
            BlockFace.BOTTOM -> {
                vertex(0f, 0f, 0f); vertex(1f, 0f, 0f); vertex(1f, 0f, 1f)
                vertex(0f, 0f, 0f); vertex(1f, 0f, 1f); vertex(0f, 0f, 1f)
            }
            BlockFace.NORTH -> {
                vertex(0f, 0f, 1f); vertex(1f, 0f, 1f); vertex(1f, 1f, 1f)
                vertex(0f, 0f, 1f); vertex(1f, 1f, 1f); vertex(0f, 1f, 1f)
            }
            BlockFace.SOUTH -> {
                vertex(1f, 0f, 0f); vertex(0f, 0f, 0f); vertex(0f, 1f, 0f)
                vertex(1f, 0f, 0f); vertex(0f, 1f, 0f); vertex(1f, 1f, 0f)
            }
            BlockFace.EAST -> {
                vertex(1f, 0f, 1f); vertex(1f, 0f, 0f); vertex(1f, 1f, 0f)
                vertex(1f, 0f, 1f); vertex(1f, 1f, 0f); vertex(1f, 1f, 1f)
            }
            BlockFace.WEST -> {
                vertex(0f, 0f, 0f); vertex(0f, 0f, 1f); vertex(0f, 1f, 1f)
                vertex(0f, 0f, 0f); vertex(0f, 1f, 1f); vertex(0f, 1f, 0f)
            }
        }
        return idx
    }
}
