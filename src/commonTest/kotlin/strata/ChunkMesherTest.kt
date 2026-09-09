package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkMesherTest {
    @Test
    fun testSingleBlockFaceCulling() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Stone)
        val mesh = ChunkMesher.generateMesh(chunk)
        assertEquals(36, mesh.vertexCount) // 6 faces * 6 vertices
    }

    @Test
    fun waterMeshesAsSurfacePlaneOnly() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Water)
        val mesh = ChunkMesher.generateMesh(chunk)
        // Only the (lowered) top face is emitted: 1 quad = 6 vertices.
        assertEquals(6, mesh.vertexCount)
    }

    @Test
    fun stackedWaterEmitsSingleSurface() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Water)
        chunk.set(0, 1, 0, BlockId.Water)
        val mesh = ChunkMesher.generateMesh(chunk)
        // Only the top-most water block emits its face → 6 vertices.
        assertEquals(6, mesh.vertexCount)
    }

    @Test
    fun waterDoesNotOccludeSolidBlocks() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Sand)   // lake bed
        chunk.set(0, 1, 0, BlockId.Water)  // water above it
        val mesh = ChunkMesher.generateMesh(chunk)
        // Sand keeps all 6 faces (water is not occluding) = 36 verts,
        // plus the water surface plane = 6 verts → 42.
        assertEquals(42, mesh.vertexCount)
    }

    @Test
    fun waterVerticesCarryMaterialFlag() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Water)
        val mesh = ChunkMesher.generateMesh(chunk)
        // First vertex's 7th float must be the water material flag.
        assertEquals(ChunkMesher.MATERIAL_WATER, mesh.floatData[6])
    }

    @Test
    fun solidVerticesCarrySolidMaterial() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Stone)
        val mesh = ChunkMesher.generateMesh(chunk)
        assertEquals(ChunkMesher.MATERIAL_SOLID, mesh.floatData[6])
    }

    @Test
    fun testTwoAdjacentBlocksFaceCulling() {
        val chunk = Chunk(32)
        chunk.set(0, 0, 0, BlockId.Stone)
        chunk.set(1, 0, 0, BlockId.Stone)
        val mesh = ChunkMesher.generateMesh(chunk)
        // 2 blocks = 12 faces minus 2 shared faces = 10 visible faces = 60 vertices
        assertEquals(60, mesh.vertexCount)
    }
}
