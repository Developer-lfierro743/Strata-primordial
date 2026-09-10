package strata

import kotlin.math.sin
import kotlin.math.PI

/**
 * Actions the mod editor screen can trigger.
 */
enum class EditorAction {
    SAVE,       // Save current file
    BACK,       // Return to native mods hub
    RUN,        // Run/test the mod
    SETTINGS,   // Open editor settings
    FORMAT      // Format the code
}

/**
 * Mod Code Editor screen: Monaco Editor integration for writing mod scripts.
 * 
 * The editor uses a webview to display Monaco Editor with full IDE features:
 * - Syntax highlighting for JavaScript/TypeScript
 * - IntelliSense and code completion
 * - Error checking and linting
 * - Multiple file tabs
 * - File explorer sidebar
 * - Terminal/output panel
 * 
 * Draws through the webview for the editor UI, with ImGui for the overlay.
 */
object ModEditorScreen {

    /** Everything the editor screen needs for one frame. */
    data class EditorState(
        val time: Float,
        val mouseX: Float = -1f,
        val mouseY: Float = -1f,
        /** Current file being edited */
        val currentFile: String = "mod.js",
        /** File content (can be read/written via webview) */
        val files: Map<String, String> = emptyMap(),
        /** Whether the webview is initialized */
        val webviewReady: Boolean = false,
        /** Editor theme */
        val theme: String = "dark",
        /** Whether minimap is enabled */
        val minimapEnabled: Boolean = true,
        /** Whether word wrap is enabled */
        val wordWrapEnabled: Boolean = false
    )

    /** Result of user interaction. */
    data class EditorResult(
        val action: EditorAction,
        val file: String? = null,
        val content: String? = null
    )

    /* ── Button layout ── */
    private const val HEADER_H = 48f
    private const val TOOLBAR_H = 32f
    private const val STATUSBAR_H = 24f
    private const val SIDEBAR_W = 260f
    
    private const val BTN_W = 100f
    private const val BTN_H = 28f
    private const val BTN_GAP = 8f
    
    private const val BACK_W = 120f
    private const val BACK_H = 32f

    /**
     * Hit-test: which button was clicked?
     * Returns an [EditorResult] or null.
     */
    fun hitTest(mx: Float, my: Float, w: Int, h: Int): EditorResult? {
        val winW = w.toFloat()
        val winH = h.toFloat()
        
        // Header buttons (right side)
        val headerBtnStartX = winW - 200f
        val headerY = 10f
        
        // Save button
        val saveX = headerBtnStartX
        if (mx in saveX..(saveX + BTN_W) && my in headerY..(headerY + BTN_H)) {
            return EditorResult(EditorAction.SAVE)
        }
        
        // Format button
        val formatX = headerBtnStartX + BTN_W + BTN_GAP
        if (mx in formatX..(formatX + BTN_W) && my in headerY..(headerY + BTN_H)) {
            return EditorResult(EditorAction.FORMAT)
        }
        
        // Run button
        val runX = headerBtnStartX + (BTN_W + BTN_GAP) * 2
        if (mx in runX..(runX + BTN_W) && my in headerY..(headerY + BTN_H)) {
            return EditorResult(EditorAction.RUN)
        }
        
        // Back button (bottom left)
        val backX = 20f
        val backY = winH - STATUSBAR_H - BACK_H - 10f
        if (mx in backX..(backX + BACK_W) && my in backY..(backY + BACK_H)) {
            return EditorResult(EditorAction.BACK)
        }
        
        return null
    }

