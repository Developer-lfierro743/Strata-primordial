package strata.security.copyright

import strata.security.crypto.sha256Hex
import strata.security.file.readFileBytes
import strata.security.file.getFileSize

enum class ModSafety(val label: String, val description: String) {
    TRUSTED("✅ Trusted", "Officially signed by a verified Strata creator — 100% safe"),
    VERIFIED("🟢 Verified", "Scanned clean, no malicious patterns — safe to load"),
    UNVERIFIED("🟡 Unverified", "No signature, no known threats but no guarantees"),
    SUSPICIOUS("🟠 Suspicious", "Matches some heuristic patterns — manual review needed"),
    MALICIOUS("🔴 Malicious", "Contains virus/malware/trojan patterns — BLOCKED"),
    NSFW("🔞 Adult", "Contains sexual/pornographic content — BLOCKED"),
    CSAM("🚨 CSAM", "Contains child sexual abuse material — DELETED + REPORTED"),
    MINECRAFT("⛔ Minecraft", "Minecraft mod (any version/loader) — BLOCKED"),
    UTILITY("⚠️ Utility", "Utility mod (xray, minimap, cheat) — BLOCKED"),
    UNKNOWN("⬜ Unknown", "Could not be analyzed"),
}

enum class StrataModCategory(val label: String, val description: String) {
    CORE("Core", "Engine extensions, hooks, and framework mods"),
    CONTENT("Content", "New blocks, items, entities, biomes"),
    MECHANIC("Mechanic", "New gameplay mechanics and systems"),
    VISUAL("Visual", "Shaders, models, textures, UI enhancements"),
    AUDIO("Audio", "Sound packs, music, voice acting"),
    WORLD("World", "World generation, dimensions, terrain"),
    COMBAT("Combat", "Weapons, armor, PvP/PvE systems"),
    MAGIC("Magic", "Spells, enchantments, magical systems"),
    TECH("Tech", "Machines, automation, energy systems"),
    BUILD("Build", "Building tools, schematics, architecture"),
    SOCIAL("Social", "Chat, guilds, parties, social systems"),
    MINIGAME("Minigame", "In-game minigames and competitions"),
    ECONOMY("Economy", "Shops, currency, trading systems"),
    API("API", "Library/API mods required by other mods"),
    TOOL("Tool", "Developer tools, debug utilities"),
}

enum class MinecraftModCategory(val label: String, val description: String, val versions: String) {
    ARMOR("Armor/Tools/Weapons", "Adds new gear, tools, weapons, armor sets", "ALL"),
    MAGIC("Magic", "Spells, magic systems, enchanted items", "ALL"),
    ADVENTURE("Adventure/RPG", "RPG mechanics, quests, dungeons, classes", "ALL"),
    TECH("Tech", "Machines, automation, pipes, energy (RF/EU)", "ALL"),
    WORLDGEN("World Gen", "Biomes, dimensions, terrain generation", "ALL"),
    MOBS("Mobs/Creatures", "New entities, animals, monsters, NPCs", "ALL"),
    FARMING("Farming/Food", "Crops, animals, cooking, food", "ALL"),
    STORAGE("Storage", "Inventory management, chests, sorting", "ALL"),
    DECO("Decoration", "Furniture, building blocks, aesthetics", "ALL"),
    TRANSPORT("Transport", "Vehicles, rails, elevators, movement", "ALL"),
    MAP("Map/Info", "Minimaps, waypoints, JEI/NEI, info displays", "ALL"),
    UTILITY("Utility/QoL", "Crafting tweaks, convenience, QoL improvements", "ALL"),
    PERFORMANCE("Performance", "Optimization, FPS boosts, memory fixes", "ALL"),
    API("API/Library", "Required libraries (GeckoLib, Architectury, etc.)", "ALL"),
    SERVER("Server Utility", "Server management, permissions, anti-grief", "ALL"),
    COMBAT("Combat/PvP", "PvP arenas, combat overhauls, kits", "ALL"),
    COSMETIC("Cosmetic", "Skins, capes, particle effects, cosmetics", "ALL"),
    REDSTONE("Redstone", "Redstone components, logic gates, automation", "ALL"),
    NSFW("🔞 ADULT", "Pornographic, sexual, NSFW content", "ALL"),
    CHEAT("🚫 CHEAT", "Xray, minimap esp, fly, killaura, grief tools", "ALL"),
}

enum class ThreatLevel(val label: String, val action: String) {
    NONE("None", "Allow"),
    LOW("Low", "Flag for review"),
    MEDIUM("Medium", "Quarantine to unsafe folder"),
    HIGH("High", "Delete immediately"),
    CRITICAL("CRITICAL", "Delete + report to authorities"),
}

data class ModProfile(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256Hash: String,
    val isMinecraftMod: Boolean,
    val modloaderType: String?,
    val mcVersion: String?,
    val detectedCategory: StrataModCategory?,
    val mcCategory: MinecraftModCategory?,
    val safety: ModSafety,
    val threatLevel: ThreatLevel,
    val reason: String,
    val nsfwScore: Int,
    val csamScore: Int,
    val malwareScore: Int,
    val utilityScore: Int,
    val minecraftRefCount: Int,
    val modloaderRefCount: Int,
    val nsfwRefCount: Int,
)

