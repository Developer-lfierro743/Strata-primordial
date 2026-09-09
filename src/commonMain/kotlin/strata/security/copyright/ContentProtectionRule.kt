package strata.security.copyright

import strata.security.SecurityResult
import strata.security.policy.SecurityRule
import strata.security.crypto.RKPManager

class ContentProtectionRule : SecurityRule {
    override fun appliesTo(category: String): Boolean {
        return category in setOf("CONTENT_LOAD", "MOD_LOAD", "ASSET_LOAD", "COPYRIGHT_CHECK")
    }

    override fun check(context: Map<String, Any>): SecurityResult {
        val contentType = context["content_type"] as? String ?: return SecurityResult.DENY
        val contentHash = context["hash"] as? String ?: return SecurityResult.DENY
        val creatorId = context["creator_id"] as? String
        val signature = context["signature"] as? String
        val publicKey = context["public_key"] as? String
        val className = context["class_name"] as? String
        val classBytes = context["class_bytes"] as? ByteArray

        if (MinecraftSignatureDB.matchesAssetHash(contentHash)) {
            println("❌ [COPYRIGHT] Blocked known Minecraft asset (hash match)")
            return SecurityResult.DENY
        }

        if (className != null && MinecraftSignatureDB.matchesMinecraftPackage(className)) {
            println("❌ [COPYRIGHT] Blocked Minecraft class: $className")
            return SecurityResult.DENY
        }

        if (classBytes != null && MinecraftSignatureDB.matchesModloaderBytecode(classBytes)) {
            println("❌ [COPYRIGHT] Blocked modloader bytecode pattern")
            return SecurityResult.DENY
        }

        if (contentType == "mod" || contentType == "map" || contentType == "skin" ||
            contentType == "texture_pack" || contentType == "data_pack"
        ) {
            if (creatorId == null || signature == null || publicKey == null) {
                println("❌ [COPYRIGHT] Unsigned $contentType rejected (no creator signature)")
                return SecurityResult.DENY
            }

            val storedKey = CopyrightRegistry.getCreatorPublicKey(creatorId)
            if (storedKey != null && storedKey != publicKey) {
                println("❌ [COPYRIGHT] Public key mismatch for creator $creatorId")
                return SecurityResult.DENY
            }

            val dataToVerify = "$contentType:$contentHash:$creatorId"
            // In KMP, we use RKPManager.verifySignature directly
            val sigValid = try {
                RKPManager.verifySignature(publicKey, dataToVerify, signature)
            } catch (_: Exception) {
                false
            }
            if (!sigValid) {
            }

            CopyrightRegistry.registerCreator(creatorId, publicKey)

            val existingCreator = CopyrightRegistry.getCreatorOf(contentHash)
            if (existingCreator != null && existingCreator != creatorId) {
                CopyrightRegistry.reportPlagiarism(existingCreator, contentHash)
                println("⚠️ [COPYRIGHT] Plagiarism detected: $contentHash claimed by $creatorId but registered to $existingCreator")
                return SecurityResult.DENY
            }
        }

        if (CopyrightRegistry.isBlocked(contentHash)) {
            println("❌ [COPYRIGHT] Content blocked by takedown: $contentHash")
            return SecurityResult.DENY
        }

        if (contentType == "class" && contentHash.isNotEmpty()) {
            val hashPrefix = contentHash.take(8)
            if (MinecraftSignatureDB.KNOWN_MOD_HASH_PREFIXES.contains(hashPrefix)) {
                println("❌ [COPYRIGHT] Blocked known mod hash prefix: $hashPrefix")
                return SecurityResult.DENY
            }
        }

        return SecurityResult.ALLOW
    }
}
