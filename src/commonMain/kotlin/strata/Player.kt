package strata

import kotlin.math.floor
import kotlin.math.min

// ─── Math primitives ──────────────────────────────────────────────────

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
}

data class BlockPos(val x: Int, val y: Int, val z: Int)

data class BlockHit(val block: BlockPos, val adjacent: BlockPos)

// ─── Player ───────────────────────────────────────────────────────────

class Player(val world: World) {
    /** Feet position (AABB center on X/Z, bottom on Y). */
    var position = Vec3(16f, 12f, 16f)
    var velocity = Vec3(0f, 0f, 0f)
    var forward = Vec3(0f, -0.15f, 1f)

    /** Whether the feet are resting on a solid block (grounded). */
    var onGround = false
    /** Whether the head is submerged in water. */
    var inWater = false

    /* ── Jump-feel state ── */
    private var jumpBuffer = 0f      // seconds remaining on a buffered jump press
    private var coyoteTimer = 0f     // seconds remaining to jump after leaving ground
    private var jumpHeldPrev = false // whether Space was held last tick (variable height)

    companion object {
        const val HALF_WIDTH = 0.3f     // 0.6-wide box
        const val HEIGHT = 1.8f         // standing height
        const val EYE_HEIGHT = 1.62f    // eye offset above feet
        const val WALK_SPEED = 6f
        const val SNEAK_SPEED = 2.5f
        const val JUMP_SPEED = 8.5f
        const val GRAVITY = 24f
        /* ── Jump feel (Minecraft/Celeste-style) ──
         *  - JUMP_BUFFER_TIME: a press up to this long before landing still fires
         *    (so the jump doesn't feel like it "ate" your input).
         *  - COYOTE_TIME: you can still jump this long after walking off a ledge.
         *  - JUMP_CUT_SPEED: releasing Space mid-ascent caps the upward speed,
         *    so a quick tap is a short hop and holding Space is a full jump. */
        const val JUMP_BUFFER_TIME = 0.15f
        const val COYOTE_TIME = 0.12f
        const val JUMP_CUT_SPEED = 4.5f
        const val MAX_FALL = 48f
        const val SWIM_SPEED = 4f
        const val SWIM_UP = 3.5f        // buoyancy while holding jump
        const val WATER_SINK = 1.5f     // gentle sink in water
        const val FLY_SPEED = 10f       // creative/spectator flight speed
        const val FLY_UP = 10f          // vertical flight speed while holding jump/sneak
    }

    /** Eye position used for the camera and for block targeting. */
    fun eye(): Vec3 = Vec3(position.x, position.y + EYE_HEIGHT, position.z)

    /** True when the given world cell blocks the player. */
    private fun solid(x: Int, y: Int, z: Int): Boolean =
        world.getBlock(x, y, z).let { it != BlockId.Air && it != BlockId.Water }

    private fun isWater(x: Int, y: Int, z: Int): Boolean =
        world.getBlock(x, y, z) == BlockId.Water

