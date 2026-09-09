package strata

/**
 * Stripped-down port of FastNoiseLite v1.1.1 (MIT License).
 * Only includes OpenSimplex2 noise + FBm/Ridged fractals — everything
 * Strata needs for terrain heightmaps and 3D cave carving.
 *
 * Original: https://github.com/Auburn/FastNoiseLite
 */
class FastNoiseLite(seed: Int = 1337) {

    // ─── Enums ─────────────────────────────────────────────────────────

    enum class NoiseType { OpenSimplex2, OpenSimplex2S }
    enum class FractalType { None, FBm, Ridged }

    // ─── Settings ──────────────────────────────────────────────────────

    var seed: Int = seed; set(v) { field = v }
    var frequency: Float = 0.01f; set(v) { field = v }
    var noiseType: NoiseType = NoiseType.OpenSimplex2; set(v) { field = v; updateTransformType3D() }
    var fractalType: FractalType = FractalType.None; set(v) { field = v }
    var fractalOctaves: Int = 3; set(v) { field = v; recalcBounding() }
    var fractalLacunarity: Float = 2.0f; set(v) { field = v }
    var fractalGain: Float = 0.5f; set(v) { field = v; recalcBounding() }
    var fractalWeightedStrength: Float = 0.0f; set(v) { field = v }

    private var fractalBounding: Float = 1f / 1.75f
    private var transformType3D = TransformType3D.DefaultOpenSimplex2

    private enum class TransformType3D { None, DefaultOpenSimplex2 }

    private fun updateTransformType3D() {
        transformType3D = when (noiseType) {
            NoiseType.OpenSimplex2, NoiseType.OpenSimplex2S -> TransformType3D.DefaultOpenSimplex2
        }
    }

    private fun recalcBounding() {
        var amp = fractalGain
        var ampFractal = 1f
        for (i in 1 until fractalOctaves) { ampFractal += amp; amp *= fractalGain }
        fractalBounding = 1f / ampFractal
    }

    init { recalcBounding() }

    // ─── Public API ────────────────────────────────────────────────────

    /** 2D noise output bounded [-1, 1]. */
    fun noise2D(x: Float, y: Float): Float {
        var nx = x * frequency
        var ny = y * frequency
        if (noiseType == NoiseType.OpenSimplex2 || noiseType == NoiseType.OpenSimplex2S) {
            val s = (nx + ny) * F2; nx += s; ny += s
        }
        return when (fractalType) {
            FractalType.None -> genSingle(seed, nx, ny)
            FractalType.FBm -> genFBm2D(nx, ny)
            FractalType.Ridged -> genRidged2D(nx, ny)
        }
    }

    /** 3D noise output bounded [-1, 1]. */
    fun noise3D(x: Float, y: Float, z: Float): Float {
        var nx = x * frequency
        var ny = y * frequency
        var nz = z * frequency
        if (transformType3D == TransformType3D.DefaultOpenSimplex2) {
            val r = (nx + ny + nz) * (2f / 3f)
            nx = r - nx; ny = r - ny; nz = r - nz
        }
        return when (fractalType) {
            FractalType.None -> genSingle(seed, nx, ny, nz)
            FractalType.FBm -> genFBm3D(nx, ny, nz)
            FractalType.Ridged -> genRidged3D(nx, ny, nz)
        }
    }

    // ─── Fractal: FBm ──────────────────────────────────────────────────

    private fun genFBm2D(x: Float, y: Float): Float {
        var s = seed; var sum = 0f; var amp = fractalBounding; var freq = 1f
        var cx = x; var cy = y
        for (i in 0 until fractalOctaves) {
            sum += genSingle(s++, cx, cy) * amp
            amp *= lerp(1f, (genSingle(s - 1, cx, cy) + 1f) * 0.5f, fractalWeightedStrength)
            cx *= fractalLacunarity; cy *= fractalLacunarity; amp *= fractalGain
        }
        return sum
    }

    private fun genFBm3D(x: Float, y: Float, z: Float): Float {
        var s = seed; var sum = 0f; var amp = fractalBounding
        var cx = x; var cy = y; var cz = z
        for (i in 0 until fractalOctaves) {
            sum += genSingle(s++, cx, cy, cz) * amp
            amp *= lerp(1f, (genSingle(s - 1, cx, cy, cz) + 1f) * 0.5f, fractalWeightedStrength)
            cx *= fractalLacunarity; cy *= fractalLacunarity; cz *= fractalLacunarity; amp *= fractalGain
        }
        return sum
    }