    /**
     * Assemble the editor screen overlay for one frame.
     * 
     * The main editor UI is rendered by the webview (Monaco Editor).
     * This function renders the ImGui overlay with:
     * - Header bar with buttons
     * - Status bar
     * - Optional tooltips
     */
    fun build(state: EditorState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        /* ── Semi-transparent header bar ── */
        Ui.rect(0f, 0f, winW, HEADER_H, 0.14f, 0.14f, 0.17f, 0.95f)
        Ui.rect(0f, HEADER_H - 1f, winW, 1f, 0.24f, 0.24f, 0.27f)

        /* ── Title ── */
        Ui.text(16f, 12f, 1.8f, 0.6f, 0.8f, 1f, "MOD EDITOR")
        Ui.text(120f, 14f, 1.2f, 0.5f, 0.5f, 0.55f, "— ${state.currentFile}")

        /* ── Header buttons ── */
        val headerBtnStartX = winW - 200f
        val headerY = 10f

        // Format button
        val formatX = headerBtnStartX + BTN_W + BTN_GAP
        val formatHovered = state.mouseX in formatX..(formatX + BTN_W) &&
                state.mouseY in headerY..(headerY + BTN_H)
        drawButton(formatX, headerY, BTN_W, BTN_H, "FORMAT", formatHovered,
            0.15f, 0.15f, 0.18f, 0.4f, 0.5f, 0.6f)

        // Save button
        val saveX = headerBtnStartX
        val saveHovered = state.mouseX in saveX..(saveX + BTN_W) &&
                state.mouseY in headerY..(headerY + BTN_H)
        drawButton(saveX, headerY, BTN_W, BTN_H, "SAVE", saveHovered,
            0.1f, 0.3f, 0.15f, 0.3f, 0.8f, 0.4f)

        // Run button
        val runX = headerBtnStartX + (BTN_W + BTN_GAP) * 2
        val runHovered = state.mouseX in runX..(runX + BTN_W) &&
                state.mouseY in headerY..(headerY + BTN_H)
        drawButton(runX, headerY, BTN_W, BTN_H, "▶ RUN", runHovered,
            0.1f, 0.25f, 0.4f, 0.3f, 0.7f, 1f)

        /* ── Status bar ── */
        val statusY = winH - STATUSBAR_H
        Ui.rect(0f, statusY, winW, STATUSBAR_H, 0.0f, 0.47f, 0.8f)
        
        // Left status
        Ui.text(16f, statusY + 5f, 1.1f, 1f, 1f, 1f, "✓ Ready")
        
        // Right status
        val themeText = if (state.theme == "dark") "Dark Theme" else "Light Theme"
        Ui.textRight(winW - 16f, statusY + 5f, 1.1f, 1f, 1f, 1f, 
            "$themeText | JavaScript | UTF-8")

        /* ── Back button (overlay) ── */
        val backX = 20f
        val backY = winH - STATUSBAR_H - BACK_H - 10f
        val backHovered = state.mouseX in backX..(backX + BACK_W) &&
                state.mouseY in backY..(backY + BACK_H)
        
        if (backHovered) {
            Ui.rect(backX - 2f, backY - 2f, BACK_W + 4f, BACK_H + 4f, 0.5f, 0.5f, 0.6f, 0.8f)
            Ui.rect(backX, backY, BACK_W, BACK_H, 0.15f, 0.15f, 0.2f, 0.9f)
        } else {
            Ui.rect(backX - 1f, backY - 1f, BACK_W + 2f, BACK_H + 2f, 0.3f, 0.3f, 0.35f, 0.7f)
            Ui.rect(backX, backY, BACK_W, BACK_H, 0.08f, 0.08f, 0.12f, 0.9f)
        }
        
        val backLabelColor = if (backHovered) Triple(1f, 1f, 1f) else Triple(0.7f, 0.75f, 0.85f)
        Ui.textCentered(
            backX + BACK_W / 2f,
            backY + BACK_H / 2f - 3.5f * 1.6f,
            1.6f,
            backLabelColor.first, backLabelColor.second, backLabelColor.third,
            "< BACK"
        )

        /* ── Webview status indicator ── */
        if (!state.webviewReady) {
            val indicatorX = winW - 30f
            val indicatorY = 16f
            // Pulsing dot
            val pulse = (state.time * 3f) % 1f
            val alpha = 0.5f + 0.5f * sin(pulse * 2f * PI.toFloat())
            Ui.rect(indicatorX, indicatorY, 8f, 8f, 0.8f, 0.6f, 0.2f, alpha)
        }

        return Ui.end()
    }

    /**
     * Draw a button with hover effect.
     */
    private fun drawButton(
        x: Float, y: Float, w: Float, h: Float,
        label: String, hovered: Boolean,
        bgR: Float, bgG: Float, bgB: Float,
        textR: Float, textG: Float, textB: Float
    ) {
        if (hovered) {
            Ui.rect(x - 1f, y - 1f, w + 2f, h + 2f, textR, textG, textB, 0.6f)
        }
        
        Ui.rect(x, y, w, h, bgR, bgG, bgB, 0.9f)
        
        val labelColor = if (hovered) Triple(1f, 1f, 1f) else Triple(textR, textG, textB)
        Ui.textCentered(
            x + w / 2f,
            y + h / 2f - 3.5f * 1.2f,
            1.2f,
            labelColor.first, labelColor.second, labelColor.third,
            label
        )
    }

    /**
     * Get the default mod.js template content.
     */
    fun getDefaultModJs(): String {
        return """
/**
 * Strata Mod Template
 * 
 * Edit this file to create your mod functionality.
 */

// Mod metadata
const MOD_NAME = "My Mod";
const MOD_VERSION = "1.0.0";

/**
 * Called when the mod is loaded.
 */
function onModLoad() {
    console.log(`[${MOD_NAME}] Loaded!`);
    
    // Register event handlers
    Events.on('playerJoin', (event) => {
        console.log(`Player joined: ${'$'}{event.player.name}`);
    });
}

/**
 * Called when the mod is unloaded.
 */
function onModUnload() {
    console.log(`[${MOD_NAME}] Unloaded.`);
}

module.exports = { onModLoad, onModUnload };
""".trimIndent()
    }

    /**
     * Get the default mod.json template content.
     */
    fun getDefaultModJson(): String {
        return """
{
    "name": "my_mod",
    "displayName": "My Mod",
    "version": "1.0.0",
    "author": "Your Name",
    "description": "A mod for Strata: Primordial",
    "strataVersion": ">=0.2.0",
    "main": "mod.js",
    "permissions": ["events", "player"],
    "settings": {
        "debug": false
    }
}
""".trimIndent()
    }
}