object ModCategorizer {
    private val csamPatterns = listOf(
        Regex("child.*sex|sex.*child", RegexOption.IGNORE_CASE),
        Regex("minor.*nude|nude.*minor", RegexOption.IGNORE_CASE),
        Regex("underage.*sex|sex.*underage", RegexOption.IGNORE_CASE),
        Regex("loli|shota|shotacon|lolicon", RegexOption.IGNORE_CASE),
        Regex("child.*porn|porn.*child", RegexOption.IGNORE_CASE),
        Regex("young.*nude|nude.*young", RegexOption.IGNORE_CASE),
        Regex("teen.*nude|nude.*teen|teen.*porn|porn.*teen", RegexOption.IGNORE_CASE),
        Regex("kid.*nude|nude.*kid|kid.*porn|porn.*kid", RegexOption.IGNORE_CASE),
        Regex("child.*abuse|abuse.*child", RegexOption.IGNORE_CASE),
        Regex("baby.*sex|sex.*baby|baby.*nude|nude.*baby", RegexOption.IGNORE_CASE),
        Regex("grooming", RegexOption.IGNORE_CASE),
        Regex("csam", RegexOption.IGNORE_CASE),
    )

    private val malwarePatterns = listOf(
        Regex("runtime\\.exec|getRuntime\\.exec|ProcessBuilder", RegexOption.IGNORE_CASE),
        Regex("FileOutputStream.*\\.exe", RegexOption.IGNORE_CASE),
        Regex("socket|Socket|getInputStream|getOutputStream", RegexOption.IGNORE_CASE),
        Regex("downloadFile|uploadFile|sendToServer|fetchUrl", RegexOption.IGNORE_CASE),
        Regex("cmd\\.exe|powershell|bash|sh\\s+-c", RegexOption.IGNORE_CASE),
        Regex("deleteFile|wipeDir|formatDrive|c:\\\\.*del", RegexOption.IGNORE_CASE),
        Regex("keylog|keystroke|inputhook", RegexOption.IGNORE_CASE),
        Regex("miner|cryptonight|ethash|stratum\\.", RegexOption.IGNORE_CASE),
        Regex("ClassLoader.*defineClass|Unsafe.*defineClass", RegexOption.IGNORE_CASE),
        Regex("reflection\\.setAccessible|setAccessible\\(true\\)", RegexOption.IGNORE_CASE),
    )

    private val utilityCheatPatterns = listOf(
        Regex("world\\.getBlock|BlockPos|getBlockState", RegexOption.IGNORE_CASE),
        Regex("xray|x.?ray|wallhack|wall.?hack", RegexOption.IGNORE_CASE),
        Regex("killaura|kill.?aura|aimbot|auto.?aim", RegexOption.IGNORE_CASE),
        Regex("mobesp|entityesp|tracers|playeresp", RegexOption.IGNORE_CASE),
        Regex("minimap|mapwriter|voxelmap|journeymap|x.?aero.?minimap", RegexOption.IGNORE_CASE),
        Regex("cave.?map|cave.?finder|ore.?finder|ore.?esp", RegexOption.IGNORE_CASE),
        Regex("autoclick|auto.?click|macro", RegexOption.IGNORE_CASE),
        Regex("cheat|hack|modmenu", RegexOption.IGNORE_CASE),
        Regex("wurst|aristois|impact|inertia|meteor", RegexOption.IGNORE_CASE),
        Regex("nodus|weepcraft|huzuni|toxicity", RegexOption.IGNORE_CASE),
        Regex("freecam|free.?cam|noclip|no.?clip|flyhack", RegexOption.IGNORE_CASE),
        Regex("autofish|auto.?fish|autofarm|auto.?farm", RegexOption.IGNORE_CASE),
        Regex("speedhack|speed.?hack|step.?hack|jesus.?hack", RegexOption.IGNORE_CASE),
    )

