package strata

import strata.security.ChatSecurityBridge

/**
 * The chat command system, with modern-Minecraft-style syntax: as you type
 * "/" a dropdown of matching commands appears, arguments autocomplete (Tab or
 * Up/Down to pick), and unknown input gives usage feedback.
 *
 * Commands live here as pure, testable data; [Context] lets the registry
 * mutate game state without knowing about the main loop.
 */
object ChatCommands {

    /** What a command is allowed to touch, provided by Main.kt. */
    class Context(
        val seed: Int,
        val setGameMode: (GameMode) -> Unit,
        val playerId: String = "anonymous"
    )

    /** What [execute] wants the caller to do with a submitted command line. */
    sealed class Result {
        /** Show a line in chat ([system] lines are tinted). */
        data class Message(val text: String, val system: Boolean = false) : Result()
        /** Empty the chat log. */
        object ClearChat : Result()
        /** The command name wasn't recognized. */
        object Unknown : Result()
    }

    private data class CommandDef(
        val name: String,
        val args: List<String>,
        val help: String
    )

    /** The command table — add commands here and they show up everywhere. */
    private val COMMANDS: List<CommandDef> = listOf(
        CommandDef(
            "gamemode",
            listOf("survival", "creative", "spectator", "s", "c", "sp"),
            "Set your game mode: /gamemode <survival|creative|spectator>"
        ),
        CommandDef("help", emptyList(), "List all commands"),
        CommandDef("seed", emptyList(), "Show the world seed"),
        CommandDef("clear", emptyList(), "Clear the chat"),
        // ── Security commands ──
        CommandDef(
            "report",
            listOf("<player>", "<reason>"),
            "Report a player: /report <player> <reason>"
        ),
        CommandDef("security", emptyList(), "Show security status and violations"),
        CommandDef("mute", listOf("<player>", "<seconds>"), "Mute a player (moderator): /mute <player> <seconds>"),
        CommandDef("alerts", emptyList(), "Show pending security alerts")
    )

    /**
     * Run a submitted line. Returns null when it is not a command (i.e. it
     * should be broadcast as a chat message instead).
     */
    fun execute(line: String, ctx: Context): Result? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("/")) return null
        val parts = trimmed.substring(1).split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        return when (parts[0].lowercase()) {
            "gamemode" -> {
                val mode = parts.getOrNull(1)?.let(GameMode::from)
                if (mode == null) {
                    Result.Message("Usage: /gamemode <survival|creative|spectator>", system = true)
                } else {
                    ctx.setGameMode(mode)
                    Result.Message("Set game mode to ${mode.label}", system = true)
                }
            }
            "help" -> Result.Message(
                "Commands: " + COMMANDS.joinToString("   ") { "/${it.name}" },
                system = true
            )
            "seed" -> Result.Message("World seed: ${ctx.seed}", system = true)
            "clear" -> Result.ClearChat
            // ── Security commands ──
            "report" -> {
                val target = parts.getOrNull(1)
                val reason = parts.drop(2).joinToString(" ")
                if (target == null || reason.isEmpty()) {
                    Result.Message("Usage: /report <player> <reason>", system = true)
                } else {
                    val reported = ChatSecurityBridge.reportPlayer(ctx.playerId, target, reason)
                    if (reported) {
                        Result.Message("✅ Report submitted for $target", system = true)
                    } else {
                        Result.Message("❌ Cannot report: invalid target or you are banned", system = true)
                    }
                }
            }
            "security" -> {
                val violations = ChatSecurityBridge.getViolationCount(ctx.playerId)
                val muted = ChatSecurityBridge.isMuted(ctx.playerId)
                val muteRemaining = ChatSecurityBridge.getMuteRemainingSeconds(ctx.playerId)
                val reports = ChatSecurityBridge.getPendingReportsCount()
                buildString {
                    appendLine("═══ SECURITY STATUS ═══")
                    appendLine("Player: ${ctx.playerId.take(16)}...")
                    appendLine("Violations: $violations")
                    appendLine("Muted: $muted${if (muted) " (${muteRemaining}s remaining)" else ""}")
                    appendLine("Pending reports: $reports")
                    appendLine("═════════════════════")
                }.let { Result.Message(it, system = true) }
            }
            "mute" -> {
                val target = parts.getOrNull(1)
                val duration = parts.getOrNull(2)?.toLongOrNull()
                if (target == null || duration == null) {
                    Result.Message("Usage: /mute <player> <seconds>", system = true)
                } else {
                    ChatSecurityBridge.mutePlayer(target, duration * 1000)
                    Result.Message("🔇 $target muted for ${duration}s", system = true)
                }
            }
            "alerts" -> {
                val alerts = ChatSecurityBridge.peekAlerts()
                if (alerts.isEmpty()) {
                    Result.Message("No pending alerts", system = true)
                } else {
                    val sb = StringBuilder("═══ SECURITY ALERTS ═══\n")
                    alerts.takeLast(10).forEach { alert ->
                        val icon = when (alert.severity) {
                            ChatSecurityBridge.AlertSeverity.INFO -> "ℹ️"
                            ChatSecurityBridge.AlertSeverity.WARNING -> "⚠️"
                            ChatSecurityBridge.AlertSeverity.CRITICAL -> "🚨"
                            ChatSecurityBridge.AlertSeverity.BAN -> "🚫"
                        }
                        sb.appendLine("$icon ${alert.message}")
                    }
                    sb.append("══════════════════════")
                    Result.Message(sb.toString(), system = true)
                }
            }
            else -> Result.Unknown
        }
    }

    /**
     * Minecraft-style autocomplete for the current [raw] input. Returns full
     * insertion strings ("/gamemode", "creative") — callers render them as
     * suggestions and [applySuggestion] splices one into the input.
     */
    fun complete(raw: String): List<String> {
        val input = raw.lowercase()
        if (!input.startsWith("/")) return emptyList()
        // Collapse runs of spaces so double/multi-space input still tokenizes
        // cleanly (e.g. "/gamemode  c" behaves like "/gamemode c").
        val hasTrailingSpace = input.endsWith(" ")
        val parts = input.trimEnd().split(' ').filter { it.isNotEmpty() }

        /* Still typing the command name itself. */
        if (!hasTrailingSpace && parts.size <= 1) {
            val first = parts.getOrNull(0) ?: return COMMANDS.map { "/${it.name}" }
            if (first == "/") return COMMANDS.map { "/${it.name}" }
            val prefix = first.removePrefix("/")
            return COMMANDS.filter { it.name.startsWith(prefix) }.map { "/${it.name}" }
        }

        /* Past the command name: suggest arguments for the token being typed.
         * A trailing space means the argument is still empty -> all of them. */
        val cmd = COMMANDS.firstOrNull { it.name == parts[0].removePrefix("/") }
        if (cmd == null) {
            // Incomplete command followed by spaces: still offer the command.
            if (parts.size <= 1) {
                val prefix = parts[0].removePrefix("/")
                return COMMANDS.filter { it.name.startsWith(prefix) }.map { "/${it.name}" }
            }
            return emptyList()
        }
        val argToken = if (hasTrailingSpace) "" else parts.last()
        return cmd.args.filter { it.startsWith(argToken) }
    }

    /**
     * Replace the token being typed with [suggestion], preserving anything
     * typed before it (e.g. "/gamemode c" + "creative" -> "/gamemode creative").
     */
    fun applySuggestion(input: String, suggestion: String): String {
        val idx = input.lastIndexOf(' ')
        return if (idx < 0) suggestion else input.substring(0, idx + 1) + suggestion
    }
}
