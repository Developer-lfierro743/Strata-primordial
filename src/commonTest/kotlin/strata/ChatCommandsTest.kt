package strata

import strata.security.ChatSecurityBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Command execution + Minecraft-style autocomplete, and the ChatLog. */
class ChatCommandsTest {

    private fun context(onMode: (GameMode) -> Unit = {}): ChatCommands.Context =
        ChatCommands.Context(seed = 12345, setGameMode = onMode)

    /* ── Execution ── */

    @Test
    fun plainMessageIsNotACommand() {
        assertNull(ChatCommands.execute("hello world", context()))
        assertNull(ChatCommands.execute("", context()))
        assertNull(ChatCommands.execute("/", context()))
    }

    @Test
    fun gamemodeCreativeSwitchesMode() {
        var mode: GameMode? = null
        val result = ChatCommands.execute("/gamemode creative", context { mode = it })
        assertEquals(GameMode.CREATIVE, mode)
        assertIs<ChatCommands.Result.Message>(result)
        assertTrue(result.text.contains("creative"), "feedback should name the mode: ${result.text}")
    }

    @Test
    fun gamemodeAcceptsAliases() {
        for ((alias, expected) in listOf(
            "s" to GameMode.SURVIVAL,
            "c" to GameMode.CREATIVE,
            "sp" to GameMode.SPECTATOR,
            "SURVIVAL" to GameMode.SURVIVAL
        )) {
            var mode: GameMode? = null
            ChatCommands.execute("/gamemode $alias", context { mode = it })
            assertEquals(expected, mode, "alias '$alias' should map to $expected")
        }
    }

    @Test
    fun gamemodeBadArgumentShowsUsage() {
        var switched = false
        val result = ChatCommands.execute("/gamemode hardcore", context { switched = true })
        assertFalse(switched, "unknown mode must not switch gamemode")
        assertIs<ChatCommands.Result.Message>(result)
        assertTrue(result.text.contains("Usage"), "should show usage: ${result.text}")
    }

    @Test
    fun helpListsCommands() {
        val result = ChatCommands.execute("/help", context())
        assertIs<ChatCommands.Result.Message>(result)
        assertTrue(result.text.contains("/gamemode"), "help should list /gamemode: ${result.text}")
    }

    @Test
    fun seedReportsWorldSeed() {
        val result = ChatCommands.execute("/seed", ChatCommands.Context(seed = 777, setGameMode = {}))
        assertIs<ChatCommands.Result.Message>(result)
        assertTrue(result.text.contains("777"), "seed should echo the world seed: ${result.text}")
    }

    @Test
    fun clearReturnsClearChat() {
        val result = ChatCommands.execute("/clear", context())
        assertIs<ChatCommands.Result.ClearChat>(result)
    }

    @Test
    fun unknownCommandIsFlagged() {
        assertIs<ChatCommands.Result.Unknown>(ChatCommands.execute("/fly", context()))
    }

    /* ── Autocomplete ── */

    @Test
    fun slashListsAllCommands() {
        val suggestions = ChatCommands.complete("/")
        assertTrue("/gamemode" in suggestions)
        assertTrue("/help" in suggestions)
        assertTrue("/seed" in suggestions)
        assertTrue("/clear" in suggestions)
    }

    @Test
    fun commandPrefixMatches() {
        assertEquals(listOf("/gamemode"), ChatCommands.complete("/gamem"))
        assertEquals(listOf("/gamemode"), ChatCommands.complete("/gamemode"))
        assertTrue(ChatCommands.complete("/s").contains("/seed"))
    }

    @Test
    fun gamemodeArgsAutocomplete() {
        val all = ChatCommands.complete("/gamemode ")
        assertTrue("survival" in all)
        assertTrue("creative" in all)
        assertTrue("spectator" in all)
        assertEquals(listOf("creative", "c"), ChatCommands.complete("/gamemode c"))
        assertEquals(listOf("spectator", "sp"), ChatCommands.complete("/gamemode sp"))
    }

    @Test
    fun autocompleteToleratesRaggedSpacing() {
        // Double/triple spaces must not break suggestions; a trailing space
        // means a fresh token, so all arguments are offered again.
        assertEquals(listOf("creative", "c"), ChatCommands.complete("/gamemode  c"))
        assertEquals(listOf("creative", "c"), ChatCommands.complete("/gamemode    c"))
        val afterSpace = ChatCommands.complete("/gamemode   c   ")
        assertTrue("creative" in afterSpace && "spectator" in afterSpace)
        // Incomplete command name followed by spaces still completes the command.
        assertEquals(listOf("/gamemode"), ChatCommands.complete("/g  "))
        // A completed command + space moves on to its argument suggestions.
        val args = ChatCommands.complete("/gamemode ")
        assertTrue("survival" in args && "creative" in args && "spectator" in args)
    }

    @Test
    fun nonCommandsSuggestNothing() {
        assertEquals(emptyList(), ChatCommands.complete(""))
        assertEquals(emptyList(), ChatCommands.complete("hello"))
        assertEquals(emptyList(), ChatCommands.complete("/notacommand "))
    }

    @Test
    fun applySuggestionSplicesIntoInput() {
        assertEquals("/gamemode", ChatCommands.applySuggestion("/g", "/gamemode"))
        assertEquals("/gamemode creative", ChatCommands.applySuggestion("/gamemode c", "creative"))
        assertEquals("/gamemode spectator", ChatCommands.applySuggestion("/gamemode s", "spectator"))
    }

    /* ── ChatLog ── */

    @Test
    fun logCapsLinesAndClears() {
        val log = ChatLog(maxLines = 3)
        log.addLine("one")
        log.addLine("two")
        log.addLine("three")
        log.addLine("four")
        assertEquals(listOf("two", "three", "four"), log.lines.map { it.text })
        log.clear()
        assertTrue(log.lines.isEmpty())
    }

    @Test
    fun commitInputPushesHistoryAndResets() {
        val log = ChatLog()
        log.input = "  /gamemode creative  "
        val result = log.commitInput()
        assertIs<ChatSecurityBridge.ChatCheckResult>(result)
        assertEquals("", log.input)
        log.input = "/gamemode creative"   // consecutive duplicate is skipped
        log.commitInput()
        assertEquals(listOf("/gamemode creative"), log.history)
    }

    @Test
    fun recallWalksHistoryBothWays() {
        val log = ChatLog()
        log.input = "one"; log.commitInput()
        log.input = "two"; log.commitInput()
        log.input = "three"; log.commitInput()

        assertEquals("three", log.recallUp())
        assertEquals("two", log.recallUp())
        assertEquals("one", log.recallUp())      // oldest — stays put
        assertEquals("one", log.recallUp())
        assertEquals("two", log.recallDown())    // back toward newest
        assertEquals("three", log.recallDown())
        assertEquals("", log.recallDown())       // newest + 1 -> empty input
        assertNull(log.recallDown())
    }

    @Test
    fun recallOnEmptyHistoryReturnsNull() {
        val log = ChatLog()
        assertNull(log.recallUp())
        assertNull(log.recallDown())
    }
}
