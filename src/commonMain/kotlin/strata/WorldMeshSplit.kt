package strata

/**
 * Builds the world's two GPU vertex buffers (opaque + transparent water) from
 * per-chunk meshes, and records each chunk's vertex range within them.
 *
 * The renderer draws one range per chunk using `vkCmdDraw`'s firstVertex
 * offset, which lets it frustum-cull whole chunks without rebuilding the GPU
 * buffer every frame. Lives in common code so it is unit-testable and stays
 * in sync with [ChunkMesher]'s MATERIAL_* constants.
 *
 * Handles cubic chunks: each chunk has (x, y, z) world-space offsets.
 *
 * Supports incremental updates: when a chunk mesh changes, only that chunk's
 * portion of the buffer is rebuilt, not the entire world buffer.
 */
object WorldMeshSplit {

    /** A chunk's slice of the opaque + water buffers, in vertices.
     *  [minX]/[minY]/[minZ] are the world-space AABB origin — precomputed
     *  so the per-frame frustum culling does no bit-shifting. */
    data class ChunkRange(
        val key: Long,
        val solidStart: Int, val solidCount: Int,
        val waterStart: Int, val waterCount: Int,
        val minX: Float, val minY: Float, val minZ: Float,
        /** LOD level for this chunk (0=full, 1=half, 2=quarter, 3=eighth) */
        val lodLevel: Int = 0
    )

    /** The two GPU buffers plus the per-chunk ranges after a rebuild. */
    data class Split(
        val solid: FloatArray,
        val water: FloatArray,
        val ranges: List<ChunkRange>
    )

    /**
     * Result of an incremental update — contains the updated ranges and
     * which portions of the buffers changed.
     */
    data class IncrementalResult(
        val solid: FloatArray,
        val water: FloatArray,
        val ranges: List<ChunkRange>,
        /** Byte offset into solid buffer where changes start */
        val solidDirtyOffset: Int,
        /** Byte length of changes in solid buffer */
        val solidDirtyLength: Int,
        /** Byte offset into water buffer where changes start */
        val waterDirtyOffset: Int,
        /** Byte length of changes in water buffer */
        val waterDirtyLength: Int
    )

    /**
     * Translate each mesh to world space (chunk origin), split its vertices
     * by material flag, append to the solid/water buffers and record ranges.
     *
     * @param chunkSize the size of each chunk (e.g. 32)
     */
    fun split(entries: List<Pair<Long, ChunkMesh>>, chunkSize: Int): Split {
        var solidTotal = 0
        var waterTotal = 0
        for ((_, m) in entries) {
            var i = 6
            while (i < m.floatData.size) {
                if (m.floatData[i] > 0.5f) waterTotal++ else solidTotal++
                i += 7
            }
        }
        val solid = FloatArray(solidTotal * 7)
        val water = FloatArray(waterTotal * 7)
        val ranges = ArrayList<ChunkRange>(entries.size)
        var so = 0
        var wo = 0
        for ((key, m) in entries) {
            // Zigzag decode the chunk coordinates from the key
            fun decodeChunkCoord(bits: Long, shift: Int): Int {
                val raw = ((bits shr shift) and 0x1FFFFFL).toInt()
                return (raw ushr 1) xor -(raw and 1)
            }
            val cx = decodeChunkCoord(key, 42)
            val cy = decodeChunkCoord(key, 21)
            val cz = decodeChunkCoord(key, 0)
            val ox = cx * chunkSize
            val oy = cy * chunkSize
            val oz = cz * chunkSize
            val solidStart = so
            val waterStart = wo
            var i = 0
            while (i < m.floatData.size) {
                val isWater = m.floatData[i + 6] > 0.5f
                if (isWater) {
                    water[wo++] = m.floatData[i++] + ox
                    water[wo++] = m.floatData[i++] + oy
                    water[wo++] = m.floatData[i++] + oz
                    water[wo++] = m.floatData[i++]
                    water[wo++] = m.floatData[i++]
                    water[wo++] = m.floatData[i++]
                    water[wo++] = m.floatData[i++]
                } else {
                    solid[so++] = m.floatData[i++] + ox
                    solid[so++] = m.floatData[i++] + oy
                    solid[so++] = m.floatData[i++] + oz
                    solid[so++] = m.floatData[i++]
                    solid[so++] = m.floatData[i++]
                    solid[so++] = m.floatData[i++]
                    solid[so++] = m.floatData[i++]
                }
            }
            // Ranges are in VERTICES (7 floats each).
            ranges.add(ChunkRange(
                key,
                solidStart / 7, (so - solidStart) / 7,
                waterStart / 7, (wo - waterStart) / 7,
                ox.toFloat(), oy.toFloat(), oz.toFloat(),
                m.lodLevel
            ))
        }
        return Split(solid, water, ranges)
    }

