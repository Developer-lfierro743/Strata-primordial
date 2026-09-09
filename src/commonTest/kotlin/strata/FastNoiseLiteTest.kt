package strata

import kotlin.test.Test
import kotlin.test.assertTrue

class FastNoiseLiteTest {

    @Test
    fun singleCallDoesNotCrash() {
        val noise = FastNoiseLite(42)
        noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
        noise.frequency = 0.01f
        val result = noise.noise2D(0f, 0f)
        println("Result: $result")
    }

    @Test
    fun basic2DNoiseDoesNotCrash() {
        val noise = FastNoiseLite(42)
        noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
        noise.frequency = 0.01f
        // Wide range to test gradient table coverage
        for (x in -50..50) {
            for (z in -50..50) {
                try {
                    noise.noise2D(x.toFloat(), z.toFloat())
                } catch (e: Exception) {
                    throw AssertionError("Crash at x=$x z=$z: ${e::class.simpleName}: ${e.message}", e)
                }
            }
        }
    }

    @Test
    fun basic3DNoiseDoesNotCrash() {
        val noise = FastNoiseLite(42)
        noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
        noise.frequency = 0.04f
        for (x in -20..20) {
            for (y in -20..20) {
                for (z in -20..20) {
                    noise.noise3D(x.toFloat(), y.toFloat(), z.toFloat())
                }
            }
        }
    }

    @Test
    fun fbm2DNoiseDoesNotCrash() {
        val noise = FastNoiseLite(42)
        noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
        noise.fractalType = FastNoiseLite.FractalType.FBm
        noise.fractalOctaves = 4
        noise.frequency = 0.018f
        for (x in -50..50) {
            for (z in -50..50) {
                noise.noise2D(x.toFloat(), z.toFloat())
            }
        }
    }

    @Test
    fun ridged2DNoiseDoesNotCrash() {
        val noise = FastNoiseLite(42)
        noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
        noise.fractalType = FastNoiseLite.FractalType.Ridged
        noise.fractalOctaves = 3
        noise.frequency = 0.009f
        for (x in -50..50) {
            for (z in -50..50) {
                noise.noise2D(x.toFloat(), z.toFloat())
            }
        }
    }

    @Test
    fun terrainHeightDoesNotCrash() {
        val terrainNoise = FastNoiseLite().apply {
            noiseType = FastNoiseLite.NoiseType.OpenSimplex2
            frequency = 0.018f
            fractalOctaves = 4
            fractalType = FastNoiseLite.FractalType.FBm
        }
        for (x in -100..100) {
            for (z in -100..100) {
                terrainNoise.seed = 12345
                terrainNoise.noise2D(x.toFloat(), z.toFloat())
            }
        }
    }

    @Test
    fun multipleSeedsWork() {
        for (seed in 0..200) {
            val noise = FastNoiseLite(seed)
            noise.noiseType = FastNoiseLite.NoiseType.OpenSimplex2
            noise.frequency = 0.01f
            noise.noise2D(1f, 1f)
        }
    }
}