    // ─── Fractal: Ridged ───────────────────────────────────────────────

    private fun genRidged2D(x: Float, y: Float): Float {
        var s = seed; var sum = 0f; var amp = fractalBounding
        var cx = x; var cy = y
        for (i in 0 until fractalOctaves) {
            val n = kotlin.math.abs(genSingle(s++, cx, cy))
            sum += (n * -2f + 1f) * amp
            amp *= lerp(1f, 1f - n, fractalWeightedStrength)
            cx *= fractalLacunarity; cy *= fractalLacunarity; amp *= fractalGain
        }
        return sum
    }

    private fun genRidged3D(x: Float, y: Float, z: Float): Float {
        var s = seed; var sum = 0f; var amp = fractalBounding
        var cx = x; var cy = y; var cz = z
        for (i in 0 until fractalOctaves) {
            val n = kotlin.math.abs(genSingle(s++, cx, cy, cz))
            sum += (n * -2f + 1f) * amp
            amp *= lerp(1f, 1f - n, fractalWeightedStrength)
            cx *= fractalLacunarity; cy *= fractalLacunarity; cz *= fractalLacunarity; amp *= fractalGain
        }
        return sum
    }

    // ─── Single noise dispatch ─────────────────────────────────────────

    private fun genSingle(seed: Int, x: Float, y: Float): Float = when (noiseType) {
        NoiseType.OpenSimplex2 -> singleSimplex2D(seed, x, y)
        NoiseType.OpenSimplex2S -> singleOpenSimplex2S2D(seed, x, y)
    }

    private fun genSingle(seed: Int, x: Float, y: Float, z: Float): Float = when (noiseType) {
        NoiseType.OpenSimplex2 -> singleOpenSimplex2_3D(seed, x, y, z)
        NoiseType.OpenSimplex2S -> singleOpenSimplex2S_3D(seed, x, y, z)
    }

    // ─── OpenSimplex2 2D ──────────────────────────────────────────────

    private fun singleSimplex2D(seed: Int, x: Float, y: Float): Float {
        val i = fastFloor(x); val j = fastFloor(y)
        val xi = x - i; val yi = y - j
        val t = (xi + yi) * G2
        val x0 = xi - t; val y0 = yi - t
        val ip = i * PrimeX; val jp = j * PrimeY

        val a = 0.5f - x0 * x0 - y0 * y0
        val n0 = if (a > 0) { val a2 = a * a; a2 * a2 * gradCoord2D(seed, ip, jp, x0, y0) } else 0f

        val c = (2f * (1f - 2f * G2) * (1f / G2 - 2f)) * t + (-2f * (1f - 2f * G2) * (1f - 2f * G2) + a)
        val n2 = if (c > 0) {
            val x2 = x0 + (2f * G2 - 1f); val y2 = y0 + (2f * G2 - 1f)
            val c2 = c * c; c2 * c2 * gradCoord2D(seed, ip + PrimeX, jp + PrimeY, x2, y2)
        } else 0f

        val n1: Float
        if (y0 > x0) {
            val x1 = x0 + G2; val y1 = y0 + (G2 - 1f)
            val b = 0.5f - x1 * x1 - y1 * y1
            n1 = if (b > 0) { val b2 = b * b; b2 * b2 * gradCoord2D(seed, ip, jp + PrimeY, x1, y1) } else 0f
        } else {
            val x1 = x0 + (G2 - 1f); val y1 = y0 + G2
            val b = 0.5f - x1 * x1 - y1 * y1
            n1 = if (b > 0) { val b2 = b * b; b2 * b2 * gradCoord2D(seed, ip + PrimeX, jp, x1, y1) } else 0f
        }

        return (n0 + n1 + n2) * 99.83685446303647f
    }

    // ─── OpenSimplex2S 2D ─────────────────────────────────────────────