    /**
     * Incremental update: only rebuild the changed chunks in the buffer.
     * This is much faster than rebuilding the entire buffer when only a few
     * chunks change.
     *
     * @param currentSplit The current buffer state
     * @param changedKeys Set of chunk keys that have new meshes
     * @param newMeshes Map of chunk key to new mesh data
     * @param chunkSize the size of each chunk (e.g. 32)
     * @return IncrementalResult with updated buffers and dirty regions
     */
    fun incrementalUpdate(
        currentSplit: Split,
        changedKeys: Set<Long>,
        newMeshes: Map<Long, ChunkMesh>,
        chunkSize: Int
    ): IncrementalResult {
        if (changedKeys.isEmpty()) {
            return IncrementalResult(
                currentSplit.solid, currentSplit.water, currentSplit.ranges,
                0, 0, 0, 0
            )
        }

        // Build a map of key -> range for quick lookup
        val rangeMap = HashMap<Long, ChunkRange>()
        for (range in currentSplit.ranges) {
            rangeMap[range.key] = range
        }

        // Calculate total size needed for new buffers
        var newSolidSize = 0
        var newWaterSize = 0

        // First pass: calculate sizes
        for (range in currentSplit.ranges) {
            if (range.key in changedKeys) {
                // This chunk is being replaced — use new mesh size
                val newMesh = newMeshes[range.key]
                if (newMesh != null) {
                    var solidCount = 0
                    var waterCount = 0
                    var i = 6
                    while (i < newMesh.floatData.size) {
                        if (newMesh.floatData[i] > 0.5f) waterCount++ else solidCount++
                        i += 7
                    }
                    newSolidSize += solidCount * 7
                    newWaterSize += waterCount * 7
                }
            } else {
                // Keep existing range
                newSolidSize += range.solidCount * 7
                newWaterSize += range.waterCount * 7
            }
        }

        // Allocate new buffers
        val newSolid = FloatArray(newSolidSize)
        val newWater = FloatArray(newWaterSize)
        val newRanges = ArrayList<ChunkRange>(currentSplit.ranges.size)

        var so = 0
        var wo = 0
        var minSolidDirty = Int.MAX_VALUE
        var maxSolidDirty = 0
        var minWaterDirty = Int.MAX_VALUE
        var maxWaterDirty = 0

        // Second pass: copy data
        for (range in currentSplit.ranges) {
            if (range.key in changedKeys) {
                // Replace with new mesh
                val newMesh = newMeshes[range.key]
                if (newMesh != null) {
                    val solidStart = so
                    val waterStart = wo

                    // Decode chunk coordinates
                    fun decodeChunkCoord(bits: Long, shift: Int): Int {
                        val raw = ((bits shr shift) and 0x1FFFFFL).toInt()
                        return (raw ushr 1) xor -(raw and 1)
                    }
                    val cx = decodeChunkCoord(range.key, 42)
                    val cy = decodeChunkCoord(range.key, 21)
                    val cz = decodeChunkCoord(range.key, 0)
                    val ox = cx * chunkSize
                    val oy = cy * chunkSize
                    val oz = cz * chunkSize

                    var i = 0
                    while (i < newMesh.floatData.size) {
                        val isWater = newMesh.floatData[i + 6] > 0.5f
                        if (isWater) {
                            newWater[wo++] = newMesh.floatData[i++] + ox
                            newWater[wo++] = newMesh.floatData[i++] + oy
                            newWater[wo++] = newMesh.floatData[i++] + oz
                            newWater[wo++] = newMesh.floatData[i++]
                            newWater[wo++] = newMesh.floatData[i++]
                            newWater[wo++] = newMesh.floatData[i++]
                            newWater[wo++] = newMesh.floatData[i++]
                        } else {
                            newSolid[so++] = newMesh.floatData[i++] + ox
                            newSolid[so++] = newMesh.floatData[i++] + oy
                            newSolid[so++] = newMesh.floatData[i++] + oz
                            newSolid[so++] = newMesh.floatData[i++]
                            newSolid[so++] = newMesh.floatData[i++]
                            newSolid[so++] = newMesh.floatData[i++]
                            newSolid[so++] = newMesh.floatData[i++]
                        }
                    }

                    // Track dirty regions
                    val solidBytes = (so - solidStart) * 4 // 4 bytes per float
                    val waterBytes = (wo - waterStart) * 4
                    if (solidBytes > 0) {
                        minSolidDirty = minSolidDirty.coerceAtMost(solidStart * 4)
                        maxSolidDirty = maxSolidDirty.coerceAtLeast(so * 4)
                    }
                    if (waterBytes > 0) {
                        minWaterDirty = minWaterDirty.coerceAtMost(waterStart * 4)
                        maxWaterDirty = maxWaterDirty.coerceAtLeast(wo * 4)
                    }

                    newRanges.add(ChunkRange(
                        range.key,
                        solidStart / 7, (so - solidStart) / 7,
                        waterStart / 7, (wo - waterStart) / 7,
                        range.minX, range.minY, range.minZ,
                        newMesh.lodLevel
                    ))
                }
            } else {
                // Copy existing data unchanged
                val solidStart = so
                val waterStart = wo

                // Copy solid vertices (manual arraycopy for Kotlin/Native compat)
                val solidLen = range.solidCount * 7
                for (j in 0 until solidLen) {
                    newSolid[so + j] = currentSplit.solid[range.solidStart * 7 + j]
                }
                so += solidLen

                // Copy water vertices
                val waterLen = range.waterCount * 7
                for (j in 0 until waterLen) {
                    newWater[wo + j] = currentSplit.water[range.waterStart * 7 + j]
                }
                wo += waterLen

                newRanges.add(range.copy(
                    solidStart = solidStart / 7,
                    waterStart = waterStart / 7
                ))
            }
        }

        // If no dirty region was tracked (all changes were water-only or vice versa),
        // mark the entire buffer as dirty
        if (minSolidDirty > maxSolidDirty) {
            minSolidDirty = 0
            maxSolidDirty = newSolidSize * 4
        }
        if (minWaterDirty > maxWaterDirty) {
            minWaterDirty = 0
            maxWaterDirty = newWaterSize * 4
        }

        return IncrementalResult(
            newSolid, newWater, newRanges,
            minSolidDirty, maxSolidDirty - minSolidDirty,
            minWaterDirty, maxWaterDirty - minWaterDirty
        )
    }

    /**
     * Helper to decode chunk coordinates from a key.
     * Used by both split and incrementalUpdate.
     */
    fun decodeChunkCoords(key: Long, chunkSize: Int): Triple<Int, Int, Int> {
        fun decodeChunkCoord(bits: Long, shift: Int): Int {
            val raw = ((bits shr shift) and 0x1FFFFFL).toInt()
            return (raw ushr 1) xor -(raw and 1)
        }
        return Triple(
            decodeChunkCoord(key, 42),
            decodeChunkCoord(key, 21),
            decodeChunkCoord(key, 0)
        )
    }
}
