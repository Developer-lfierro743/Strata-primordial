package strata.modapi

import platform.posix.printf
import strata.security.copyright.ModScanner
import strata.security.copyright.ModSafety
import strata.security.copyright.ThreatLevel

/**
 * Desktop implementation of the mod manager with ImGui UI.
 */
class ModManager {
    private val modLoader = ModLoader()
    private val modScanner = ModScanner()
    private val loadedMods = mutableListOf<ModDescriptor>()
    
    /** Initialize and scan for mods */
    fun initialize(modsPath: String = "mods") {
        // Scan for mods
        val scanResult = modScanner.scanFolder(modsPath)
        
        // Register valid mods from allowed list
        for (profile in scanResult.profiles) {
            if (!profile.isMinecraftMod && profile.safety != ModSafety.MINECRAFT &&
                profile.safety != ModSafety.NSFW && profile.safety != ModSafety.CSAM &&
                profile.safety != ModSafety.MALICIOUS) {
                try {
                    // Create a basic descriptor from the scanned info
                    val desc = ModDescriptor(
                        id = profile.fileName.replace(".jar", "").lowercase(),
                        name = profile.fileName,
                        version = profile.mcVersion ?: "1.0.0",
                        description = "Mod: ${profile.fileName}",
                        author = "Unknown",
                        enabled = profile.threatLevel == ThreatLevel.NONE
                    )
                    loadedMods.add(desc)
                    // Note: In real implementation, we'd dynamically load the mod JAR
                } catch (e: Exception) {
                    printf("[ModManager] Failed to register mod %s: %s\n", profile.fileName, e.message ?: "unknown")
                }
            }
        }
        
        // Register default example mod
        modLoader.register(HelloWorldMod())
        
        printf("[ModManager] Loaded %d mods\n", loadedMods.size)
    }
    
    /** Get list of loaded mods */
    fun getLoadedMods(): List<ModDescriptor> = loadedMods.toList()
    
    /** Toggle mod enabled state */
    fun toggleMod(modId: String) {
        if (modLoader.isModEnabled(modId)) {
            modLoader.disableMod(modId)
        } else {
            modLoader.enableMod(modId)
        }
    }
}
