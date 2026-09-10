package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldMeshSplitTest {

    /** Build a synthetic vertex (x,y,z,r,g,b,material). */
    private fun vert(x: Float, y: Float, z: Float, material: Float): FloatArray =
        floatArrayOf(x, y, z, 1f, 1f, 1f, material)

    private fun mesh(vararg verts: FloatArray): ChunkMesh {
        val data = FloatArray(verts.size * 7)
        var o = 0
        for (v in verts) for (f in v) data[o++] = f
        return ChunkMesh(data, verts.size)
    }

    private fun key(cx: Int, cy: Int, cz: Int): Long {
        fun zigzag(x: Int): Long = ((x.toLong() shl 1) xor (x.toLong() shr 31)) and 0x1FFFFFL
        return (zigzag(cx) shl 42) or (zigzag(cy) shl 21) or zigzag(cz)
    }

    @Test
    fun waterVerticesLandInWaterBufferOnlyWithWorldOffset() {
        // Chunk (1,1): one solid + one water vertex in local coords.
        val entries = listOf(
            key(1, 0, 1) to mesh(
                vert(0f, 1f, 0f, ChunkMesher.MATERIAL_SOLID),
                vert(1f, 1f, 1f, ChunkMesher.MATERIAL_WATER)
            )
        )
        val s = WorldMeshSplit.split(entries, 32)
        assertEquals(7, s.solid.size)
        assertEquals(7, s.water.size)
        // World-space translation: chunk origin (32, 0, 32).
        assertEquals(32f, s.solid[0])
        assertEquals(1f, s.solid[1])
        assertEquals(32f, s.solid[2])
        assertEquals(ChunkMesher.MATERIAL_SOLID, s.solid[6])
        assertEquals(33f, s.water[0])
        assertEquals(33f, s.water[2])
        assertEquals(ChunkMesher.MATERIAL_WATER, s.water[6])
        // Range for the only chunk.
        assertEquals(1, s.ranges.size)
        val r = s.ranges[0]
        assertEquals(key(1, 0, 1), r.key)
        assertEquals(0, r.solidStart)
        assertEquals(1, r.solidCount)
        assertEquals(0, r.waterStart)
        assertEquals(1, r.waterCount)
    }

    @Test
    fun rangesAccumulateAcrossChunks() {
        val entries = listOf(
            key(0, 0, 0) to mesh(vert(0f, 0f, 0f, ChunkMesher.MATERIAL_SOLID)),
            key(0, 0, 1) to mesh(
                vert(0f, 0f, 0f, ChunkMesher.MATERIAL_SOLID),
                vert(0f, 1f, 0f, ChunkMesher.MATERIAL_WATER)
            )
        )
        val s = WorldMeshSplit.split(entries, 32)
        assertEquals(2, s.ranges.size)
        // First chunk: 1 solid, no water.
        assertEquals(0, s.ranges[0].solidStart)
        assertEquals(1, s.ranges[0].solidCount)
        assertEquals(0, s.ranges[0].waterStart)
        assertEquals(0, s.ranges[0].waterCount)
        // Second chunk: solid appends after the first chunk's solid.
        assertEquals(1, s.ranges[1].solidStart)
        assertEquals(1, s.ranges[1].solidCount)
        assertEquals(0, s.ranges[1].waterStart)
        assertEquals(1, s.ranges[1].waterCount)
        // Totals.
        assertEquals(14, s.solid.size)
        assertEquals(7, s.water.size)
    }

    @Test
    fun rangesSumToBufferSizes() {
        // Invariant: the sum of every range's counts must equal the buffers.
        val entries = listOf(
            key(0, 0, 0) to mesh(
                vert(0f, 0f, 0f, ChunkMesher.MATERIAL_SOLID),
                vert(1f, 0f, 0f, ChunkMesher.MATERIAL_WATER)
            ),
            key(2, 0, -1) to mesh(
                vert(0f, 0f, 0f, ChunkMesher.MATERIAL_WATER),
                vert(0f, 1f, 0f, ChunkMesher.MATERIAL_SOLID)
            )
        )
        val s = WorldMeshSplit.split(entries, 32)
        val sumSolid = s.ranges.sumOf { it.solidCount }
        val sumWater = s.ranges.sumOf { it.waterCount }
        assertEquals(s.solid.size / 7, sumSolid, "solid counts must sum to the solid buffer")
        assertEquals(s.water.size / 7, sumWater, "water counts must sum to the water buffer")
        // AABB origins come from the chunk keys.
        assertEquals(64f, s.ranges[1].minX)
        assertEquals(-32f, s.ranges[1].minZ)
    }

    @Test
    fun emptyEntriesSplitToEmpty() {
        val s = WorldMeshSplit.split(emptyList(), 32)
        assertEquals(0, s.solid.size)
        assertEquals(0, s.water.size)
        assertTrue(s.ranges.isEmpty())
    }

    @Test
    fun allOpaqueMeansNoWaterBuffer() {
        val entries = listOf(
            key(0, 0, 0) to mesh(
                vert(0f, 0f, 0f, ChunkMesher.MATERIAL_SOLID),
                vert(1f, 0f, 0f, ChunkMesher.MATERIAL_SOLID)
            )
        )
        val s = WorldMeshSplit.split(entries, 32)
        assertTrue(s.water.isEmpty())
        assertEquals(14, s.solid.size)
        assertEquals(2, s.ranges[0].solidCount)
        assertEquals(0, s.ranges[0].waterCount)
    }
}
