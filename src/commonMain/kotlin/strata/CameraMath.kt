package strata

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Camera state (pure Kotlin). */
data class Camera(
    val x: Float, val y: Float, val z: Float,
    val yaw: Float, val pitch: Float
)

/* ── 4×4 Matrix in column-major order ── */
data class Mat4(val m: FloatArray = FloatArray(16)) {
    companion object {
        fun identity(): Mat4 = Mat4().apply {
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        }
    }
}

operator fun Mat4.times(other: Mat4): Mat4 {
    val r = Mat4()
    for (c in 0..3) for (row in 0..3) {
        var sum = 0f
        for (k in 0..3) sum += m[k * 4 + row] * other.m[c * 4 + k]
        r.m[c * 4 + row] = sum
    }
    return r
}

fun perspectiveMatrix(fovYRadians: Float, aspect: Float, near: Float, far: Float): Mat4 {
    val r = Mat4()
    val tanHalf = tan(fovYRadians * 0.5f)
    r.m[0] = 1f / (aspect * tanHalf)
    r.m[5] = -1f / tanHalf          // Vulkan Y-flip
    r.m[10] = far / (near - far)
    r.m[11] = -1f
    r.m[14] = (near * far) / (near - far)
    return r
}

fun lookAtMatrix(eyeX: Float, eyeY: Float, eyeZ: Float,
                 cx: Float, cy: Float, cz: Float,
                 upX: Float, upY: Float, upZ: Float): Mat4 {
    var fx = cx - eyeX; var fy = cy - eyeY; var fz = cz - eyeZ
    var flen = (fx*fx + fy*fy + fz*fz).let { if (it < 1e-8f) 1e-8f else sqrt(it) }
    fx /= flen; fy /= flen; fz /= flen

    var rx = fy*upZ - fz*upY; var ry = fz*upX - fx*upZ; var rz = fx*upY - fy*upX
    var rlen = (rx*rx + ry*ry + rz*rz).let { if (it < 1e-8f) 1e-8f else sqrt(it) }
    rx /= rlen; ry /= rlen; rz /= rlen

    val ux = ry*fz - rz*fy; val uy = rz*fx - rx*fz; val uz = rx*fy - ry*fx

    val r = Mat4.identity()
    r.m[0] = rx;  r.m[4] = ry;  r.m[8]  = rz
    r.m[1] = ux;  r.m[5] = uy;  r.m[9]  = uz
    r.m[2] = -fx; r.m[6] = -fy; r.m[10] = -fz
    r.m[12] = -(rx*eyeX + ry*eyeY + rz*eyeZ)
    r.m[13] = -(ux*eyeX + uy*eyeY + uz*eyeZ)
    r.m[14] =  (fx*eyeX + fy*eyeY + fz*eyeZ)
    return r
}

fun computeMVP(cam: Camera, aspect: Float): FloatArray {
    val cy = cos(cam.yaw); val sy = sin(cam.yaw)
    val cp = cos(cam.pitch); val sp = sin(cam.pitch)
    val fx = sy * cp; val fy = -sp; val fz = cy * cp

    val view = lookAtMatrix(cam.x, cam.y, cam.z,
        cam.x + fx, cam.y + fy, cam.z + fz,
        0f, 1f, 0f)  // Y-up world; Vulkan Y-flip is in the projection matrix
    val proj = perspectiveMatrix(1.0472f, aspect, 0.5f, 200f)
    return (proj * view).m
}
