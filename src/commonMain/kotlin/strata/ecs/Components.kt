package strata.ecs

import strata.math.*

data class Position(var pos: Vec3) : Component
data class Velocity(var vel: Vec3) : Component
data class Rotation(var yaw: Float, var pitch: Float) : Component
data class Renderable(val meshId: Int) : Component
data class PlayerControlled(val name: String) : Component
data class Camera(
    var fov: Float = 70f,
    var near: Float = 0.1f,
    var far: Float = 1000f
) : Component

interface Component
