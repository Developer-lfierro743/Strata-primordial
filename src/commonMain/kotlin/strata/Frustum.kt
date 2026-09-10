package strata

import kotlin.math.sqrt

/**
 * View frustum extracted from a column-major view-projection matrix using
 * the Gribb–Hartmann plane-extraction method. A chunk is culled (not drawn)
 * when its AABB falls entirely outside any one of the six planes — the last
 * optimization from the superformula's Keyconcepts (Part 1).
 *
 * Planes are stored as (A, B, C, D); a point is inside when
 * A·x + B·y + C·z + D >= 0. Normalized so the AABB "positive vertex" test
 * gives correct distances.
 */
class Frustum(vp: FloatArray) {

    private val planes = Array(6) { FloatArray(4) }

    init {
        // Column-major: element [col * 4 + row].
        val r0 = floatArrayOf(vp[0], vp[4], vp[8], vp[12])
        val r1 = floatArrayOf(vp[1], vp[5], vp[9], vp[13])
        val r2 = floatArrayOf(vp[2], vp[6], vp[10], vp[14])
        val r3 = floatArrayOf(vp[3], vp[7], vp[11], vp[15])

        fun combine(a: FloatArray, b: FloatArray, sign: Float) =
            floatArrayOf(a[0] + sign * b[0], a[1] + sign * b[1], a[2] + sign * b[2], a[3] + sign * b[3])

        planes[0] = combine(r3, r0, 1f)   // left
        planes[1] = combine(r3, r0, -1f)  // right
        planes[2] = combine(r3, r1, 1f)   // bottom
        planes[3] = combine(r3, r1, -1f)  // top
        planes[4] = combine(r3, r2, 1f)   // near
        planes[5] = combine(r3, r2, -1f)  // far

        for (pl in planes) {
            val len = sqrt(pl[0] * pl[0] + pl[1] * pl[1] + pl[2] * pl[2]).let { if (it < 1e-8f) 1f else it }
            pl[0] /= len; pl[1] /= len; pl[2] /= len; pl[3] /= len
        }
    }

    /** True when the given AABB is at least partially inside the frustum. */
    fun intersectsAabb(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float
    ): Boolean {
        for (pl in planes) {
            val px = if (pl[0] >= 0f) maxX else minX
            val py = if (pl[1] >= 0f) maxY else minY
            val pz = if (pl[2] >= 0f) maxZ else minZ
            if (pl[0] * px + pl[1] * py + pl[2] * pz + pl[3] < 0f) return false
        }
        return true
    }

    /** AABB of the chunk identified by [key] (cx in the high 32 bits). */
    fun intersectsChunk(key: Long, chunkSize: Int): Boolean {
        val cx = (key shr 32).toInt()
        val cz = key.toInt()
        val ox = cx * chunkSize
        val oz = cz * chunkSize
        return intersectsAabb(
            ox.toFloat(), 0f, oz.toFloat(),
            (ox + chunkSize).toFloat(), chunkSize.toFloat(), (oz + chunkSize).toFloat()
        )
    }
}
