package strata

/**
 * The three game modes (formula: "full 3" — survival, creative, spectator).
 *
 * Each mode changes how the player moves and what the HUD shows:
 *  - [SURVIVAL]  — gravity + collision, hunger/thirst/health bars, XP.
 *  - [CREATIVE]  — flight (no gravity), block interaction, health only.
 *  - [SPECTATOR] — no-clip flight through everything, no interaction, no bars.
 */
enum class GameMode(val label: String) {
    SURVIVAL("survival"),
    CREATIVE("creative"),
    SPECTATOR("spectator");

    /** Minecraft-style shorthand used by the /gamemode command. */
    val short: String
        get() = when (this) {
            SURVIVAL -> "s"
            CREATIVE -> "c"
            SPECTATOR -> "sp"
        }

    /** The next mode, cycling survival -> creative -> spectator -> survival. */
    fun next(): GameMode = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Parse a mode from its full label or short alias, case-insensitive. */
        fun from(raw: String): GameMode? {
            val s = raw.trim().lowercase()
            return entries.firstOrNull { it.label == s || it.short == s }
        }
    }
}
