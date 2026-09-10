package strata

/**
 * All screens the app can show. Add new entries here (and handle them in
 * [ScreenManager] / the main loop) to grow the game's UI — e.g. PAUSE,
 * OPTIONS, WORLDS, SERVER_LIST.
 */
enum class Screen {
    TITLE,
    GAME,
    /** The 50-slot storage grid, opened with E. The world keeps rendering behind it. */
    INVENTORY,
    /** Native Mods hub — Editor + Mods list. */
    NATIVE_MODS,
    /** Mod code editor (Monaco/ImGui text editor). */
    MOD_EDITOR,
    /** Installed mods list with load/unload toggles. */
    MOD_LIST
}

/**
 * Tiny state machine tracking which screen is active and how screens
 * transition. Kept in common code so it's pure, testable, and screens can be
 * added modularly.
 */
object ScreenManager {
    var current: Screen = Screen.TITLE
        private set

    /** A transition, used so future screens can hook enter/exit logic. */
    data class Transition(val from: Screen, val to: Screen)

    var lastTransition: Transition? = null
        private set

    fun switchTo(screen: Screen) {
        if (screen == current) return
        lastTransition = Transition(current, screen)
        current = screen
    }

    /** Back to the title screen (e.g. ESC from a pause menu later). */
    fun goToTitle() = switchTo(Screen.TITLE)
}
