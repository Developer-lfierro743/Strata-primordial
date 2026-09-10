package strata.game

import strata.ecs.*
import strata.math.*
import strata.world.World

/**
 * System interface - each system processes entities with specific components
 */
interface System {
    fun update(world: ecs.World, gameWorld: World, deltaTime: Float)
}

/**
 * Movement system - applies velocity to position
 */
class MovementSystem : System {
    override fun update(world: ecs.World, gameWorld: World, deltaTime: Float) {
        val store = world.componentManager.getStore<Position>() ?: return
        val velStore = world.componentManager.getStore<Velocity>() ?: return

        for (entity in store.entities()) {
            val pos = store[entity] ?: continue
            val vel = velStore[entity] ?: continue
            pos.pos = pos.pos + vel.vel * deltaTime
        }
    }
}

/**
 * World generation system - generates chunks around players
 */
class WorldGenSystem : System {
    private val generatedChunks = mutableSetOf<ChunkPos>()

    override fun update(world: ecs.World, gameWorld: World, deltaTime: Float) {
        val store = world.componentManager.getStore<Position>() ?: return

        for (entity in store.entities()) {
            val pos = store[entity] ?: continue
            val cx = (pos.pos.x / 16f).toInt()
            val cz = (pos.pos.z / 16f).toInt()

            // Generate chunks in a 5x5 area around player
            for (dx in -2..2) {
                for (dz in -2..2) {
                    val chunkPos = ChunkPos(cx + dx, cz + dz)
                    if (chunkPos !in generatedChunks) {
                        generateChunk(gameWorld, chunkPos)
                        generatedChunks.add(chunkPos)
                    }
                }
            }
        }
    }

    private fun generateChunk(world: World, pos: ChunkPos) {
        val baseX = pos.x * 16
        val baseZ = pos.z * 16

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = baseX + lx
                val wz = baseZ + lz

                // Simple terrain: bedrock at 0, stone 1-12, dirt 13-14, grass at 15
                world.setBlock(wx, 0, wz, 8) // BEDROCK
                for (y in 1..12) {
                    world.setBlock(wx, y, wz, 1) // STONE
                }
                world.setBlock(wx, 13, wz, 2) // DIRT
                world.setBlock(wx, 14, wz, 2) // DIRT
                world.setBlock(wx, 15, wz, 3) // GRASS
            }
        }
    }
}

/**
 * Render system - collects renderable entities
 */
class RenderSystem : System {
    val visibleEntities = mutableListOf<Entity>()

    override fun update(world: ecs.World, gameWorld: World, deltaTime: Float) {
        visibleEntities.clear()
        val store = world.componentManager.getStore<Renderable>() ?: return
        visibleEntities.addAll(store.entities())
    }
}
