package strata

/**
 * The chat overlay, drawn through the shared [Ui] renderer like every other
 * screen. Bottom-left panel like Minecraft: recent lines, then (while typing)
 * the input row with a blinking cursor, and a suggestion dropdown above it
 * that autocompletes commands and their arguments as you type.
 *
 * Rendered as clip-space quads with the built-in pixel font (uppercase —
 * that's the font's only case, so commands display uppercased but still
 * parse case-insensitively).
 */
object ChatOverlay {

    private const val MARGIN = 16f
    private const val LINE_H = 17f
    private const val INPUT_H = 26f
    private const val SUGGESTION_H = 20f
    private const val SCALE = 1.5f
    private const val MAX_LINES_OPEN = 8
    private const val MAX_LINES_CLOSED = 3
    private const val MAX_SUGGESTIONS = 6

    /** Everything the chat needs to draw for one frame. */
    data class ChatState(
        val lines: List<ChatLine>,
        val input: String,
        val suggestions: List<String>,
        val selectedSuggestion: Int,
        val cursorVisible: Boolean,
        val open: Boolean
    )

    /** Assemble the chat overlay as clip-space vertex data. */
    fun build(state: ChatState, w: Int, h: Int): FloatArray {
        Ui.begin(w, h)
        val winW = Ui.winW
        val winH = Ui.winH

        val panelW = minOf(560f, winW - 2 * MARGIN)
        val x = MARGIN
        val panelBottom = winH - MARGIN

        val shownLines = state.lines.takeLast(if (state.open) MAX_LINES_OPEN else MAX_LINES_CLOSED)
        val sugCount = if (state.open) state.suggestions.size.coerceAtMost(MAX_SUGGESTIONS) else 0

        val panelH = shownLines.size * LINE_H +
            sugCount * SUGGESTION_H +
            (if (state.open) INPUT_H + 8f else 4f)
        val panelTop = panelBottom - panelH

        /* Panel backdrop + a thin accent along the top edge. */
        Ui.rect(x, panelTop, panelW, panelH, 0.04f, 0.04f, 0.07f)
        Ui.rect(x, panelTop, panelW, 1f, 0.35f, 0.4f, 0.5f)

        /* Work upward from the bottom: input row, suggestions, then lines. */
        var y = panelBottom - INPUT_H - 4f

        if (state.open) {
            /* ── Input row: green ">" prompt, typed text, blinking cursor ── */
            Ui.rect(x + 2f, y, panelW - 4f, INPUT_H, 0.1f, 0.1f, 0.14f)
            val prefixW = 2 * 6f * SCALE
            Ui.text(x + 8f, y + 6f, SCALE, 0.45f, 0.85f, 0.4f, ">")
            val textX = x + 8f + prefixW
            val maxChars = ((panelW - 24f - prefixW) / (6f * SCALE)).toInt().coerceAtLeast(1)
            val shown = state.input.takeLast(maxChars)
            Ui.text(textX, y + 6f, SCALE, 1f, 1f, 1f, shown)
            if (state.cursorVisible) {
                val caretX = textX + shown.length * 6f * SCALE + 2f
                Ui.rect(caretX, y + 5f, 5f, INPUT_H - 10f, 1f, 1f, 1f)
            }
            y -= INPUT_H + 4f

            /* ── Suggestion dropdown (Minecraft-style, above the input) ── */
            val sug = state.suggestions.take(MAX_SUGGESTIONS)
            if (sug.isNotEmpty()) {
                val sugTop = y - sug.size * SUGGESTION_H
                for (i in sug.indices) {
                    val sy = sugTop + i * SUGGESTION_H
                    val selected = i == state.selectedSuggestion
                    if (selected) {
                        Ui.rect(x + 2f, sy, panelW - 4f, SUGGESTION_H, 1f, 1f, 1f)
                        Ui.text(x + 10f, sy + 5f, SCALE * 0.93f, 0.05f, 0.05f, 0.05f, sug[i])
                    } else {
                        Ui.rect(x + 2f, sy, panelW - 4f, SUGGESTION_H, 0.2f, 0.22f, 0.3f)
                        Ui.text(x + 10f, sy + 5f, SCALE * 0.93f, 0.85f, 0.9f, 1f, sug[i])
                    }
                }
                y = sugTop - 4f
            }
        }

        /* ── Message lines (most recent at the bottom, just above input) ── */
        for (line in shownLines) {
            y -= LINE_H
            val (r, g, b) = when {
                line.system && line.text.contains("🚫") -> Triple(1f, 0.3f, 0.3f) // Ban alert = red
                line.system && line.text.contains("🔇") -> Triple(1f, 0.6f, 0.3f) // Mute alert = orange
                line.system && line.text.contains("⚠️") -> Triple(1f, 0.9f, 0.3f) // Warning = yellow
                line.system -> Triple(0.55f, 0.85f, 1f) // System = blue
                line.flagged -> Triple(1f, 0.8f, 0.5f) // Flagged = amber
                else -> Triple(1f, 1f, 1f) // Normal = white
            }
            val maxChars = ((panelW - 20f) / (6f * SCALE)).toInt().coerceAtLeast(1)
            val prefix = if (line.flagged && !line.system) "⚠ " else ""
            val text = if ((prefix + line.text).length > maxChars) (prefix + line.text).take(maxChars) else prefix + line.text
            Ui.text(x + 8f, y + 3f, SCALE, r, g, b, text)
        }

        return Ui.end()
    }
}
