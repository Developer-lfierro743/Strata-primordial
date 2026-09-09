package strata

import kotlin.test.Test
import kotlin.test.assertEquals

class VoxelWorldTest {
    @Test
    fun flatWorldGeneratesAndMutates() {
        val chunk = Chunk()
        chunk.generateFlat()
        assertEquals(BlockId.Grass, chunk.get(0, 7, 0))
        chunk.set(0, 7, 0, BlockId.Air)
        assertEquals(BlockId.Air, chunk.get(0, 7, 0))
    }

    @Test
    fun placeTargetPlacesRequestedBlock() {
        val world = World(seed = 42, size = 32)
        world.putChunk(0, 0, 0, Chunk(32).apply { generateFlat() })
        val player = Player(world)
        player.position = Vec3(16f, 14f, 16f)   // above the flat surface (top at y=8)
        player.forward = Vec3(0f, -1f, 0f)      // look straight down
        val hit = player.placeTarget(BlockId.Stone)
        assertEquals(BlockId.Stone, world.getBlock(hit!!.adjacent.x, hit.adjacent.y, hit.adjacent.z))
    }
}

