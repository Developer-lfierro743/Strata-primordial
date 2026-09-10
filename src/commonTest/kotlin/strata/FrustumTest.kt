package strata

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrustumTest {

    private fun key(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    @Test
    fun chunkInFrontIsVisible() {
        val mvp = computeMVP(Camera(0f, 0f, 0f, yaw = 0f, pitch = 0f), 1f) // looking +Z
        val f = Frustum(mvp)
        // A box straight ahead, well within FOV and range.
        assertTrue(f.intersectsAabb(0f, 0f, 10f, 10f, 10f, 20f))
        assertTrue(f.intersectsAabb(5f, 0f, 80f, 15f, 10f, 90f))
        // The chunk containing the camera must be visible too.
        assertTrue(f.intersectsAabb(-10f, 0f, -10f, 10f, 32f, 10f))
    }

    @Test
    fun chunkBehindCameraIsCulled() {
        val mvp = computeMVP(Camera(0f, 0f, 0f, yaw = 0f, pitch = 0f), 1f)
        val f = Frustum(mvp)
        assertFalse(f.intersectsAabb(-10f, 0f, -20f, 10f, 10f, -10f))
    }

    @Test
    fun chunkFarOutsideSideIsCulled() {
        val mvp = computeMVP(Camera(0f, 0f, 0f, yaw = 0f, pitch = 0f), 1f)
        val f = Frustum(mvp)
        // FOV 60deg, aspect 1: half-width at distance 80 is ~46. x=-100 is out.
        assertFalse(f.intersectsAabb(-100f, 0f, 80f, -80f, 10f, 90f))
        assertFalse(f.intersectsAabb(-200f, 0f, -20f, -190f, 10f, -10f))
    }

    @Test
    fun cameraAwayFromOriginStillCullsCorrectly() {
        // Camera translated to (100, 16, 100), looking +Z. The positive-vertex
        // test must still work with a non-origin camera.
        val mvp = computeMVP(Camera(100f, 16f, 100f, yaw = 0f, pitch = 0f), 1f)
        val f = Frustum(mvp)
        // Chunk (3,3) spans x[96,128] z[96,128] y[0,32] — contains the camera.
        assertTrue(f.intersectsChunk(key(3, 3), 32))
        // Chunk (3,4) spans z[128,160] — straight ahead, within FOV and range.
        assertTrue(f.intersectsChunk(key(3, 4), 32))
        // Chunk (3,-1) spans z[-32,0] — behind the camera.
        assertFalse(f.intersectsChunk(key(3, -1), 32))
        // Chunk (0,0) spans z[0,32] — also behind the camera (camera at z=100).
        assertFalse(f.intersectsChunk(key(0, 0), 32))
    }

    @Test
    fun rotationChangesVisibility() {
        // Looking +X: the chunk at cx=1 is in front, cx=-1 is behind.
        val mvpX = computeMVP(Camera(0f, 0f, 0f, yaw = (PI / 2).toFloat(), pitch = 0f), 1f)
        val fx = Frustum(mvpX)
        assertTrue(fx.intersectsChunk(key(1, 0), 32))
        assertFalse(fx.intersectsChunk(key(-1, 0), 32))

        // Looking +Z: cx=0/cz=1 in front, cx=0/cz=-1 behind.
        val mvpZ = computeMVP(Camera(0f, 0f, 0f, yaw = 0f, pitch = 0f), 1f)
        val fz = Frustum(mvpZ)
        assertTrue(fz.intersectsChunk(key(0, 1), 32))
        assertFalse(fz.intersectsChunk(key(0, -1), 32))
    }
}
