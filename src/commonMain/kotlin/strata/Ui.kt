package strata

/**
 * Shared clip-space quad renderer + built-in 5x7 pixel font.
 *
 * Every screen (in-game HUD, title screen, future menus) draws through [Ui]:
 * it emits colored quads directly in the pipeline's clip space (NDC) and the
 * result is uploaded to the HUD vertex buffer with an identity MVP. No
 * textures, no font files — just rectangles.
 *
 * Note: this is a Vulkan pipeline, where NDC y=+1 is the BOTTOM of the screen
 * (the world projection flips Y in perspectiveMatrix; Ui flips it here so
 * pixel coordinates stay y-down).
 */
object Ui {

    private const val FLOATS_PER_VERTEX = 7

    /* ── 5x7 pixel font: glyph -> 7 rows, row bits 4..0 (bit 4 = leftmost) ── */
    private val FONT: Map<Char, IntArray> = buildMap {
        put('0', intArrayOf(0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E))
        put('1', intArrayOf(0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E))
        put('2', intArrayOf(0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F))
        put('3', intArrayOf(0x1F, 0x02, 0x04, 0x0E, 0x01, 0x11, 0x0E))
        put('4', intArrayOf(0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02))
        put('5', intArrayOf(0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E))
        put('6', intArrayOf(0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E))
        put('7', intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08))
        put('8', intArrayOf(0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E))
        put('9', intArrayOf(0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C))
        put('A', intArrayOf(0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11))
        put('B', intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E))
        put('C', intArrayOf(0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E))
        put('D', intArrayOf(0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E))
        put('E', intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F))
        put('F', intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10))
        put('G', intArrayOf(0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F))
        put('H', intArrayOf(0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11))
        put('I', intArrayOf(0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E))
        put('J', intArrayOf(0x07, 0x02, 0x02, 0x02, 0x12, 0x12, 0x0C))
        put('K', intArrayOf(0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11))
        put('L', intArrayOf(0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F))
        put('M', intArrayOf(0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11))
        put('N', intArrayOf(0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11))
        put('O', intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E))
        put('P', intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10))
        put('Q', intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D))
        put('R', intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11))
        put('S', intArrayOf(0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E))
        put('T', intArrayOf(0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04))
        put('U', intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E))
        put('V', intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04))
        put('W', intArrayOf(0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11))
        put('X', intArrayOf(0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11))
        put('Y', intArrayOf(0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04))
        put('Z', intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F))
        put('-', intArrayOf(0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00))
        put('.', intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C))
        put(',', intArrayOf(0x00, 0x00, 0x00, 0x00, 0x0C, 0x04, 0x08))
        put(':', intArrayOf(0x00, 0x0C, 0x0C, 0x00, 0x0C, 0x0C, 0x00))
        put('/', intArrayOf(0x01, 0x02, 0x02, 0x04, 0x08, 0x08, 0x10))
        put('%', intArrayOf(0x11, 0x13, 0x02, 0x04, 0x08, 0x19, 0x11))
        put('+', intArrayOf(0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00))
        put('=', intArrayOf(0x00, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00))
        put('(', intArrayOf(0x02, 0x04, 0x08, 0x08, 0x08, 0x04, 0x02))
        put(')', intArrayOf(0x08, 0x04, 0x02, 0x02, 0x02, 0x04, 0x08))
        put('!', intArrayOf(0x04, 0x04, 0x04, 0x04, 0x04, 0x00, 0x04))
        put('?', intArrayOf(0x0E, 0x11, 0x01, 0x02, 0x04, 0x00, 0x04))
        put('*', intArrayOf(0x00, 0x0A, 0x1F, 0x0A, 0x1F, 0x0A, 0x00))
        put('<', intArrayOf(0x02, 0x04, 0x08, 0x10, 0x08, 0x04, 0x02))
        put('>', intArrayOf(0x08, 0x04, 0x02, 0x01, 0x02, 0x04, 0x08))
        put('[', intArrayOf(0x0E, 0x08, 0x08, 0x08, 0x08, 0x08, 0x0E))
        put(']', intArrayOf(0x0E, 0x02, 0x02, 0x02, 0x02, 0x02, 0x0E))
        put('#', intArrayOf(0x0A, 0x0A, 0x1F, 0x0A, 0x1F, 0x0A, 0x0A))
    }

    private var floats = FloatArray(16384)
    private var count = 0
    var winW = 1280f
        private set
    var winH = 720f
        private set

    /** Start a fresh frame: window size in pixels, y-down coordinates. */
    fun begin(w: Int, h: Int) {
        winW = w.toFloat()
        winH = h.toFloat()
        count = 0
    }

    private fun ensure(extra: Int) {
        if (count + extra > floats.size) {
            var ns = floats.size
            while (ns < count + extra) ns *= 2
            floats = floats.copyOf(ns)
        }
    }

    /** Emit a quad spanning pixel rect (px0,py0)-(px1,py1), y-down. */
    private fun quad(px0: Float, py0: Float, px1: Float, py1: Float, r: Float, g: Float, b: Float) {
        val x0 = px0 / winW * 2f - 1f
        val x1 = px1 / winW * 2f - 1f
        val y0 = py1 / winH * 2f - 1f  // bottom pixel -> bottom of screen (y=+1)
        val y1 = py0 / winH * 2f - 1f  // top pixel -> top of screen (y=-1)
        ensure(6 * FLOATS_PER_VERTEX)
        fun v(x: Float, y: Float) {
            floats[count++] = x
            floats[count++] = y
            floats[count++] = 0f
            floats[count++] = r
            floats[count++] = g
            floats[count++] = b
            floats[count++] = ChunkMesher.MATERIAL_SOLID
        }
        v(x0, y0); v(x1, y0); v(x1, y1)
        v(x0, y0); v(x1, y1); v(x0, y1)
    }

    /**
     * Emit an arbitrary convex quad through its four pixel corners, given in
     * perimeter order (any winding — culling is off). This lets the HUD draw
     * isometric/3D faces (parallelograms) instead of only axis-aligned rects.
     * The [r]/[g]/[b] color is shared by both triangles.
     */
    internal fun quad4(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
        r: Float, g: Float, b: Float
    ) {
        ensure(6 * FLOATS_PER_VERTEX)
        fun v(x: Float, y: Float) {
            floats[count++] = x / winW * 2f - 1f
            floats[count++] = y / winH * 2f - 1f
            floats[count++] = 0f
            floats[count++] = r
            floats[count++] = g
            floats[count++] = b
            floats[count++] = ChunkMesher.MATERIAL_SOLID
        }
        v(x0, y0); v(x1, y1); v(x2, y2)
        v(x0, y0); v(x2, y2); v(x3, y3)
    }

    /** Filled rect at pixel (x,y) with size (w,h), y-down. */
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float) =
        quad(x, y, x + w, y + h, r, g, b)

    /** Draw a text string; (x,y) is the top-left, each cell is scale px. */
    fun text(x: Float, y: Float, scale: Float, r: Float, g: Float, b: Float, str: String) {
        var cx = x
        for (ch in str.uppercase()) {
            if (ch == ' ') {
                cx += 6 * scale
                continue
            }
            val glyph = FONT[ch] ?: continue
            for (row in 0 until 7) {
                val bits = glyph[row]
                for (col in 0 until 5) {
                    if ((bits shr (4 - col)) and 1 == 1) {
                        quad(cx + col * scale, y + row * scale, cx + (col + 1) * scale, y + (row + 1) * scale, r, g, b)
                    }
                }
            }
            cx += 6 * scale
        }
    }

    /** Right-aligned text ending at pixel (right,y). */
    fun textRight(right: Float, y: Float, scale: Float, r: Float, g: Float, b: Float, str: String) =
        text(right - str.length * 6 * scale, y, scale, r, g, b, str)

    /** Centered text: (cx,y) is the horizontal center / top of the text. */
    fun textCentered(cx: Float, y: Float, scale: Float, r: Float, g: Float, b: Float, str: String) =
        text(cx - str.length * 6 * scale / 2f, y, scale, r, g, b, str)

    /** Finish the frame and return the vertex data (7 floats per vertex). */
    fun end(): FloatArray = floats.copyOf(count)

    /** Internal: render a single character (used by tests). */
    internal fun renderChar(ch: Char, w: Int = 1280, h: Int = 720): FloatArray {
        begin(w, h)
        text(0f, 0f, 2f, 1f, 1f, 1f, ch.toString())
        return end()
    }
}
