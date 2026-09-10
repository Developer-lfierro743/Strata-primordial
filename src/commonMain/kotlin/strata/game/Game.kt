package strata.game

import strata.ecs.*
import strata.math.*
import strata.world.World

/**
 * Main game class - orchestrates all systems
 */
class Game {
    val world = ecs.World()
    val gameWorld = World()
    private val systems = mutableListOf<System>()

    // Player entity
    val player: Entity = world.create()

    init {
        // Add systems
        systems.add(WorldGenSystem())
        systems.add(MovementSystem())
        systems.add(RenderSystem())

        // Setup player
        world.componentManager.register<Position>()[player] = Position(Vec3(8f, 20f, 8f))
        world.componentManager.register<Velocity>()[player] = Velocity(Vec3(0f, 0f, 0f))
        world.componentManager.register<Rotation>()[player] = Rotation(0f, 0f)
        world.componentManager.register<Camera>()[player] = Camera()
        world.componentManager.register<PlayerControlled>()[player] = PlayerControlled("Player1")
    }

    fun update(deltaTime: Float) {
        for (system in systems) {
            system.update(world, gameWorld, deltaTime)
        }
    }

    fun movePlayer(dx: Float, dy: Float, dz: Float) {
        val vel = world.componentManager.getStore<Velocity>()?.get(player)
        vel?.let {
            it.vel = Vec3(dx, dy, dz)
        }
    }

    fun rotatePlayer(dYaw: Float, dPitch: Float) {
        val rot = world.componentManager.getStore<Rotation>()?.get(player)
        rot?.let {
            it.yaw += dYaw
            it.pitch += dPitch
            // Clamp pitch
            if (it.pitch > 89f) it.pitch = 89f
            if (it.pitch < -89f) it.pitch = -89f
        }
    }

    fun getPlayerPosition(): Vec3? {
        return world.componentManager.getStore<Position>()?.get(player)?.pos
    }

    fun getPlayerRotation(): Pair<Float, Float>? {
        val rot = world.componentManager.getStore<Rotation>()?.get(player) ?: return null
        return Pair(rot.yaw, rot.pitch)
    }
}
