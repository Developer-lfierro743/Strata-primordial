package strata

import kotlin.math.abs
import kotlin.math.floor

/**
 * Deterministic, seedable terrain generator.
 *
 * Uses FastNoiseLite (OpenSimplex2) for all noise generation.
 * Creates local FastNoiseLite instances per call for thread safety
 * (worker threads generate chunks concurrently via WorldStreamer).
 */
object Terrain {

    const val SEA_LEVEL = 10
    const val WORLD_BOTTOM = -32
    const val WORLD_TOP = 64

    // ─── Helper to create configured FastNoiseLite instances ────────────

    private fun make2D(freq: Float, octaves: Int = 1, fractal: FastNoiseLite.FractalType = FastNoiseLite.FractalType.None): FastNoiseLite =
        FastNoiseLite().apply {
            noiseType = FastNoiseLite.NoiseType.OpenSimplex2
            frequency = freq
            fractalOctaves = octaves
            fractalType = fractal
            fractalGain = 0.5f
            fractalLacunarity = 2.0f
        }

    private fun make3D(freq: Float): FastNoiseLite =
        FastNoiseLite().apply {
            noiseType = FastNoiseLite.NoiseType.OpenSimplex2
            frequency = freq
        }

    // ─── Heightmap (2D) ───────────────────────────────────────────────

    fun heightAt(seed: Int, x: Int, z: Int): Int {
        val xf = x.toFloat()
        val zf = z.toFloat()

        val continental = make2D(0.004f).apply { this.seed = seed + 100 }.noise2D(xf, zf)
        val shape = make2D(0.018f, octaves = 4, fractal = FastNoiseLite.FractalType.FBm).apply { this.seed = seed }.noise2D(xf, zf)
        val detail = make2D(0.055f).apply { this.seed = seed + 1 }.noise2D(xf, zf)
        val ridgeShaped = make2D(0.009f, octaves = 3, fractal = FastNoiseLite.FractalType.Ridged).apply { this.seed = seed + 2 }.noise2D(xf, zf)

        val h = 14f + shape * 8f + detail * 2.5f + ridgeShaped * 12f * (0.5f + continental * 0.5f)
        return h.toInt().coerceIn(WORLD_BOTTOM + 4, 45)
    }

    // ─── Cave carving (3D) ────────────────────────────────────────────

    fun hasCave(seed: Int, x: Int, y: Int, z: Int): Boolean {
        if (y <= WORLD_BOTTOM + 2) return false
        val surfaceH = heightAt(seed, x, z)
        if (y >= surfaceH - 2) return false
        if (y <= 2 && y <= SEA_LEVEL) return false

        val xf = x.toFloat(); val yf = y.toFloat(); val zf = z.toFloat()

        val cave1 = make3D(0.04f).apply { this.seed = seed + 50 }.noise3D(xf, yf, zf)
        val cave2 = make3D(0.07f).apply { this.seed = seed + 51 }.noise3D(xf, yf, zf)
        val depthFactor = ((surfaceH - y).toFloat() / 30f).coerceIn(0f, 1f)
        val cave3 = make3D(0.025f).apply { this.seed = seed + 52 }.noise3D(xf, yf, zf)

        if (cave1 > 0.35f) return true
        if (cave2 > 0.48f && cave1 > 0.1f) return true
        if (cave3 > 0.3f && depthFactor > 0.5f) return true
        return false
    }

    // ─── Ore veins (3D) ───────────────────────────────────────────────

    fun oreAt(seed: Int, x: Int, y: Int, z: Int): BlockId? {
        if (y <= WORLD_BOTTOM + 2) return BlockId.Bedrock

        val xf = x.toFloat(); val yf = y.toFloat(); val zf = z.toFloat()

        val coal = make3D(0.12f).apply { this.seed = seed + 60 }.noise3D(xf, yf, zf)
        if (coal > 0.6f && y < 40) return BlockId.CoalOre

        val iron = make3D(0.1f).apply { this.seed = seed + 61 }.noise3D(xf, yf, zf)
        if (iron > 0.65f && y in 5..30) return BlockId.IronOre

        val gravel = make3D(0.08f).apply { this.seed = seed + 62 }.noise3D(xf, yf, zf)
        if (gravel > 0.55f && y in (WORLD_BOTTOM + 5)..25) return BlockId.Gravel

        return null
    }

    // ─── Tree placement (2D) ──────────────────────────────────────────

    fun treeDensity(seed: Int, x: Int, z: Int): Float =
        make2D(0.08f).apply { this.seed = seed + 200 }.noise2D(x.toFloat(), z.toFloat())

    fun shouldPlaceTree(seed: Int, x: Int, z: Int): Boolean {
        val surfaceH = heightAt(seed, x, z)
        if (surfaceH <= SEA_LEVEL + 1) return false
        val density = treeDensity(seed, x, z)
        if (density < 0.15f) return false
        val r = abs(hash(seed + 300, x, z)) % 100
        return r < 12
    }

    fun treeBlocks(seed: Int, x: Int, z: Int): List<Triple<Int, BlockId, Int>> {
        val blocks = mutableListOf<Triple<Int, BlockId, Int>>()
        val trunkHeight = 4 + abs(hash(seed + 310, x, z)) % 3

        for (y in 0 until trunkHeight) {
            blocks.add(Triple(0, BlockId.Log, y))
        }

        val canopyCenter = trunkHeight
        val canopyRadius = 2
        for (dy in -1..canopyRadius + 1) {
            for (dx in -canopyRadius..canopyRadius) {
                for (dz in -canopyRadius..canopyRadius) {
                    if (dx == 0 && dz == 0 && dy < 0) continue
                    val distSq = dx * dx + dy * dy + dz * dz
                    if (distSq <= canopyRadius * canopyRadius + 1) {
                        val cornerSkip = abs(hash(seed + 320, x + dx, z + dz)) % 100
                        if (distSq > canopyRadius * canopyRadius && cornerSkip > 40) continue
                        blocks.add(Triple(dx, BlockId.Leaves, canopyCenter + dy))
                    }
                }
            }
        }
        return blocks
    }

    // ─── Hash functions ────────────────────────────────────────────────

    private fun hash(seed: Int, x: Int, z: Int): Int {
        var h = seed.toLong()
        h = h * 374761393L + x.toLong() * 668265263L + z.toLong() * 2246822519L
        h = h xor (h ushr 13)
        h = h * 1274126177L
        h = h xor (h ushr 16)
        return h.toInt()
    }
}
