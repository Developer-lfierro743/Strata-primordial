package strata.security.policy

import strata.security.SecurityResult
import strata.security.copyright.CopyrightRegistry
import strata.security.copyright.MinecraftSignatureDB
import strata.security.crypto.RKPManager

class ModdingRule : SecurityRule {

    private val authorizedModHashes = setOf("a1b2c3d4...", "e5f6g7h8...")

    private val forgePatterns = listOf(
        Regex("net\\.minecraftforge\\..*"),
        Regex("cpw\\.mods\\..*"),
        Regex("net\\.minecraftforge\\.common\\.ModContainer"),
        Regex("FMLCommonSetupEvent"),
    )

    private val fabricPatterns = listOf(
        Regex("net\\.fabricmc\\..*"),
        Regex("net\\.fabricmc\\.loader\\..*"),
        Regex("net\\.fabricmc\\.api\\.ModInitializer"),
    )

    private val quiltPatterns = listOf(
        Regex("org\\.quiltmc\\..*"),
        Regex("org\\.quiltmc\\.loader\\..*"),
    )

    override fun appliesTo(category: String): Boolean {
        return category == "MOD_LOAD"
    }

    override fun check(context: Map<String, Any>): SecurityResult {
        val fileHash = context["hash"] as? String ?: return SecurityResult.DENY
        val className = context["class_name"] as? String
        val classBytes = context["class_bytes"] as? ByteArray

        if (className != null) {
            for (pattern in forgePatterns + fabricPatterns + quiltPatterns) {
                if (pattern.matches(className)) {
                    println("❌ [MODDING] Blocked Minecraft modloader class: $className")
                    return SecurityResult.DENY
                }
            }
        }

        if (classBytes != null) {
            val source = classBytes.decodeToString()
            val allPatterns = MinecraftSignatureDB.MODLOADER_PATTERNS +
                forgePatterns + fabricPatterns + quiltPatterns
            for (pattern in allPatterns) {
                if (pattern.containsMatchIn(source)) {
                    println("❌ [MODDING] Blocked modloader bytecode pattern in class bytes")
                    return SecurityResult.DENY
                }
            }
        }

        if (MinecraftSignatureDB.KNOWN_MOD_HASH_PREFIXES.any { fileHash.startsWith(it) }) {
            println("❌ [MODDING] Blocked known mod hash: ${fileHash.take(16)}...")
            return SecurityResult.DENY
        }

        if (authorizedModHashes.contains(fileHash)) {
            return SecurityResult.ALLOW
        }

        if (CopyrightRegistry.isBlocked(fileHash)) {
            println("❌ [MODDING] Blocked by copyright takedown: $fileHash")
            return SecurityResult.DENY
        }

        val existing = CopyrightRegistry.getCreatorOf(fileHash)
        if (existing != null) {
            val sig = context["signature"] as? String
            val pubKey = context["public_key"] as? String
            val creatorId = context["creator_id"] as? String
            if (sig == null || pubKey == null || creatorId == null) {
                println("❌ [MODDING] Registered content requires valid signature")
                return SecurityResult.DENY
            }
            val storedKey = CopyrightRegistry.getCreatorPublicKey(creatorId)
            if (storedKey != null && storedKey != pubKey) {
                println("❌ [MODDING] Public key mismatch for $creatorId")
                return SecurityResult.DENY
            }
            val dataToVerify = "mod:$fileHash:$creatorId"
            try {
                if (!RKPManager.verifySignature(pubKey, dataToVerify, sig)) {
                    println("❌ [MODDING] Invalid signature for $creatorId")
                    return SecurityResult.DENY
                }
            } catch (_: Exception) {
                return SecurityResult.DENY
            }
        }

        return SecurityResult.ALLOW
    }
}
