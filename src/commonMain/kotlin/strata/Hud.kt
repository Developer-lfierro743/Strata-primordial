package strata

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * In-game HUD overlay. Draws through the shared [Ui] renderer: crosshair,
 * compass, animated health/hunger bars, minimap, info panel and hotbar —
 * all as clip-space quads with the built-in pixel font.
 */
object Hud {

    /** Everything the HUD needs to draw for one frame. */
    data class HudState(
        val camX: Float, val camY: Float, val camZ: Float,
        val yaw: Float,
        val fps: Int,
        val seed: Int,
        val chunks: Int,
        /** Double hearts: 40 HP = 20 hearts (formula: 2x hearts). */
        val health: Int = Survival.MAX_HEALTH.toInt(),
        val hunger: Int = 20,
        val thirst: Int = 20,
        /** Animated bar fill widths, eased toward the stat targets. */
        val healthDisplay: Float = health.toFloat(),
        val hungerDisplay: Float = hunger.toFloat(),
        val thirstDisplay: Float = thirst.toFloat(),
        /** Slower "trail" values — the Minecraft-style damage indicator. */
        val healthGhost: Float = healthDisplay,
        val hungerGhost: Float = hungerDisplay,
        val thirstGhost: Float = thirstDisplay,
        /** XP progress toward the next level, in 0..1 (drives the XP bar). */
        val xp: Float = 0f,
        val xpLevel: Int = 0,
        val selectedSlot: Int = 0,
        /** Seconds since launch — drives the selection-pop animation. */
        val time: Float = 0f,
        /** Live hotbar contents (nullable slots — the inventory can empty one). */
        val hotbar: List<HotbarItem?> = HOTBAR,
        /** Drives which HUD elements show: spectator hides bars/hotbar/crosshair,
         * creative keeps health but drops hunger + thirst. */
        val gamemode: GameMode = GameMode.SURVIVAL,
        /** Selected GPU adapter name — F3-style debug readout. */
        val gpuName: String = "Unknown GPU",
        /** Vulkan API version the driver exposes, formatted "major.minor.patch". */
        val vulkanVersion: String = "0.0.0",
        /** CPU + RAM summary (e.g. "8 cores · 16 GB RAM"). Empty = hidden. */
        val cpuLine: String = "",
        /** OS + power summary (e.g. "Windows · AC 100%"). Empty = hidden. */
        val osLine: String = ""
    )

    /* ── Selection-pop animation state ──
     * When [animSlot] changes, the newly selected slot's frame + icon briefly
     * scale up past 1.0 and settle back (a subtle "pop"). State lives in the
     * object because build() is called once per frame on the main thread. */
    private const val POP_DURATION = 0.25f    // seconds
    private const val POP_AMPLITUDE = 0.35f   // peak extra scale
    private var animSlot = -1
    private var animStartTime = 0f

    private val DIR_NAMES = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /**
     * A hotbar slot: either a placeable [block], a [food] item that restores
     * that much hunger when used, or a [drink] item (water bottle) that
     * restores that much thirst when used (right-click). Shared with Main.kt
     * so the icons, the highlight, and what you actually do always agree.
     */
    data class HotbarItem(val block: BlockId?, val food: Int = 0, val drink: Int = 0)

    /**
     * The 11 hotbar slots — 8 blocks for building, 2 food and 1 water bottle
     * for the survival bars. This is the default layout; the live hotbar lives
     * in [PlayerInventory] and can have empty slots. Keys: 1-9, 0 and -
     * select them.
     */
    val HOTBAR: List<HotbarItem> = listOf(
        HotbarItem(BlockId.Grass),
        HotbarItem(BlockId.Dirt),
        HotbarItem(BlockId.Stone),
        HotbarItem(BlockId.Wood),
        HotbarItem(BlockId.Water),
        HotbarItem(BlockId.Sand),
        HotbarItem(BlockId.Stone),
        HotbarItem(BlockId.Wood),
        HotbarItem(null, drink = 7),   // water bottle (restores thirst)
        HotbarItem(null, food = 6),    // bread
        HotbarItem(null, food = 8)     // steak
    )

    /** Icon color for a slot — blocks match the ChunkMesher palette. */
    fun iconColor(item: HotbarItem): Triple<Float, Float, Float> = when {
        item.block != null -> ChunkMesher.blockColor(item.block)
        item.drink > 0 -> Triple(0.25f, 0.55f, 0.95f)   // water bottle
        item.food >= 8 -> Triple(0.65f, 0.3f, 0.2f)     // steak
        else -> Triple(0.9f, 0.72f, 0.35f)              // bread
    }