    /**
     * AABB-overlap test at an arbitrary feet position. Samples every voxel the
     * box touches; water and air never collide (so you can walk into lakes).
     */
    private fun collides(px: Float, py: Float, pz: Float): Boolean {
        val x0 = floor(px - HALF_WIDTH).toInt()
        val x1 = floor(px + HALF_WIDTH).toInt()
        val y0 = floor(py).toInt()
        val y1 = floor(py + HEIGHT).toInt()
        val z0 = floor(pz - HALF_WIDTH).toInt()
        val z1 = floor(pz + HALF_WIDTH).toInt()
        for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) {
            if (solid(x, y, z)) return true
        }
        return false
    }

    private fun headInWater(): Boolean {
        val hx = floor(position.x).toInt()
        val hy = floor(position.y + EYE_HEIGHT).toInt()
        val hz = floor(position.z).toInt()
        return isWater(hx, hy, hz)
    }

    /**
     * Advance the player one physics step at fixed [dt]. [moveX]/[moveZ] are
     * already-normalized world-space directions from WASD + yaw. The [mode]
     * decides the rules: survival walks with gravity + voxel collision,
     * creative flies (still blocked by walls), spectator flies right through
     * everything (no-clip).
     */
    fun tick(
        dt: Float,
        moveX: Float,
        moveZ: Float,
        jump: Boolean,
        sneak: Boolean,
        mode: GameMode = GameMode.SURVIVAL
    ) {
        when (mode) {
            GameMode.SURVIVAL -> tickWalk(dt, moveX, moveZ, jump, sneak)
            GameMode.CREATIVE -> tickFly(dt, moveX, moveZ, jump, sneak, collide = true)
            GameMode.SPECTATOR -> tickFly(dt, moveX, moveZ, jump, sneak, collide = false)
        }
    }

    /** Survival: gravity, jumping, swimming, full voxel collision. */
    private fun tickWalk(dt: Float, moveX: Float, moveZ: Float, jump: Boolean, sneak: Boolean) {
        inWater = headInWater()

        /* ── Jump-feel input: buffer presses, grant coyote grace, and detect
         * the Space release so a tap gives a short hop while holding gives a
         * full jump (see companion constants). ── */
        jumpBuffer = if (jump) JUMP_BUFFER_TIME else (jumpBuffer - dt).coerceAtLeast(0f)
        coyoteTimer = if (onGround) COYOTE_TIME else (coyoteTimer - dt).coerceAtLeast(0f)

        /* Horizontal: walk speed, sneak slower, swim slower still. */
        val speed = when {
            inWater -> SWIM_SPEED
            sneak -> SNEAK_SPEED
            else -> WALK_SPEED
        }
        var vx = moveX * speed
        var vz = moveZ * speed

        /* Vertical: gravity (soft in water), jump off the ground, swim up. */
        var vy = velocity.y
        if (inWater) {
            vy -= GRAVITY * 0.15f * dt
            if (vy < -WATER_SINK) vy = -WATER_SINK
            if (jump) vy = SWIM_UP
        } else {
            vy -= GRAVITY * dt
            if (vy < -MAX_FALL) vy = -MAX_FALL
            // Jump fires from a buffered press while grounded (or within the
            // coyote window after leaving a ledge) — no more lost inputs.
            if (jumpBuffer > 0f && (onGround || coyoteTimer > 0f)) {
                vy = JUMP_SPEED
                jumpBuffer = 0f
                coyoteTimer = 0f
            }
            // Variable jump height: releasing Space while still rising caps
            // the ascent so a tap is a short hop, holding is a full jump.
            if (jumpHeldPrev && !jump && vy > JUMP_CUT_SPEED) vy = JUMP_CUT_SPEED
        }
        jumpHeldPrev = jump

        onGround = false

        var px = position.x
        var py = position.y
        var pz = position.z

        // X axis
        val nx = px + vx * dt
        if (!collides(nx, py, pz)) px = nx else vx = 0f
        // Z axis
        val nz = pz + vz * dt
        if (!collides(px, py, nz)) pz = nz else vz = 0f
        // Y axis (last, so stepping up one block feels right)
        val ny = py + vy * dt
        if (!collides(px, ny, pz)) {
            py = ny
        } else {
            if (vy < 0f) onGround = true
            vy = 0f
        }

        position = Vec3(px, py, pz)
        velocity = Vec3(vx, vy, vz)
    }

    /**
     * Creative + spectator flight: no gravity — jump climbs, sneak dives,
     * neither keeps altitude. Vertical velocity eases toward the target for a
     * smooth feel. With [collide] = false (spectator) the player passes
     * through every block (no-clip).
     */
    private fun tickFly(dt: Float, moveX: Float, moveZ: Float, jump: Boolean, sneak: Boolean, collide: Boolean) {
        inWater = false

        val vx = moveX * FLY_SPEED
        val vz = moveZ * FLY_SPEED
        val targetVy = when {
            jump -> FLY_UP
            sneak -> -FLY_UP
            else -> 0f
        }
        val vy = velocity.y + (targetVy - velocity.y) * min(1f, dt * 8f)

        onGround = false
        var px = position.x
        var py = position.y
        var pz = position.z

        if (collide) {
            val nx = px + vx * dt
            if (!collides(nx, py, pz)) px = nx
            val nz = pz + vz * dt
            if (!collides(px, py, nz)) pz = nz
            val ny = py + vy * dt
            if (!collides(px, ny, pz)) py = ny
        } else {
            px += vx * dt
            py += vy * dt
            pz += vz * dt
        }

        position = Vec3(px, py, pz)
        velocity = Vec3(vx, vy, vz)
    }

    fun removeTarget(): BlockHit? = world.raycast(eye(), forward)?.also { hit ->
        world.setBlock(hit.block.x, hit.block.y, hit.block.z, BlockId.Air)
    }

    /** Place [block] at the block just past the raycast hit (like a hotbar). */
    fun placeTarget(block: BlockId = BlockId.Dirt): BlockHit? = world.raycast(eye(), forward)?.also { hit ->
        world.setBlock(hit.adjacent.x, hit.adjacent.y, hit.adjacent.z, block)
    }
}
