package strata.math

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun length(): Float = kotlin.math.sqrt(dot(this))
    fun normalize(): Vec3 {
        val len = length()
        return if (len > 0.0001f) this * (1.0f / len) else this
    }
}

data class Vec3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(o: Vec3i) = Vec3i(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3i) = Vec3i(x - o.x, y - o.y, z - o.z)
}

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
}

data class Mat4(val m: FloatArray) {
    companion object {
        fun identity(): Mat4 {
            val m = FloatArray(16) { 0f }
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
            return Mat4(m)
        }

        fun perspective(fov: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / kotlin.math.tan(fov * 0.5f)
            val rangeInv = 1f / (near - far)
            val m = FloatArray(16) { 0f }
            m[0] = f / aspect
            m[5] = f
            m[10] = (near + far) * rangeInv
            m[11] = -1f
            m[14] = 2f * near * far * rangeInv
            return Mat4(m)
        }

        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalize()
            val s = Vec3(
                f.y * up.z - f.z * up.y,
                f.z * up.x - f.x * up.z,
                f.x * up.y - f.y * up.x
            ).normalize()
            val u = Vec3(
                s.y * f.z - s.z * f.y,
                s.z * f.x - s.x * f.z,
                s.x * f.y - s.y * f.x
            )

            val m = FloatArray(16) { 0f }
            m[0] = s.x; m[4] = s.y; m[8] = s.z
            m[1] = u.x; m[5] = u.y; m[9] = u.z
            m[2] = -f.x; m[6] = -f.y; m[10] = -f.z
            m[12] = -(s.x * eye.x + s.y * eye.y + s.z * eye.z)
            m[13] = -(u.x * eye.x + u.y * eye.y + u.z * eye.z)
            m[14] = (f.x * eye.x + f.y * eye.y + f.z * eye.z)
            m[15] = 1f
            return Mat4(m)
        }
    }
}

data class ChunkPos(val x: Int, val z: Int)

data class AABB(val min: Vec3, val max: Vec3) {
    fun contains(point: Vec3): Boolean =
        point.x >= min.x && point.x <= max.x &&
        point.y >= min.y && point.y <= max.y &&
        point.z >= min.z && point.z <= max.z

    fun intersects(o: AABB): Boolean =
        min.x <= o.max.x && max.x >= o.min.x &&
        min.y <= o.max.y && max.y >= o.min.y &&
        min.z <= o.max.z && max.z >= o.min.z
}
