package strata

import strata.security.ChatSecurityBridge

/** One chat entry: player/command feedback text, or a system message. */
data class ChatLine(val text: String, val system: Boolean = false, val flagged: Boolean = false)

/**
 * The chat log + input buffer. Holds a capped list of lines, the message
 * history (Up/Down recall), and the current text being typed. Pure state in
 * commonMain so the chat UI and command flow are unit-testable; the pixel
 * drawing lives in [ChatOverlay] and the OS text input in Main.kt.
 *
 * Messages are routed through [ChatSecurityBridge] before being added to the log.
 */
class ChatLog(val maxLines: Int = 60, val maxHistory: Int = 50) {

    private val _lines = ArrayDeque<ChatLine>()
    private val _history = ArrayDeque<String>()

    /** Snapshot of the visible lines, oldest first. */
    val lines: List<ChatLine> get() = _lines.toList()

    /** Everything sent so far, oldest first (Up arrow recalls backwards). */
    val history: List<String> get() = _history.toList()

    /** The text currently being typed. */
    var input: String = ""

    /** Where Up/Down recall sits in [history]; -1 = not recalling. */
    var historyIndex: Int = -1

    /** Current player's cryptographic identity hash (set by Main.kt). */
    var playerId: String = "anonymous"

    /** Append a line, dropping the oldest once past [maxLines]. */
    fun addLine(text: String, system: Boolean = false, flagged: Boolean = false) {
        if (text.isEmpty()) return
        _lines.addLast(ChatLine(text, system, flagged))
        while (_lines.size > maxLines) _lines.removeFirst()
    }

    /** Empty the chat (the /clear command). */
    fun clear() {
        _lines.clear()
    }

    /**
     * Hand the current input over for sending: routes through security,
     * then adds to log if allowed. Returns the result of the security check.
     */
    fun commitInput(): ChatSecurityBridge.ChatCheckResult {
        val text = input.trim()
        input = ""
        historyIndex = -1
        if (text.isEmpty()) {
            return ChatSecurityBridge.ChatCheckResult.Deny("empty_message")
        }

        // Store in history (skipping consecutive duplicates)
        if (_history.isEmpty() || _history.last() != text) {
            _history.addLast(text)
            while (_history.size > maxHistory) _history.removeFirst()
        }

        // Route through security bridge
        val result = ChatSecurityBridge.checkMessage(playerId, text)

        when (result) {
            is ChatSecurityBridge.ChatCheckResult.Allow -> {
                addLine(text, system = false, flagged = false)
            }
            is ChatSecurityBridge.ChatCheckResult.Flagged -> {
                addLine(text, system = false, flagged = true)
            }
            is ChatSecurityBridge.ChatCheckResult.Deny -> {
                addLine("⚠️ Message blocked: ${result.reason}", system = true)
            }
            is ChatSecurityBridge.ChatCheckResult.Muted -> {
                val remaining = ChatSecurityBridge.getMuteRemainingSeconds(playerId)
                addLine("🔇 You are muted for ${remaining}s", system = true)
            }
            is ChatSecurityBridge.ChatCheckResult.Banned -> {
                addLine("🚫 You are banned: ${result.reason}", system = true)
            }
        }

        return result
    }

    /** Recall an older message (Up arrow). Returns null when there is none. */
    fun recallUp(): String? {
        if (_history.isEmpty()) return null
        if (historyIndex == -1) {
            historyIndex = _history.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        }
        return _history[historyIndex]
    }

    /** Recall a newer message (Down arrow); "" returns to empty input. */
    fun recallDown(): String? {
        if (_history.isEmpty() || historyIndex == -1) return null
        if (historyIndex < _history.size - 1) {
            historyIndex++
            return _history[historyIndex]
        }
        historyIndex = -1
        return ""
    }
}