    private fun singleOpenSimplex2S2D(seed: Int, x: Float, y: Float): Float {
        val i = fastFloor(x); val j = fastFloor(y)
        val xi = x - i; val yi = y - j
        val ip = i * PrimeX; val jp = j * PrimeY
        val ip1 = ip + PrimeX; val jp1 = jp + PrimeY

        val t = (xi + yi) * G2f
        val x0 = xi - t; val y0 = yi - t

        var value = run {
            val a0 = (2f / 3f) - x0 * x0 - y0 * y0
            val a02 = a0 * a0; a02 * a02 * gradCoord2D(seed, ip, jp, x0, y0)
        }

        val x1 = x0 - (1f - 2f * G2f); val y1 = y0 - (1f - 2f * G2f)
        val a1 = (2f * (1f - 2f * G2f) * (1f / G2f - 2f)) * t + (-2f * (1f - 2f * G2f) * (1f - 2f * G2f) + (2f / 3f) - x0 * x0 - y0 * y0)
        val a1_2 = a1 * a1; value += a1_2 * a1_2 * gradCoord2D(seed, ip1, jp1, x1, y1)

        val xmyi = xi - yi
        if (t > G2f) {
            if (xi + xmyi > 1) {
                val x2 = x0 + (3f * G2f - 2f); val y2 = y0 + (3f * G2f - 1f)
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip + (PrimeX shl 1), jp + PrimeY, x2, y2) }
            } else {
                val x2 = x0 + G2f; val y2 = y0 + (G2f - 1f)
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip, jp + PrimeY, x2, y2) }
            }
            if (yi - xmyi > 1) {
                val x3 = x0 + (3f * G2f - 1f); val y3 = y0 + (3f * G2f - 2f)
                val a3 = (2f / 3f) - x3 * x3 - y3 * y3
                if (a3 > 0) { val a3_2 = a3 * a3; value += a3_2 * a3_2 * gradCoord2D(seed, ip + PrimeX, jp + (PrimeY shl 1), x3, y3) }
            } else {
                val x3 = x0 + (G2f - 1f); val y3 = y0 + G2f
                val a3 = (2f / 3f) - x3 * x3 - y3 * y3
                if (a3 > 0) { val a3_2 = a3 * a3; value += a3_2 * a3_2 * gradCoord2D(seed, ip + PrimeX, jp, x3, y3) }
            }
        } else {
            if (xi + xmyi < 0) {
                val x2 = x0 + (1f - G2f); val y2 = y0 - G2f
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip - PrimeX, jp, x2, y2) }
            } else {
                val x2 = x0 + (G2f - 1f); val y2 = y0 + G2f
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip + PrimeX, jp, x2, y2) }
            }
            if (yi < xmyi) {
                val x2 = x0 - G2f; val y2 = y0 - (G2f - 1f)
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip, jp - PrimeY, x2, y2) }
            } else {
                val x2 = x0 + G2f; val y2 = y0 + (G2f - 1f)
                val a2 = (2f / 3f) - x2 * x2 - y2 * y2
                if (a2 > 0) { val a2_2 = a2 * a2; value += a2_2 * a2_2 * gradCoord2D(seed, ip, jp + PrimeY, x2, y2) }
            }
        }
        return value * 18.24196194486065f
    }

    // ─── OpenSimplex2 3D ──────────────────────────────────────────────

    private fun singleOpenSimplex2_3D(seed: Int, x: Float, y: Float, z: Float): Float {
        val i0 = fastRound(x); val j0 = fastRound(y); val k0 = fastRound(z)
        var x0 = x - i0; var y0 = y - j0; var z0 = z - k0
        var xNSign = (-1f - x0).toInt() or 1
        var yNSign = (-1f - y0).toInt() or 1
        var zNSign = (-1f - z0).toInt() or 1
        var ax0 = xNSign * -x0; var ay0 = yNSign * -y0; var az0 = zNSign * -z0

        var ip = i0 * PrimeX; var jp = j0 * PrimeY; var kp = k0 * PrimeZ
        var value = 0f
        var a = 0.6f - x0 * x0 - (y0 * y0 + z0 * z0)
        var curSeed = seed

        for (l in 0..1) {
            if (a > 0) { val a2 = a * a; value += a2 * a2 * gradCoord3D(curSeed, ip, jp, kp, x0, y0, z0) }

            var b: Float
            if (ax0 >= ay0 && ax0 >= az0) {
                b = a + ax0 + ax0
                if (b > 1) { b -= 1f; val b2 = b * b; value += b2 * b2 * gradCoord3D(curSeed, ip - xNSign * PrimeX, jp, kp, x0 + xNSign, y0, z0) }
            } else if (ay0 > ax0 && ay0 >= az0) {
                b = a + ay0 + ay0
                if (b > 1) { b -= 1f; val b2 = b * b; value += b2 * b2 * gradCoord3D(curSeed, ip, jp - yNSign * PrimeY, kp, x0, y0 + yNSign, z0) }
            } else {
                b = a + az0 + az0
                if (b > 1) { b -= 1f; val b2 = b * b; value += b2 * b2 * gradCoord3D(curSeed, ip, jp, kp - zNSign * PrimeZ, x0, y0, z0 + zNSign) }
            }

            if (l == 1) break

            val ax0n = 0.5f - ax0; val ay0n = 0.5f - ay0; val az0n = 0.5f - az0
            x0 = xNSign * ax0n; y0 = yNSign * ay0n; z0 = zNSign * az0n
            a += (0.75f - ax0n) - (ay0n + az0n)

            ip += (xNSign ushr 1) and PrimeX
            jp += (yNSign ushr 1) and PrimeY
            kp += (zNSign ushr 1) and PrimeZ

            curSeed = curSeed.inv()
            xNSign = -xNSign; yNSign = -yNSign; zNSign = -zNSign
        }

        return value * 32.69428253173828125f
    }

    // ─── OpenSimplex2S 3D ─────────────────────────────────────────────

    private fun singleOpenSimplex2S_3D(seed: Int, x: Float, y: Float, z: Float): Float {
        val i = fastFloor(x); val j = fastFloor(y); val k = fastFloor(z)
        val xi = x - i; val yi = y - j; val zi = z - k
        val ip = i * PrimeX; val jp = j * PrimeY; val kp = k * PrimeZ
        val seed2 = seed + 1293373

        val xNMask = (-0.5f - xi).toInt()
        val yNMask = (-0.5f - yi).toInt()
        val zNMask = (-0.5f - zi).toInt()

        val x0 = xi + xNMask; val y0 = yi + yNMask; val z0 = zi + zNMask
        val a0 = 0.75f - x0 * x0 - y0 * y0 - z0 * z0
        var value = run { val a0_2 = a0 * a0; a0_2 * a0_2 * gradCoord3D(seed,
            ip + (xNMask and PrimeX), jp + (yNMask and PrimeY), kp + (zNMask and PrimeZ), x0, y0, z0) }

        val x1 = xi - 0.5f; val y1 = yi - 0.5f; val z1 = zi - 0.5f
        val a1 = 0.75f - x1 * x1 - y1 * y1 - z1 * z1
        value += run { val a1_2 = a1 * a1; a1_2 * a1_2 * gradCoord3D(seed2, ip + PrimeX, jp + PrimeY, kp + PrimeZ, x1, y1, z1) }

        val xAFlipMask0 = ((xNMask or 1) shl 1) * x1
        val yAFlipMask0 = ((yNMask or 1) shl 1) * y1
        val zAFlipMask0 = ((zNMask or 1) shl 1) * z1
        val xAFlipMask1 = (-2 - (xNMask shl 2)) * x1 - 1f
        val yAFlipMask1 = (-2 - (yNMask shl 2)) * y1 - 1f
        val zAFlipMask1 = (-2 - (zNMask shl 2)) * z1 - 1f

        // X axis
        var skip5 = false
        val a2 = xAFlipMask0 + a0
        if (a2 > 0) {
            val x2 = x0 - (xNMask or 1); val y2 = y0; val z2 = z0
            value += run { val a2_2 = a2 * a2; a2_2 * a2_2 * gradCoord3D(seed,
                ip + (xNMask.inv() and PrimeX), jp + (yNMask and PrimeY), kp + (zNMask and PrimeZ), x2, y2, z2) }
        } else {
            val a3 = yAFlipMask0 + zAFlipMask0 + a0
            if (a3 > 0) {
                val x3 = x0; val y3 = y0 - (yNMask or 1); val z3 = z0 - (zNMask or 1)
                value += run { val a3_2 = a3 * a3; a3_2 * a3_2 * gradCoord3D(seed,
                    ip + (xNMask and PrimeX), jp + (yNMask.inv() and PrimeY), kp + (zNMask.inv() and PrimeZ), x3, y3, z3) }
            }
            val a4 = xAFlipMask1 + a1
            if (a4 > 0) {
                val x4 = (xNMask or 1) + x1; val y4 = y1; val z4 = z1
                value += run { val a4_2 = a4 * a4; a4_2 * a4_2 * gradCoord3D(seed2,
                    ip + (xNMask and (PrimeX * 2)), jp + PrimeY, kp + PrimeZ, x4, y4, z4) }
                skip5 = true
            }
        }

        // Y axis
        var skip9 = false
        val a6 = yAFlipMask0 + a0
        if (a6 > 0) {
            val x6 = x0; val y6 = y0 - (yNMask or 1); val z6 = z0
            value += run { val a6_2 = a6 * a6; a6_2 * a6_2 * gradCoord3D(seed,
                ip + (xNMask and PrimeX), jp + (yNMask.inv() and PrimeY), kp + (zNMask and PrimeZ), x6, y6, z6) }
        } else {
            val a7 = xAFlipMask0 + zAFlipMask0 + a0
            if (a7 > 0) {
                val x7 = x0 - (xNMask or 1); val y7 = y0; val z7 = z0 - (zNMask or 1)
                value += run { val a7_2 = a7 * a7; a7_2 * a7_2 * gradCoord3D(seed,
                    ip + (xNMask.inv() and PrimeX), jp + (yNMask and PrimeY), kp + (zNMask.inv() and PrimeZ), x7, y7, z7) }
            }
            val a8 = yAFlipMask1 + a1
            if (a8 > 0) {
                val x8 = x1; val y8 = (yNMask or 1) + y1; val z8 = z1
                value += run { val a8_2 = a8 * a8; a8_2 * a8_2 * gradCoord3D(seed2,
                    ip + PrimeX, jp + (yNMask and (PrimeY shl 1)), kp + PrimeZ, x8, y8, z8) }
                skip9 = true
            }
        }

        // Z axis
        var skipD = false
        val aA = zAFlipMask0 + a0
        if (aA > 0) {
            val xA = x0; val yA = y0; val zA = z0 - (zNMask or 1)
            value += run { val aA_2 = aA * aA; aA_2 * aA_2 * gradCoord3D(seed,
                ip + (xNMask and PrimeX), jp + (yNMask and PrimeY), kp + (zNMask.inv() and PrimeZ), xA, yA, zA) }
        } else {
            val aB = xAFlipMask0 + yAFlipMask0 + a0
            if (aB > 0) {
                val xB = x0 - (xNMask or 1); val yB = y0 - (yNMask or 1); val zB = z0
                value += run { val aB_2 = aB * aB; aB_2 * aB_2 * gradCoord3D(seed,
                    ip + (xNMask.inv() and PrimeX), jp + (yNMask.inv() and PrimeY), kp + (zNMask and PrimeZ), xB, yB, zB) }
            }
            val aC = zAFlipMask1 + a1
            if (aC > 0) {
                val xC = x1; val yC = y1; val zC = (zNMask or 1) + z1
                value += run { val aC_2 = aC * aC; aC_2 * aC_2 * gradCoord3D(seed2,
                    ip + PrimeX, jp + PrimeY, kp + (zNMask and (PrimeZ shl 1)), xC, yC, zC) }
                skipD = true
            }
        }

        // Cross terms
        if (!skip5) {
            val a5 = yAFlipMask1 + zAFlipMask1 + a1
            if (a5 > 0) {
                val x5 = x1; val y5 = (yNMask or 1) + y1; val z5 = (zNMask or 1) + z1
                value += run { val a5_2 = a5 * a5; a5_2 * a5_2 * gradCoord3D(seed2,
                    ip + PrimeX, jp + (yNMask and (PrimeY shl 1)), kp + (zNMask and (PrimeZ shl 1)), x5, y5, z5) }
            }
        }
        if (!skip9) {
            val a9 = xAFlipMask1 + zAFlipMask1 + a1
            if (a9 > 0) {
                val x9 = (xNMask or 1) + x1; val y9 = y1; val z9 = (zNMask or 1) + z1
                value += run { val a9_2 = a9 * a9; a9_2 * a9_2 * gradCoord3D(seed2,
                    ip + (xNMask and (PrimeX * 2)), jp + PrimeY, kp + (zNMask and (PrimeZ shl 1)), x9, y9, z9) }
            }
        }
        if (!skipD) {
            val aD = xAFlipMask1 + yAFlipMask1 + a1
            if (aD > 0) {
                val xD = (xNMask or 1) + x1; val yD = (yNMask or 1) + y1; val zD = z1
                value += run { val aD_2 = aD * aD; aD_2 * aD_2 * gradCoord3D(seed2,
                    ip + (xNMask and (PrimeX shl 1)), jp + (yNMask and (PrimeY shl 1)), kp + PrimeZ, xD, yD, zD) }
            }
        }

        return value * 9.046026385208288f
    }

    // ─── Hashing & Gradient Coord ──────────────────────────────────────

    private fun hash(seed: Int, xPrimed: Int, yPrimed: Int): Int =
        (seed xor xPrimed xor yPrimed) * 0x27d4eb2d

    private fun hash(seed: Int, xPrimed: Int, yPrimed: Int, zPrimed: Int): Int =
        (seed xor xPrimed xor yPrimed xor zPrimed) * 0x27d4eb2d

    private fun gradCoord2D(seed: Int, xPrimed: Int, yPrimed: Int, xd: Float, yd: Float): Float {
        var h = hash(seed, xPrimed, yPrimed)
        h = h xor (h shr 15)
        h = h and (127 shl 1)
        return xd * GRAD_2D[h] + yd * GRAD_2D[h or 1]
    }

    private fun gradCoord3D(seed: Int, xPrimed: Int, yPrimed: Int, zPrimed: Int, xd: Float, yd: Float, zd: Float): Float {
        var h = hash(seed, xPrimed, yPrimed, zPrimed)
        h = h xor (h shr 15)
        h = h and (63 shl 2)
        return xd * GRAD_3D[h] + yd * GRAD_3D[h or 1] + zd * GRAD_3D[h or 2]
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    companion object {
        private const val F2 = 0.3660254038f
        private const val G2 = 0.2113248654f
        private const val G2f = 0.2113248654f
        private const val PrimeX = 501125321
        private const val PrimeY = 1136930381
        private const val PrimeZ = 1720413743

        private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
        private fun fastFloor(f: Float): Int = if (f >= 0) f.toInt() else f.toInt() - 1
        private fun fastRound(f: Float): Int = if (f >= 0) (f + 0.5f).toInt() else (f - 0.5f).toInt()

        /** 2D gradient table (16 vectors × 2 components = 32 entries, repeated for indexing). */
        private val GRAD_2D = floatArrayOf(
            0.130526192220052f, 0.99144486137381f, 0.38268343236509f, 0.923879532511287f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f,
            0.923879532511287f, 0.38268343236509f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.923879532511287f, -0.38268343236509f,
            0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.38268343236509f, -0.923879532511287f, 0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f, -0.38268343236509f, -0.923879532511287f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f,
            -0.923879532511287f, -0.38268343236509f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.923879532511287f, 0.38268343236509f,
            -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.38268343236509f, 0.923879532511287f, -0.130526192220052f, 0.99144486137381f,
            0.130526192220052f, 0.99144486137381f, 0.38268343236509f, 0.923879532511287f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f,
            0.923879532511287f, 0.38268343236509f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.923879532511287f, -0.38268343236509f,
            0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.38268343236509f, -0.923879532511287f, 0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f, -0.38268343236509f, -0.923879532511287f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f,
            -0.923879532511287f, -0.38268343236509f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.923879532511287f, 0.38268343236509f,
            -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.38268343236509f, 0.923879532511287f, -0.130526192220052f, 0.99144486137381f,
            0.130526192220052f, 0.99144486137381f, 0.38268343236509f, 0.923879532511287f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f,
            0.923879532511287f, 0.38268343236509f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.923879532511287f, -0.38268343236509f,
            0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.38268343236509f, -0.923879532511287f, 0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f, -0.38268343236509f, -0.923879532511287f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f,
            -0.923879532511287f, -0.38268343236509f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.923879532511287f, 0.38268343236509f,
            -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.38268343236509f, 0.923879532511287f, -0.130526192220052f, 0.99144486137381f,
            0.130526192220052f, 0.99144486137381f, 0.38268343236509f, 0.923879532511287f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f,
            0.923879532511287f, 0.38268343236509f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.923879532511287f, -0.38268343236509f,
            0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.38268343236509f, -0.923879532511287f, 0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f, -0.38268343236509f, -0.923879532511287f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f,
            -0.923879532511287f, -0.38268343236509f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.923879532511287f, 0.38268343236509f,
            -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.38268343236509f, 0.923879532511287f, -0.130526192220052f, 0.99144486137381f,
            0.130526192220052f, 0.99144486137381f, 0.38268343236509f, 0.923879532511287f, 0.608761429008721f, 0.793353340291235f, 0.793353340291235f, 0.608761429008721f,
            0.923879532511287f, 0.38268343236509f, 0.99144486137381f, 0.130526192220051f, 0.99144486137381f, -0.130526192220051f, 0.923879532511287f, -0.38268343236509f,
            0.793353340291235f, -0.60876142900872f, 0.608761429008721f, -0.793353340291235f, 0.38268343236509f, -0.923879532511287f, 0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f, -0.38268343236509f, -0.923879532511287f, -0.608761429008721f, -0.793353340291235f, -0.793353340291235f, -0.608761429008721f,
            -0.923879532511287f, -0.38268343236509f, -0.99144486137381f, -0.130526192220052f, -0.99144486137381f, 0.130526192220051f, -0.923879532511287f, 0.38268343236509f,
            -0.793353340291235f, 0.608761429008721f, -0.608761429008721f, 0.793353340291235f, -0.38268343236509f, 0.923879532511287f, -0.130526192220052f, 0.99144486137381f,
            0.38268343236509f, 0.923879532511287f, 0.923879532511287f, 0.38268343236509f, 0.923879532511287f, -0.38268343236509f, 0.38268343236509f, -0.923879532511287f,
            -0.38268343236509f, -0.923879532511287f, -0.923879532511287f, -0.38268343236509f, -0.923879532511287f, 0.38268343236509f, -0.38268343236509f, 0.923879532511287f,
        )

        /** 3D gradient table (64 vectors × 4 components = 256 entries). */
        private val GRAD_3D = floatArrayOf(
            0f, 1f, 1f, 0f,  0f,-1f, 1f, 0f,  0f, 1f,-1f, 0f,  0f,-1f,-1f, 0f,
            1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f,  1f, 0f,-1f, 0f, -1f, 0f,-1f, 0f,
            1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f,  1f,-1f, 0f, 0f, -1f,-1f, 0f, 0f,
            0f, 1f, 1f, 0f,  0f,-1f, 1f, 0f,  0f, 1f,-1f, 0f,  0f,-1f,-1f, 0f,
            1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f,  1f, 0f,-1f, 0f, -1f, 0f,-1f, 0f,
            1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f,  1f,-1f, 0f, 0f, -1f,-1f, 0f, 0f,
            0f, 1f, 1f, 0f,  0f,-1f, 1f, 0f,  0f, 1f,-1f, 0f,  0f,-1f,-1f, 0f,
            1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f,  1f, 0f,-1f, 0f, -1f, 0f,-1f, 0f,
            1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f,  1f,-1f, 0f, 0f, -1f,-1f, 0f, 0f,
            0f, 1f, 1f, 0f,  0f,-1f, 1f, 0f,  0f, 1f,-1f, 0f,  0f,-1f,-1f, 0f,
            1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f,  1f, 0f,-1f, 0f, -1f, 0f,-1f, 0f,
            1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f,  1f,-1f, 0f, 0f, -1f,-1f, 0f, 0f,
            0f, 1f, 1f, 0f,  0f,-1f, 1f, 0f,  0f, 1f,-1f, 0f,  0f,-1f,-1f, 0f,
            1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f,  1f, 0f,-1f, 0f, -1f, 0f,-1f, 0f,
            1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f,  1f,-1f, 0f, 0f, -1f,-1f, 0f, 0f,
            1f, 1f, 0f, 0f,  0f,-1f, 1f, 0f, -1f, 1f, 0f, 0f,  0f,-1f,-1f, 0f
        )
    }
}