    private fun dirName(yaw: Float): String {
        // forward = (sin yaw, cos yaw): N = +z at yaw 0, E = +x at yaw +90°.
        val idx = ((yaw / (PI / 4)).roundToInt() % 8 + 8) % 8
        return DIR_NAMES[idx]
    }

    /** Wrap an angle difference into (-π, π]. */
    private fun wrapPi(a: Float): Float {
        var d = a
        while (d > PI.toFloat()) d -= 2f * PI.toFloat()
        while (d <= -PI.toFloat()) d += 2f * PI.toFloat()
        return d
    }

    /** Assemble the full HUD for one frame as clip-space vertex data. */
    fun build(state: HudState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        /* ── Crosshair (hidden in spectator) ── */
        val cx = winW / 2f
        val cy = winH / 2f
        if (state.gamemode != GameMode.SPECTATOR) {
            Ui.rect(cx - 2f, cy - 8f, 4f, 16f, 0f, 0f, 0f)   // dark outline for contrast
            Ui.rect(cx - 8f, cy - 2f, 16f, 4f, 0f, 0f, 0f)
            Ui.rect(cx - 1f, cy - 7f, 2f, 14f, 1f, 1f, 1f)
            Ui.rect(cx - 7f, cy - 1f, 14f, 2f, 1f, 1f, 1f)
            Ui.rect(cx - 1f, cy - 1f, 2f, 2f, 1f, 1f, 1f)
        }

        /* ── Compass (top center) ── */
        val cw = 300f
        val ch = 14f
        val ccx = winW / 2f
        val ccy = 16f
        Ui.rect(ccx - cw / 2f, ccy - ch / 2f, cw, ch, 0f, 0f, 0f)
        Ui.rect(ccx - cw / 2f + 1f, ccy - ch / 2f + 1f, cw - 2f, ch - 2f, 0.12f, 0.12f, 0.16f)
        Ui.rect(ccx - 1f, ccy - ch / 2f - 2f, 2f, ch + 4f, 1f, 1f, 1f)  // fixed center tick
        val ppr = 90f // pixels per radian
        for (k in -7..7) {
            val sx = ccx + wrapPi(state.yaw - k * (PI.toFloat() / 4f)) * ppr
            if (sx >= ccx - cw / 2f && sx <= ccx + cw / 2f) {
                val tall = k % 2 == 0
                Ui.rect(sx, ccy - ch / 2f + 1f, 1f, if (tall) 5f else 3f, 0.7f, 0.7f, 0.75f)
            }
        }
        for ((name, ang) in listOf(
            "N" to 0f, "E" to PI.toFloat() / 2f, "S" to PI.toFloat(), "W" to -PI.toFloat() / 2f,
            "N" to 2f * PI.toFloat(), "E" to 2.5f * PI.toFloat(), "S" to 3f * PI.toFloat(), "W" to 1.5f * PI.toFloat()
        )) {
            val sx = ccx + wrapPi(state.yaw - ang) * ppr
            if (sx >= ccx - cw / 2f + 4f && sx <= ccx + cw / 2f - 16f) {
                Ui.text(sx, ccy + ch / 2f + 3f, 1.5f, 1f, 1f, 1f, name)
            }
        }

        /* ── Health + hunger + thirst (top left) ──
         * Health is DOUBLE hearts (max 40 = 20 hearts), so its fill scales
         * against Survival.MAX_HEALTH while hunger/thirst scale against 20.
         * Creative keeps only health; spectator hides all three. */
        val bw = 150f
        val bh = 12f
        val bx = 24f
        val by = 16f
        if (state.gamemode != GameMode.SPECTATOR) {
            Ui.rect(bx - 12f, by, 10f, 10f, 0.9f, 0.25f, 0.2f)  // heart icon
            Ui.rect(bx - 2f, by - 2f, bw + 4f, bh + 4f, 0f, 0f, 0f)
            Ui.rect(bx, by, bw, bh, 0.13f, 0.13f, 0.18f)
            // Minecraft-style damage trail: the ghost only shows while it's still
            // ahead of the bar (i.e. after a change), then drains slowly behind it.
            if (state.healthGhost > state.healthDisplay + 0.01f) {
                Ui.rect(bx, by, bw * state.healthGhost / Survival.MAX_HEALTH, bh, 0.92f, 0.92f, 0.97f)
            }
            Ui.rect(bx, by, bw * state.healthDisplay / Survival.MAX_HEALTH, bh, 0.9f, 0.25f, 0.2f)
            Ui.text(bx + bw + 8f, by, 1.5f, 1f, 1f, 1f, "${state.health}")
        }
        if (state.gamemode == GameMode.SURVIVAL) {
            val hy = by + bh + 12f
            Ui.rect(bx - 12f, hy, 10f, 10f, 0.85f, 0.6f, 0.2f)   // hunger icon
            Ui.rect(bx - 2f, hy - 2f, bw + 4f, bh + 4f, 0f, 0f, 0f)
            Ui.rect(bx, hy, bw, bh, 0.13f, 0.13f, 0.18f)
            // Hunger trail is dark (like Minecraft's hunger ghost)
            if (state.hungerGhost > state.hungerDisplay + 0.01f) {
                Ui.rect(bx, hy, bw * state.hungerGhost / 20f, bh, 0.5f, 0.4f, 0.25f)
            }
            Ui.rect(bx, hy, bw * state.hungerDisplay / 20f, bh, 0.85f, 0.6f, 0.2f)
            Ui.text(bx + bw + 8f, hy, 1.5f, 1f, 1f, 1f, "${state.hunger}")
            val qy = hy + bh + 12f
            Ui.rect(bx - 12f, qy, 10f, 10f, 0.25f, 0.55f, 0.95f) // water-drop icon
            Ui.rect(bx - 2f, qy - 2f, bw + 4f, bh + 4f, 0f, 0f, 0f)
            Ui.rect(bx, qy, bw, bh, 0.13f, 0.13f, 0.18f)
            if (state.thirstGhost > state.thirstDisplay + 0.01f) {
                Ui.rect(bx, qy, bw * state.thirstGhost / 20f, bh, 0.6f, 0.8f, 1f)
            }
            Ui.rect(bx, qy, bw * state.thirstDisplay / 20f, bh, 0.25f, 0.55f, 0.95f)
            Ui.text(bx + bw + 8f, qy, 1.5f, 1f, 1f, 1f, "${state.thirst}")
        }

        /* ── Minimap (bottom right) ── */
        val mmCells = 16
        val mm = mmCells * 6f
        val mmCell = 6f
        val mmStep = 2f       // world blocks per cell
        val mmX = winW - 16f - mm
        val mmY = winH - 16f - mm
        Ui.rect(mmX - 2f, mmY - 2f, mm + 4f, mm + 4f, 0f, 0f, 0f)
        Ui.rect(mmX - 1f, mmY - 1f, mm + 2f, mm + 2f, 0.12f, 0.12f, 0.16f)
        val mmHalf = (mmCells - 1) / 2f   // centers the player on the grid
        for (j in 0 until mmCells) {
            for (i in 0 until mmCells) {
                val wx = (state.camX + (i - mmHalf) * mmStep).toInt()
                // North (+z) is up: larger z -> smaller pixel row.
                val wz = (state.camZ + (mmHalf - j) * mmStep).toInt()
                val h = Terrain.heightAt(state.seed, wx, wz)
                val (cr, cg, cb) = when {
                    h < Terrain.SEA_LEVEL -> Triple(0.15f, 0.4f, 0.9f)
                    h <= Terrain.SEA_LEVEL + 1 -> Triple(0.85f, 0.78f, 0.55f)
                    h <= Terrain.SEA_LEVEL + 5 -> Triple(0.2f, 0.75f, 0.25f)
                    else -> Triple(0.6f, 0.6f, 0.6f)
                }
                Ui.rect(mmX + i * mmCell, mmY + j * mmCell, mmCell, mmCell, cr, cg, cb)
            }
        }
        // Player marker + heading needle (forward = (sin yaw, cos yaw); north = +z = up)
        val pcx = mmX + mm / 2f
        val pcy = mmY + mm / 2f
        Ui.rect(pcx - 2.5f, pcy - 2.5f, 5f, 5f, 1f, 1f, 1f)
        val sinYaw = sin(state.yaw)
        val cosYaw = cos(state.yaw)
        for (t in 1..3) {
            Ui.rect(pcx + sinYaw * t * 4f - 1f, pcy - cosYaw * t * 4f - 1f, 2f, 2f, 1f, 1f, 0.6f)
        }
        Ui.text(mmX + mm / 2f - 4f, mmY - 12f, 1.5f, 1f, 1f, 1f, "N")

        /* ── Info panel (top right) ── */
        var ty = 16f
        fun line(s: String) {
            Ui.textRight(winW - 16f, ty, 2f, 1f, 1f, 1f, s)
            ty += 19f
        }
        line("X ${state.camX.toInt()}  Y ${state.camY.toInt()}  Z ${state.camZ.toInt()}")
        line("SEED ${state.seed}")
        line("FPS ${state.fps}")
        line("CHUNKS ${state.chunks}")
        line("MODE ${state.gamemode.label.uppercase()}")
        line("FACING ${dirName(state.yaw)}")
        line("GPU ${state.gpuName}")
        line("VULKAN ${state.vulkanVersion}")
        if (state.cpuLine.isNotEmpty()) line("CPU ${state.cpuLine}")
        if (state.osLine.isNotEmpty()) line("OS ${state.osLine}")

        /* ── Hotbar (bottom center) — flat 2D bar, 3D cube icons ──
         * The bar itself is the classic flat Minecraft-style slot row. What's
         * 3D is the ITEM: each placeable block is drawn as an isometric cube
         * (bright top face, lit front face, shaded left face) like Minecraft's
         * hotbar item models. Food items stay flat. When the selection changes
         * the chosen slot "pops": its frame and icon briefly scale up and
         * settle back down. Hidden in spectator mode. */
        if (state.gamemode != GameMode.SPECTATOR) {
            if (state.selectedSlot != animSlot) {
                animSlot = state.selectedSlot
                animStartTime = state.time
            }
            val pop = popScale(state.time - animStartTime)

            val slots = HOTBAR.size   // 11 slots: 1-9, 0 and - select them
            val slotW = 42f
            val slotH = 42f
            val gap = 4f
            val total = slots * slotW + (slots - 1) * gap
            val hx = winW / 2f - total / 2f
            val hotbarY = winH - 16f - slotH

            /* ── XP bar (above the hotbar, Minecraft-style) + level badge ──
             * A thin green bar that fills as you gain XP; the bar spans the same
             * width as the hotbar and the current level floats above it. */
            val xpX = hx
            val xpY = hotbarY - 14f
            val xpH = 6f
            Ui.rect(xpX - 1f, xpY - 1f, total + 2f, xpH + 2f, 0f, 0f, 0f)
            Ui.rect(xpX, xpY, total, xpH, 0.13f, 0.13f, 0.18f)
            if (state.xp > 0f) {
                Ui.rect(xpX, xpY, total * state.xp.coerceIn(0f, 1f), xpH, 0.45f, 0.9f, 0.3f)
            }
            Ui.textCentered(winW / 2f, xpY - 12f, 1.4f, 1f, 1f, 1f, "LVL ${state.xpLevel}")
            for (i in 0 until slots) {
                val x = hx + i * (slotW + gap)
                val sel = i == state.selectedSlot
                val scale = if (sel) pop else 1f
                val cxs = x + slotW / 2f
                val cys = hotbarY + slotH / 2f
                Ui.rect(x, hotbarY, slotW, slotH, 0f, 0f, 0f)
                Ui.rect(x + 1f, hotbarY + 1f, slotW - 2f, slotH - 2f, 0.18f, 0.18f, 0.24f)
                val item = state.hotbar.getOrNull(i)
                val iconS = 18f * scale
                if (item != null) {
                    // Block items render as 3D cubes, food as flat squares.
                    itemIcon(cxs, cys, iconS, item)
                }
                if (sel) {
                    // Selected frame — scaled around the slot center for the pop.
                    val hw = slotW / 2f * scale
                    val hh = slotH / 2f * scale
                    Ui.rect(cxs - hw - 2f, cys - hh - 2f, hw * 2f + 4f, 2f, 1f, 1f, 1f)
                    Ui.rect(cxs - hw - 2f, cys + hh, hw * 2f + 4f, 2f, 1f, 1f, 1f)
                    Ui.rect(cxs - hw - 2f, cys - hh - 2f, 2f, hh * 2f + 4f, 1f, 1f, 1f)
                    Ui.rect(cxs + hw, cys - hh - 2f, 2f, hh * 2f + 4f, 1f, 1f, 1f)
                } else {
                    Ui.rect(x, hotbarY, slotW, 1f, 0.5f, 0.5f, 0.55f)
                    Ui.rect(x, hotbarY + slotH - 1f, slotW, 1f, 0.5f, 0.5f, 0.55f)
                    Ui.rect(x, hotbarY, 1f, slotH, 0.5f, 0.5f, 0.55f)
                    Ui.rect(x + slotW - 1f, hotbarY, 1f, slotH, 0.5f, 0.5f, 0.55f)
                }
            }
            // Hotkey labels: 1-9, then 0 and - like Minecraft.
            for (i in 0 until slots) {
                val label = if (i < 9) "${i + 1}" else if (i == 9) "0" else "-"
                Ui.text(hx + i * (slotW + gap) + 2f, hotbarY + slotH + 5f, 1.2f, 0.85f, 0.85f, 0.9f, label)
            }
        }

        return Ui.end()
    }

