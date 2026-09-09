package strata.modapi

/**
 * Modding API for Strata Primordial.
 * 
 * This module provides the interface for creating and managing mods.
 * Mods can be written in Kotlin and loaded at runtime.
 * 
 * Key concepts:
 * - ModDescriptor: Metadata about a mod
 * - ModLoader: Loads and manages mods
 * - ModHook: Hooks into game systems
 */

/**
 * Represents a loaded mod.
 */
data class ModDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val enabled: Boolean = true,
    val dependencies: List<String> = emptyList()
) {
    override fun toString(): String = "Mod[$id v$version by $author]"
}

/**
 * Mod lifecycle events.
 */
enum class ModEvent {
    /** Called when mod is first loaded */
    ON_LOAD,
    
    /** Called when game starts */
    ON_GAME_START,
    
    /** Called when game ends */
    ON_GAME_END,
    
    /** Called when player joins */
    ON_PLAYER_JOIN,
    
    /** Called when player leaves */
    ON_PLAYER_LEAVE,
    
    /** Called when chat message is received */
    ON_CHAT_MESSAGE,
    
    /** Called when block is broken */
    ON_BLOCK_BREAK,
    
    /** Called when block is placed */
    ON_BLOCK_PLACE,
    
    /** Called on each tick */
    ON_TICK
}

/**
 * Interface for all mods.
 * Implement this to create a mod.
 */
interface IMod {
    /** Get the mod descriptor */
    fun getDescriptor(): ModDescriptor
    
    /** Called when mod is loaded */
    fun onLoad() {}
    
    /** Called when game starts */
    fun onGameStart() {}
    
    /** Called when game ends */
    fun onGameEnd() {}
    
    /** Called when player joins */
    fun onPlayerJoin(playerId: String) {}
    
    /** Called when player leaves */
    fun onPlayerLeave(playerId: String) {}
    
    /** Called when chat message is received */
    fun onChatMessage(sender: String, message: String): String? {
        return message // Return null to block, modified string to modify
    }
    
    /** Called when block is broken */
    fun onBlockBreak(x: Int, y: Int, z: Int, blockId: Int) {}
    
    /** Called when block is placed */
    fun onBlockPlace(x: Int, y: Int, z: Int, blockId: Int) {}
    
    /** Called on each tick */
    fun onTick() {}
}

/**
 * Simple mod loader.
 * In the future, this will load mods from .strata_mod files.
 */
class ModLoader {
    private val mods = mutableMapOf<String, IMod>()
    private val descriptors = mutableMapOf<String, ModDescriptor>()
    
    /** Register a mod instance */
    fun register(mod: IMod) {
        val desc = mod.getDescriptor()
        mods[desc.id] = mod
        descriptors[desc.id] = desc
        mod.onLoad()
        println("[ModLoader] Registered mod: ${desc.name} v${desc.version}")
    }
    
    /** Enable a mod */
    fun enableMod(modId: String) {
        mods[modId]?.let { 
            it.onGameStart()
            descriptors[modId] = descriptors[modId]?.copy(enabled = true) ?: descriptors[modId]!!
        }
    }
    
    /** Disable a mod */
    fun disableMod(modId: String) {
        descriptors[modId]?.let { desc ->
            if (desc.enabled) {
                println("[ModLoader] Disabled mod: ${desc.name}")
                descriptors[modId] = desc.copy(enabled = false)
            }
        }
    }
    
    /** Get all registered mods */
    fun getAllMods(): List<ModDescriptor> = descriptors.values.toList()
    
    /** Check if mod is enabled */
    fun isModEnabled(modId: String): Boolean = descriptors[modId]?.enabled ?: false
    
    /** Process chat message through all mods */
    fun processChatMessage(sender: String, message: String): String? {
        var result = message
        for ((id, mod) in mods) {
            if (descriptors[id]?.enabled == true) {
                result = mod.onChatMessage(sender, result) ?: return null
            }
        }
        return result
    }
}

/**
 * Example mod: Hello World
 */
class HelloWorldMod : IMod {
    override fun getDescriptor(): ModDescriptor = ModDescriptor(
        id = "hello_world",
        name = "Hello World",
        version = "1.0.0",
        description = "A simple hello world mod",
        author = "Strata Team"
    )
    
    override fun onGameStart() {
        println("[HelloWorld] Game started!")
    }
    
    override fun onChatMessage(sender: String, message: String): String? {
        if (message == "/hello") {
            println("[$sender] said hello!")
        }
        return message
    }
}