    fun categorize(jarResult: JarInspectionResult): ModProfile {
        val modName = jarResult.jarPath

        val nsfwStrings = jarResult.nsfwStrings
        val allRefs = jarResult.minecraftRefs + jarResult.modloaderRefs + nsfwStrings

        val isMinecraft = jarResult.isMinecraftMod
        val mcRefCount = jarResult.minecraftRefs.size
        val mlRefCount = jarResult.modloaderRefs.size
        val nsfwCount = jarResult.nsfwStrings.size

        val csamScore = allRefs.count { s -> csamPatterns.any { it.containsMatchIn(s) } }
        val malwareScore = allRefs.count { s -> malwarePatterns.any { it.containsMatchIn(s) } }
        val utilityScore = allRefs.count { s -> utilityCheatPatterns.any { it.containsMatchIn(s) } }

        val modloaderType = when {
            allRefs.any { it.contains("forge", true) } -> "Forge"
            allRefs.any { it.contains("fabric", true) } -> "Fabric"
            allRefs.any { it.contains("quilt", true) } -> "Quilt"
            allRefs.any { it.contains("neoforge", true) } -> "NeoForge"
            allRefs.any { it.contains("liteloader", true) } -> "LiteLoader"
            allRefs.any { it.contains("rift", true) } -> "Rift"
            else -> null
        }

        val mcVersion = when {
            allRefs.any { Regex("1\\.\\d{1,2}(\\.\\d+)?").containsMatchIn(it) } -> {
                val m = Regex("1\\.(\\d{1,2})(\\.(\\d+))?").find(allRefs.joinToString(" "))
                m?.value
            }
            else -> null
        }

        val detectedCategory: MinecraftModCategory? = when {
            nsfwCount > 0 -> MinecraftModCategory.NSFW
            utilityScore > 0 -> MinecraftModCategory.CHEAT
            isMinecraft && allRefs.any { it.contains("magic", true) || it.contains("spell", true) } -> MinecraftModCategory.MAGIC
            isMinecraft && allRefs.any { it.contains("tech", true) || it.contains("machine", true) || it.contains("energy", true) } -> MinecraftModCategory.TECH
            isMinecraft && allRefs.any { it.contains("dimension", true) || it.contains("biome", true) } -> MinecraftModCategory.WORLDGEN
            isMinecraft && allRefs.any { it.contains("weapon", true) || it.contains("sword", true) || it.contains("bow", true) || it.contains("pvp", true) } -> MinecraftModCategory.COMBAT
            isMinecraft && allRefs.any { it.contains("armor", true) || it.contains("tool", true) } -> MinecraftModCategory.ARMOR
            isMinecraft && allRefs.any { it.contains("minimap", true) || it.contains("mapwriter", true) || it.contains("jei", true) || it.contains("nei", true) } -> MinecraftModCategory.MAP
            isMinecraft && allRefs.any { it.contains("mob", true) || it.contains("entity", true) || it.contains("creature", true) } -> MinecraftModCategory.MOBS
            isMinecraft && allRefs.any { it.contains("food", true) || it.contains("farm", true) || it.contains("crop", true) } -> MinecraftModCategory.FARMING
            isMinecraft && allRefs.any { it.contains("deco", true) || it.contains("furniture", true) || it.contains("build", true) } -> MinecraftModCategory.DECO
            isMinecraft && allRefs.any { it.contains("transport", true) || it.contains("vehicle", true) || it.contains("rail", true) } -> MinecraftModCategory.TRANSPORT
            isMinecraft && allRefs.any { it.contains("chest", true) || it.contains("storage", true) || it.contains("inventory", true) } -> MinecraftModCategory.STORAGE
            isMinecraft && allRefs.any { it.contains("perform", true) || it.contains("optimize", true) || it.contains("fps", true) } -> MinecraftModCategory.PERFORMANCE
            isMinecraft && allRefs.any { it.contains("api", true) || it.contains("library", true) } -> MinecraftModCategory.API
            else -> null
        }

        val threatLevel: ThreatLevel = when {
            csamScore > 0 -> ThreatLevel.CRITICAL
            malwareScore > 0 -> ThreatLevel.HIGH
            nsfwCount > 0 -> ThreatLevel.MEDIUM
            isMinecraft -> ThreatLevel.MEDIUM
            utilityScore > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        val safety: ModSafety = when {
            csamScore > 0 -> ModSafety.CSAM
            malwareScore > 0 -> ModSafety.MALICIOUS
            nsfwCount > 0 -> ModSafety.NSFW
            isMinecraft -> ModSafety.MINECRAFT
            utilityScore > 0 -> ModSafety.UTILITY
            else -> ModSafety.UNVERIFIED
        }

        val reason = buildString {
            var hasIssues = false
            if (csamScore > 0) { append("🚨 CSAM DETECTED! "); hasIssues = true }
            if (malwareScore > 0) { append("🦠 Malware patterns found! "); hasIssues = true }
            if (nsfwCount > 0) { append("🔞 NSFW content ($nsfwCount strings) "); hasIssues = true }
            if (isMinecraft) { append("⛔ Minecraft mod [$modloaderType] "); hasIssues = true }
            if (utilityScore > 0) { append("⚠️ Utility/cheat patterns ($utilityScore matches) "); hasIssues = true }
            if (!hasIssues) append("No issues detected")
        }.trim()

        val hash = try {
            sha256Hex(readFileBytes(jarResult.jarPath))
        } catch (_: Exception) { "" }

        return ModProfile(
            fileName = modName,
            filePath = jarResult.jarPath,
            sizeBytes = try { getFileSize(jarResult.jarPath) } catch (_: Exception) { 0 },
            sha256Hash = hash,
            isMinecraftMod = isMinecraft,
            modloaderType = modloaderType,
            mcVersion = mcVersion,
            detectedCategory = null,
            mcCategory = detectedCategory,
            safety = safety,
            threatLevel = threatLevel,
            reason = reason,
            nsfwScore = nsfwCount,
            csamScore = csamScore,
            malwareScore = malwareScore,
            utilityScore = utilityScore,
            minecraftRefCount = mcRefCount,
            modloaderRefCount = mlRefCount,
            nsfwRefCount = nsfwCount,
        )
    }
}