    /**
     * Draw an item icon centered at (cx, cy): a 3D isometric cube for block
     * items (like Minecraft's hotbar models), a flat square for food. Shared
     * by the in-game hotbar and the inventory screen.
     */
    internal fun itemIcon(cx: Float, cy: Float, size: Float, item: HotbarItem) {
        val (ir, ig, ib) = iconColor(item)
        when {
            item.block != null -> cubeIcon(cx, cy, size, ir, ig, ib)
            item.drink > 0 -> bottleIcon(cx, cy, size, ir, ig, ib)
            else -> Ui.rect(cx - size / 2f, cy - size / 2f, size, size, ir, ig, ib)
        }
    }

    /**
     * Draw a simple water-bottle icon centered at (cx, cy): a wide body, a
     * narrower neck and a small cap, stacked — reads as a drink even at
     * hotbar-icon size. Colored with the item's [r]/[g]/[b].
     */
    private fun bottleIcon(cx: Float, cy: Float, s: Float, r: Float, g: Float, b: Float) {
        val bodyW = s * 0.62f
        val bodyH = s * 0.5f
        val neckW = s * 0.3f
        val neckH = s * 0.38f
        val cap = s * 0.2f
        val capH = s * 0.16f
        val bottom = cy + s / 2f
        val bodyTop = bottom - bodyH
        // Body (lit), neck (slightly shaded), cap (dark) stacked upward.
        Ui.rect(cx - bodyW / 2f, bodyTop, bodyW, bodyH, r, g, b)
        Ui.rect(cx - neckW / 2f, bodyTop - neckH, neckW, neckH, r * 0.85f, g * 0.85f, b * 0.85f)
        Ui.rect(cx - cap / 2f, bodyTop - neckH - capH, cap, capH, r * 0.55f, g * 0.55f, b * 0.55f)
    }

