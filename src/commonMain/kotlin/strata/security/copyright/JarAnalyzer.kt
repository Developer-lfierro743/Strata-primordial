package strata.security.copyright

import strata.security.file.readFileBytes
import strata.security.file.fileExists

/**
 * JAR Analyzer — Inspects JAR files for Minecraft mod patterns.
 * 
 * In KMP, we use our own ZIP parsing instead of java.util.jar.JarFile.
 */
data class JarInspectionResult(
    val jarPath: String,
    val isMinecraftMod: Boolean,
    val minecraftRefs: List<String>,
    val modloaderRefs: List<String>,
    val nsfwStrings: List<String>,
    val totalEntries: Int
)

object JarAnalyzer {
    
    private val minecraftPatterns = listOf(
        "net/minecraft/",
        "net.minecraft.",
        "com/mojang/",
        "com.mojang.",
        "net/minecraftforge/",
        "net.minecraftforge.",
        "cpw/mods/",
        "cpw.mods.",
        "net/fabricmc/",
        "net.fabricmc.",
        "org/quiltmc/",
        "org.quiltmc.",
        "fabric.mod.json",
        "quilt.mod.json",
        "mcmod.info"
    )
    
    private val modloaderPatterns = listOf(
        "forge",
        "fabric-loader",
        "quilt-loader",
        "neoforge",
        "MinecraftForge",
        "FMLCommonSetupEvent",
        "FMLInitializationEvent",
        "ModInitializer"
    )
    
    fun inspect(jarPath: String): JarInspectionResult {
        if (!fileExists(jarPath)) {
            return JarInspectionResult(
                jarPath = jarPath,
                isMinecraftMod = false,
                minecraftRefs = emptyList(),
                modloaderRefs = emptyList(),
                nsfwStrings = emptyList(),
                totalEntries = 0
            )
        }
        
        val bytes = readFileBytes(jarPath)
        return inspectBytes(jarPath, bytes)
    }
    
    fun inspectBytes(jarPath: String, bytes: ByteArray): JarInspectionResult {
        val minecraftRefs = mutableListOf<String>()
        val modloaderRefs = mutableListOf<String>()
        val nsfwStrings = mutableListOf<String>()
        var isMinecraftMod = false
        var totalEntries = 0
        
        // Simple ZIP parsing - look for pattern matches in the byte content
        val content = bytes.decodeToString()
        
        // Check for Minecraft references
        for (pattern in minecraftPatterns) {
            if (content.contains(pattern)) {
                minecraftRefs.add(pattern)
                isMinecraftMod = true
            }
        }
        
        // Check for modloader references
        for (pattern in modloaderPatterns) {
            if (content.contains(pattern, ignoreCase = true)) {
                modloaderRefs.add(pattern)
                isMinecraftMod = true
            }
        }
        
        // Check for NSFW patterns
        val nsfwPatterns = listOf(
            "nude", "porn", "hentai", "xxx", "nsfw",
            "sexual", "erotic", "fetish", "lewd"
        )
        for (pattern in nsfwPatterns) {
            if (content.contains(pattern, ignoreCase = true)) {
                nsfwStrings.add(pattern)
            }
        }
        
        return JarInspectionResult(
            jarPath = jarPath,
            isMinecraftMod = isMinecraftMod,
            minecraftRefs = minecraftRefs,
            modloaderRefs = modloaderRefs,
            nsfwStrings = nsfwStrings,
            totalEntries = totalEntries
        )
    }
}
