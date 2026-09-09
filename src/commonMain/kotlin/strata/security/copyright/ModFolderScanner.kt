package strata.security.copyright

import strata.security.Shredder
import strata.security.file.*

/**
 * Mod Folder Scanner — Scans all mods in a folder.
 * 
 * In KMP, we use our own file operations instead of strata.security.file.
 */
data class FolderScanReport(
    val scannedCount: Int,
    val allowedCount: Int,
    val blockedCount: Int,
    val deletedCount: Int,
    val profiles: List<ModProfile>,
    val blockedProfiles: List<ModProfile>,
    val deletedProfiles: List<ModProfile>,
    val totalSizeBytes: Long
) {
    val summary: String get() {
        return buildString {
            appendLine("=".repeat(60))
            appendLine("  📊 MOD FOLDER SCAN REPORT")
            appendLine("=".repeat(60))
            appendLine("  Total mods scanned: $scannedCount")
            appendLine("  Total size:        $totalSizeBytes bytes")
            appendLine("  ✅ Allowed:         $allowedCount")
            appendLine("  ❌ Blocked:         $blockedCount")
            appendLine("  🗑️  Shredded:        $deletedCount")
            appendLine()

            if (deletedProfiles.isNotEmpty()) {
                appendLine("  🗑️  SHREDDED MODS (permanently destroyed):")
                deletedProfiles.forEach { p ->
                    appendLine("    [${p.threatLevel.label}] ${p.fileName} — ${p.reason}")
                }
                appendLine()
            }

            if (blockedProfiles.isNotEmpty()) {
                appendLine("  ❌ BLOCKED MODS (moved to blocked/):")
                blockedProfiles.forEach { p ->
                    appendLine("    [${p.safety.label}] ${p.fileName} (${p.mcCategory?.label ?: "N/A"})")
                    appendLine("      ${p.reason}")
                }
                appendLine()
            }

            appendLine("  ✅ ALLOWED MODS:")
            profiles.filter { it !in blockedProfiles && it !in deletedProfiles }.forEach { p ->
                appendLine("    [${p.safety.label}] ${p.fileName}")
            }
            appendLine("=".repeat(60))
        }
    }
}

object ModFolderScanner {
    private const val MODS_FOLDER = "server/mods"
    private const val BLOCKED_FOLDER = "mods/blocked"

    fun scanAll(): FolderScanReport {
        createDirectory(BLOCKED_FOLDER)

        if (!isDirectory(MODS_FOLDER)) {
            return FolderScanReport(0, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0)
        }

        val jarFiles = listFiles(MODS_FOLDER, null)
            .filter { it.lowercase().endsWithAny("jar", "zip", "strata_mod") }
            .map { "$MODS_FOLDER/$it" }

        val profiles = mutableListOf<ModProfile>()
        val blocked = mutableListOf<ModProfile>()
        val deleted = mutableListOf<ModProfile>()
        var totalSize = 0L

        for (jar in jarFiles) {
            totalSize += getFileSize(jar)

            val inspection = JarAnalyzer.inspect(jar)
            val profile = ModCategorizer.categorize(inspection)
            profiles.add(profile)

            when (profile.threatLevel) {
                ThreatLevel.CRITICAL -> {
                    Shredder.shred(jar)
                    deleted.add(profile)
                    println("🚨 [CSAM] ${profile.fileName} — SHREDDED permanently")
                }
                ThreatLevel.HIGH -> {
                    Shredder.shred(jar)
                    deleted.add(profile)
                    println("🦠 [MALWARE] ${profile.fileName} — SHREDDED permanently")
                }
                ThreatLevel.MEDIUM -> {
                    if (profile.safety == ModSafety.NSFW || profile.isMinecraftMod) {
                        val dest = "$BLOCKED_FOLDER/${profile.fileName}"
                        moveFile(jar, dest)
                        blocked.add(profile)
                        println("⛔ [BLOCKED] ${profile.fileName} — ${profile.reason}")
                    }
                }
                ThreatLevel.LOW -> {
                    if (profile.safety == ModSafety.UTILITY) {
                        val dest = "$BLOCKED_FOLDER/${profile.fileName}"
                        moveFile(jar, dest)
                        blocked.add(profile)
                        println("⚠️ [BLOCKED] ${profile.fileName} — Utility/Cheat mod")
                    }
                }
                ThreatLevel.NONE -> {
                    println("✅ [ALLOWED] ${profile.fileName}")
                }
            }
        }

        return FolderScanReport(
            scannedCount = jarFiles.size,
            allowedCount = profiles.size - blocked.size - deleted.size,
            blockedCount = blocked.size,
            deletedCount = deleted.size,
            profiles = profiles,
            blockedProfiles = blocked,
            deletedProfiles = deleted,
            totalSizeBytes = totalSize,
        )
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean {
    return suffixes.any { this.endsWith(it, ignoreCase = true) }
}