    /**
     * Selection-pop scale for [elapsed] seconds since the pop started.
     * Punchy decaying sine: quickly overshoots to ~1.12 (the decay eats most
     * of [POP_AMPLITUDE] — peak ≈ 1 + AMPLITUDE·0.33), then settles back to
     * exactly 1.0. Exposed internally for tests.
     */
    internal fun popScale(elapsed: Float): Float {
        val t = (elapsed / POP_DURATION).coerceIn(0f, 1f)
        return 1f + POP_AMPLITUDE * exp(-3f * t) * sin(t * PI.toFloat())
    }

    /**
     * Draw a 3D isometric cube icon centered at (cx, cy) — the classic
     * Minecraft hotbar block look: a bright top face, a lit front face and a
     * shaded left face, extruded toward the top-right. [s] is the front-face
     * size in pixels; [r]/[g]/[b] is the base block color.
     */
    private fun cubeIcon(cx: Float, cy: Float, s: Float, r: Float, g: Float, b: Float) {
        val shear = s * 0.4f   // horizontal run of the sheared faces
        val lift = s * 0.3f    // vertical rise of the sheared faces
        // Center the cube's full bounding box on (cx, cy):
        // x-extent [fx, fx+s+shear], y-extent [fy-lift, fy+s].
        val fx = cx - (s + shear) / 2f
        val fy = cy - (s - lift) / 2f
        // Left face (shaded) — shares the front face's left edge.
        Ui.quad4(fx, fy + s, fx + shear, fy + s - lift, fx + shear, fy - lift, fx, fy,
            r * 0.55f, g * 0.55f, b * 0.55f)
        // Front face (lit).
        Ui.quad4(fx, fy + s, fx + s, fy + s, fx + s, fy, fx, fy,
            r * 0.85f, g * 0.85f, b * 0.85f)
        // Top face (brightest) — proper parallelogram off the front's top edge.
        Ui.quad4(fx, fy, fx + s, fy, fx + s + shear, fy - lift, fx + shear, fy - lift,
            r, g, b)
    }

    /** Internal: render a single character (used by tests). */
    internal fun renderChar(ch: Char, w: Int = 1280, h: Int = 720): FloatArray =
        Ui.renderChar(ch, w, h)
}
