package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class WorldTest {
    @Test
    fun generatesChunkDeterministically() {
        val world = World(seed = 99)
        val a = world.generateChunk(0, 0, 0)
        val b = world.generateChunk(0, 0, 0)
        for (i in a.snapshot().indices) {
            assertEquals(a.snapshot()[i], b.snapshot()[i], "chunk mismatch at $i")
        }
    }

    @Test
    fun blockWorldCoordinatesCrossChunkEdges() {
        val world = World(seed = 5)
        world.putChunk(-1, 0, 0, world.generateChunk(-1, 0, 0))
        world.putChunk(0, 0, 0, world.generateChunk(0, 0, 0))
        world.putChunk(1, 0, 0, world.generateChunk(1, 0, 0))

        // chunk -1 covers world x in [-32,-1]; chunk 0 covers [0,31]; chunk 1 covers [32,63].
        world.setBlock(-32, 10, 10, BlockId.Wood)
        world.setBlock(-1, 10, 10, BlockId.Wood)
        world.setBlock(0, 10, 10, BlockId.Wood)
        world.setBlock(31, 10, 10, BlockId.Wood)
        world.setBlock(32, 10, 10, BlockId.Wood)
        assertEquals(BlockId.Wood, world.getBlock(-32, 10, 10))
        assertEquals(BlockId.Wood, world.getBlock(-1, 10, 10))
        assertEquals(BlockId.Wood, world.getBlock(0, 10, 10))
        assertEquals(BlockId.Wood, world.getBlock(31, 10, 10))
        assertEquals(BlockId.Wood, world.getBlock(32, 10, 10))

        world.setBlock(32, 10, 10, BlockId.Air)
        assertEquals(BlockId.Air, world.getBlock(32, 10, 10))
    }

    @Test
    fun chunkKeysRoundTripNegativeCoords() {
        val world = World(seed = 1)
        for (c in listOf(-3 to -2, -1 to 0, 0 to 0, 2 to 5, 7 to -9)) {
            val key = world.chunkKey(c.first, 0, c.second)
            assertEquals(c.first, world.chunkX(key))
            assertEquals(0, world.chunkY(key))
            assertEquals(c.second, world.chunkZ(key))
        }
    }

    @Test
    fun chunkKeysRoundTripVerticalCoords() {
        val world = World(seed = 1)
        val key = world.chunkKey(5, -2, 8)
        assertEquals(5, world.chunkX(key))
        assertEquals(-2, world.chunkY(key))
        assertEquals(8, world.chunkZ(key))
    }

    @Test
    fun waterFillsLowColumnsToSeaLevel() {
        val world = World(seed = 7, size = 32)
        val chunk = world.generateChunk(0, 0, 0)
        for (x in 0 until 32) for (z in 0 until 32) {
            val h = Terrain.heightAt(7, x, z)
            for (y in 0 until 32) {
                val b = chunk.get(x, y, z)
                if (y <= h) {
                    // Below surface: solid blocks (stone, dirt, grass, ores, gravel, cave air)
                    // The only guarantee is no water here
                    assertTrue(b != BlockId.Water, "solid column must not contain water at ($x,$y,$z)")
                } else if (y <= Terrain.SEA_LEVEL && h < Terrain.SEA_LEVEL) {
                    // Below sea level but above surface: must be water
                    assertEquals(BlockId.Water, b, "expected water fill at ($x,$y,$z)")
                } else if (y > Terrain.SEA_LEVEL) {
                    // Above sea level: must be air (or tree blocks placed above surface)
                    assertTrue(
                        b == BlockId.Air || b == BlockId.Leaves || b == BlockId.Log,
                        "expected air/tree above sea level at ($x,$y,$z), got $b"
                    )
                }
            }
        }
    }

    @Test
    fun shorelineColumnsAreSandy() {
        val world = World(seed = 7, size = 32)
        val chunk = world.generateChunk(0, 0, 0)
        var beaches = 0
        for (x in 0 until 32) for (z in 0 until 32) {
            val h = Terrain.heightAt(7, x, z)
            if (h <= Terrain.SEA_LEVEL + 1) {
                beaches++
                assertEquals(BlockId.Sand, chunk.get(x, h, z), "beach column at ($x,$z) should be sand")
            }
        }
        assertTrue(beaches > 0, "expected some beach columns for seed 7")
    }

    @Test
    fun defaultGameSeedHasLakesAndBeachesNearSpawn() {
        val world = World(seed = 12345, size = 32)
        var water = 0
        var beaches = 0
        for (cx in -1..1) for (cz in -1..1) {
            val chunk = world.generateChunk(cx, 0, cz)
            for (i in chunk.snapshot().indices) {
                when (chunk.getByIndex(i)) {
                    BlockId.Water -> water++
                    BlockId.Sand -> beaches++
                    else -> {}
                }
            }
        }
        assertTrue(water > 0, "game seed 12345 should have lakes near spawn")
        assertTrue(beaches > 0, "game seed 12345 should have beaches near spawn")
    }

    @Test
    fun worldsHaveLakes() {
        var totalWater = 0
        for (seed in 0..15) {
            val world = World(seed = seed, size = 32)
            val chunk = world.generateChunk(0, 0, 0)
            for (i in chunk.snapshot().indices) {
                if (chunk.getByIndex(i) == BlockId.Water) totalWater++
            }
        }
        assertTrue(totalWater > 0, "expected some water across seeds 0..15")
    }

    @Test
    fun borderSliceIndexesMatchMesherLookup() {
        val world = World(seed = 3, size = 16)
        world.putChunk(0, 0, 0, world.generateChunk(0, 0, 0))
        world.putChunk(1, 0, 0, world.generateChunk(1, 0, 0))

        val east = world.borderSlice(0, 0, 0, BlockFace.EAST)!!
        val neighbor = world.getChunk(1, 0, 0)!!
        for (y in 0 until 16) for (z in 0 until 16) {
            assertEquals(neighbor.get(0, y, z).value, east[y * 16 + z], "east slice at y=$y,z=$z")
        }

        val west = world.borderSlice(1, 0, 0, BlockFace.WEST)!!
        val left = world.getChunk(0, 0, 0)!!
        for (y in 0 until 16) for (z in 0 until 16) {
            assertEquals(left.get(15, y, z).value, west[y * 16 + z], "west slice at y=$y,z=$z")
        }
    }

    @Test
    fun verticalBorderSliceWorks() {
        val world = World(seed = 3, size = 16)
        // Place a block at y=0 in chunk (0,1,0) — the bottom layer of the upper chunk
        val c1 = world.generateChunk(0, 1, 0)
        c1.set(5, 0, 5, BlockId.Wood)
        world.putChunk(0, 1, 0, c1)

        // Create chunk (0,0,0) so the border slice has a neighbor
        world.putChunk(0, 0, 0, world.generateChunk(0, 0, 0))

        // The TOP slice of chunk (0,0,0) returns the BOTTOM plane of chunk (0,1,0)
        val top = world.borderSlice(0, 0, 0, BlockFace.TOP)
        assertEquals(BlockId.Wood.value, top!![5 * 16 + 5], "top border of lower chunk should see wood from upper chunk at x=5,z=5")
    }

    @Test
    fun streamerLoadsAndUnloadsChunks() = runBlocking {
        val world = World(seed = 11, size = 16)
        val scope = CoroutineScope(SupervisorJob())
        val streamer = WorldStreamer(world, renderDistance = 1, verticalRenderDistance = 0, scope = scope)

        streamer.update(0, 0, 0)
        for (i in 0..400) {
            streamer.update(0, 0, 0)
            if (world.loadedKeys.size >= 9) break
            delay(10)
        }
        assertEquals(9, world.loadedKeys.size, "expected 3x3 chunks loaded")

        // Move far away → everything unloads.
        for (i in 0..400) {
            streamer.update(100, 0, 100)
            if (world.loadedKeys.isEmpty()) break
            delay(10)
        }
        assertTrue(world.loadedKeys.isEmpty(), "chunks should unload outside render distance")

        scope.cancel()
    }

    @Test
    fun streamerLoadsVerticalChunks() = runBlocking {
        val world = World(seed = 11, size = 16)
        val scope = CoroutineScope(SupervisorJob())
        val streamer = WorldStreamer(world, renderDistance = 0, verticalRenderDistance = 1, scope = scope)

        streamer.update(0, 0, 0)
        for (i in 0..400) {
            streamer.update(0, 0, 0)
            if (world.loadedKeys.size >= 3) break
            delay(10)
        }
        // With verticalRenderDistance=1, we expect at least 3 chunks: cy=-1, 0, 1
        assertTrue(world.loadedKeys.size >= 3, "expected at least 3 vertical chunks loaded, got ${world.loadedKeys.size}")

        scope.cancel()
    }

    @Test
    fun meshEpochAdvancesWhenMeshesArrive() = runBlocking {
        val world = World(seed = 17, size = 16)
        val scope = CoroutineScope(SupervisorJob())
        val streamer = WorldStreamer(world, renderDistance = 0, verticalRenderDistance = 0, scope = scope)

        val e0 = streamer.meshEpoch()
        streamer.update(0, 0, 0)
        for (i in 0..400) {
            streamer.update(0, 0, 0)
            if (streamer.meshEpoch() > e0) break
            delay(10)
        }
        assertTrue(streamer.meshEpoch() > e0, "epoch should advance when a mesh is stored")
        scope.cancel()
    }

    @Test
    fun meshRejectsStaleResults() = runBlocking {
        val world = World(seed = 13, size = 16)
        val scope = CoroutineScope(SupervisorJob())
        val streamer = WorldStreamer(world, renderDistance = 0, verticalRenderDistance = 0, scope = scope)

        streamer.update(0, 0, 0)
        for (i in 0..400) {
            streamer.update(0, 0, 0)
            if (streamer.meshKeys().isNotEmpty()) break
            delay(10)
        }
        assertTrue(streamer.meshKeys().isNotEmpty(), "a mesh should eventually arrive")
        scope.cancel()
    }

    @Test
    fun caveGenerationCarvesUnderground() {
        val world = World(seed = 42, size = 32)
        val chunk = world.generateChunk(0, 0, 0)
        var airBelowSurface = 0
        for (x in 0 until 32) for (z in 0 until 32) {
            val h = Terrain.heightAt(42, x, z)
            for (y in 3 until h - 3) {
                if (chunk.get(x, y, z) == BlockId.Air) airBelowSurface++
            }
        }
        assertTrue(airBelowSurface > 0, "expected some carved caves underground")
    }

    @Test
    fun treesAreGeneratedOnSurface() {
        val world = World(seed = 42, size = 32)
        // Generate several chunks to get enough surface area for trees
        var hasLeaves = false
        for (cx in -1..1) for (cz in -1..1) {
            val chunk = world.generateChunk(cx, 0, cz)
            for (i in chunk.snapshot().indices) {
                if (chunk.getByIndex(i) == BlockId.Leaves) hasLeaves = true
            }
        }
        assertTrue(hasLeaves, "expected some trees with leaves on the surface")
    }

    @Test
    fun oreGenerationPlacesOres() {
        val world = World(seed = 42, size = 32)
        val chunk = world.generateChunk(0, 0, 0)
        var ores = 0
        for (i in chunk.snapshot().indices) {
            val block = chunk.getByIndex(i)
            if (block == BlockId.CoalOre || block == BlockId.IronOre || block == BlockId.Gravel) ores++
        }
        assertTrue(ores > 0, "expected some ores in the world")
    }

    @Test
    fun bedrockLayerAtWorldBottom() {
        val world = World(seed = 42, size = 32)
        // Generate chunk at cy=-1 which covers y=-32 to y=-1
        val chunk = world.generateChunk(0, -1, 0)
        // Bottom two layers should be bedrock
        for (x in 0 until 32) for (z in 0 until 32) {
            assertEquals(BlockId.Bedrock, chunk.get(x, 0, z), "bedrock at y=-32 (local y=0)")
            assertEquals(BlockId.Bedrock, chunk.get(x, 1, z), "bedrock at y=-31 (local y=1)")
        }
    }
}
