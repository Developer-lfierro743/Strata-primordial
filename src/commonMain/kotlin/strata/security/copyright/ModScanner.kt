package strata.security.copyright
import strata.security.SecurityResult

import strata.security.Shredder
import strata.security.file.readFileBytes
import strata.security.file.fileExists
import strata.security.file.listFiles
import strata.security.file.createDirectory
import strata.security.file.moveFile
import strata.security.policy.SecurityRule

/**
 * Mod Scanner — Scans mods for malicious content.
 * 
 * In KMP, we use our own file operations instead of java.io.
 */
data class ModScanResult(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val jarInspection: JarInspectionResult?,
    val threatLevel: ThreatLevel,
    val safety: ModSafety,
    val blocked: Boolean,
    val reasons: List<String>,
    val details: String
)

class ModScanner : SecurityRule {

    override fun appliesTo(category: String): Boolean =
        category == "MOD_SCAN"

    override fun check(context: Map<String, Any>): SecurityResult {
        val filePath = context["file_path"] as? String
        val fileName = context["file_name"] as? String
        val fileBytes = context["file_bytes"] as? ByteArray
        val preScanned = context["scan_result"] as? ModScanResult

        if (preScanned != null) {
            return if (preScanned.blocked) SecurityResult.DENY
            else SecurityResult.ALLOW
        }

        val result = if (filePath != null) {
            scanFile(filePath)
        } else if (fileBytes != null && fileName != null) {
            scanBytes(fileName, fileBytes, null)
        } else {
            return SecurityResult.ALLOW
        }

        return if (result.blocked) SecurityResult.DENY
        else SecurityResult.ALLOW
    }

    fun scanFile(filePath: String): ModScanResult {
        if (!fileExists(filePath) || !filePath.endsWithAny(".jar", ".zip", ".strata_mod")) {
            return ModScanResult(
                fileName = filePath.substringAfterLast("/"),
                filePath = filePath,
                sizeBytes = 0,
                sha256 = "",
                jarInspection = null,
                threatLevel = ThreatLevel.NONE,
                safety = ModSafety.UNVERIFIED,
                blocked = false,
                reasons = emptyList(),
                details = "Not a mod archive"
            )
        }

        val bytes = readFileBytes(filePath)
        return scanBytes(filePath.substringAfterLast("/"), bytes, filePath)
    }

    private fun scanBytes(fileName: String, bytes: ByteArray, filePath: String?): ModScanResult {
        val reasons = mutableListOf<String>()
        
        val jarInspection = JarAnalyzer.inspectBytes(filePath ?: fileName, bytes)
        
        val nsfwMatches = jarInspection.nsfwStrings
        val isMinecraftMod = jarInspection.isMinecraftMod
        
        if (nsfwMatches.isNotEmpty()) {
            reasons.add("NSFW word matches: ${nsfwMatches.take(10).joinToString(", ")}")
        }
        
        if (isMinecraftMod) {
            reasons.add("Minecraft mod detected [${jarInspection.modloaderRefs.firstOrNull() ?: "unknown"}] — ALL Minecraft mods blocked")
        }

        val threatLevel = when {
            isMinecraftMod -> ThreatLevel.MEDIUM
            nsfwMatches.isNotEmpty() -> ThreatLevel.MEDIUM
            else -> ThreatLevel.NONE
        }

        val safety = when {
            isMinecraftMod -> ModSafety.MINECRAFT
            nsfwMatches.isNotEmpty() -> ModSafety.NSFW
            else -> ModSafety.UNVERIFIED
        }

        val blocked = safety in listOf(
            ModSafety.MINECRAFT, ModSafety.NSFW, ModSafety.CSAM,
            ModSafety.MALICIOUS, ModSafety.UTILITY
        ) || threatLevel in listOf(ThreatLevel.MEDIUM, ThreatLevel.HIGH, ThreatLevel.CRITICAL)

        val details = buildString {
            if (isMinecraftMod) appendLine("⛔ MINECRAFT MOD [${jarInspection.modloaderRefs.firstOrNull() ?: "UNKNOWN"}] — BLOCKED")
            if (nsfwMatches.isNotEmpty()) appendLine("🔞 NSFW WORDS (${nsfwMatches.size}): ${nsfwMatches.take(10).joinToString(", ")}")
            if (!blocked) appendLine("✅ No issues detected — mod allowed")
        }.trimEnd()

        return ModScanResult(
            fileName = fileName,
            filePath = filePath ?: "",
            sizeBytes = bytes.size.toLong(),
            sha256 = "",
            jarInspection = jarInspection,
            threatLevel = threatLevel,
            safety = safety,
            blocked = blocked,
            reasons = reasons,
            details = details
        )
    }

    fun scanFolder(path: String = "mods"): FolderScanReport {
        createDirectory("$path/blocked")
        
        if (!fileExists(path)) {
            return FolderScanReport(0, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0)
        }

        val archives = listFiles(path, null)
            .filter { it.endsWithAny(".jar", ".zip", ".strata_mod") && 
                     !it.startsWith("blocked/") }
            .map { "$path/$it" }

        val profiles = mutableListOf<ModProfile>()
        val blocked = mutableListOf<ModProfile>()
        val deleted = mutableListOf<ModProfile>()
        var totalSize = 0L

        for (archive in archives.sortedBy { strata.security.file.getFileSize(it) }) {
            totalSize += strata.security.file.getFileSize(archive)
            val result = scanFile(archive)
            val profile = ModProfile(
                fileName = result.fileName,
                filePath = result.filePath,
                sizeBytes = result.sizeBytes,
                sha256Hash = result.sha256,
                isMinecraftMod = result.safety == ModSafety.MINECRAFT,
                modloaderType = result.jarInspection?.modloaderRefs?.firstOrNull(),
                mcVersion = null,
                detectedCategory = null,
                mcCategory = null,
                safety = result.safety,
                threatLevel = result.threatLevel,
                reason = result.reasons.firstOrNull() ?: result.details.take(120),
                nsfwScore = result.reasons.count { it.contains("NSFW", true) },
                csamScore = result.reasons.count { it.contains("CSAM", true) },
                malwareScore = result.reasons.count { it.contains("malware", true) },
                utilityScore = result.reasons.count { it.contains("cheat", true) },
                minecraftRefCount = if (result.safety == ModSafety.MINECRAFT) 1 else 0,
                modloaderRefCount = if (result.safety == ModSafety.MINECRAFT) 1 else 0,
                nsfwRefCount = result.reasons.count { it.contains("nsfw", true) }
            )
            profiles.add(profile)

            when (result.threatLevel) {
                ThreatLevel.CRITICAL, ThreatLevel.HIGH -> {
                    Shredder.shred(archive)
                    deleted.add(profile)
                }
                ThreatLevel.MEDIUM -> {
                    if (result.blocked) {
                        val dest = "$path/blocked/${result.fileName}"
                        moveFile(archive, dest)
                        blocked.add(profile)
                    }
                }
                else -> {}
            }
        }

        return FolderScanReport(
            scannedCount = archives.size,
            allowedCount = profiles.size - blocked.size - deleted.size,
            blockedCount = blocked.size,
            deletedCount = deleted.size,
            profiles = profiles,
            blockedProfiles = blocked,
            deletedProfiles = deleted,
            totalSizeBytes = totalSize,
        )
    }

    private fun String.endsWithAny(vararg suffixes: String): Boolean {
        return suffixes.any { this.endsWith(it, ignoreCase = true) }
    }
}
