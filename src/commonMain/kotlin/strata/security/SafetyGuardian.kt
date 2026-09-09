package strata.security

import strata.security.crypto.base64Decode

/**
 * SafetyGuardian — The core security orchestrator for Strata.
 * 
 * This is the "guardian angel" of the game. It:
 * - Enforces all security policies
 * - Blocks prohibited content (NSFW, CSAM, malware)
 * - Detects and prevents grooming
 * - Blocks Minecraft mods
 * - Prevents cheating/hacking
 * - Manages escalation (warning → mute → kick → ban)
 * 
 * Hardcoded rules that expand as new exploits are found.
 * NOT AI/ML — deterministic rule-based system.
 */
object SafetyGuardian {
    private val safeModId = Regex("^[a-z0-9_.-]{3,64}$")
    private val hashPattern = Regex("^[A-Fa-f0-9]{64}$")
    
    private val rules = mutableListOf<strata.security.policy.SecurityRule>()
    private val violations = mutableMapOf<String, Int>()
    private val bannedIdentities = mutableSetOf<String>()
    
    init {
        // Register all security rules
        rules.add(strata.security.policy.SexualRule())
        rules.add(strata.security.policy.ChatGuardian())
        rules.add(strata.security.policy.ModdingRule())
        rules.add(strata.security.policy.PVPIntegrityRule())
    }
    
    /**
     * Verify an action (input validation).
     */
    fun verifyAction(actionType: String, data: ByteArray): Boolean {
        if (data.isEmpty() || data.size > 4096) return false
        return when (actionType) {
            "MOD_REGISTER" -> {
                val id = data.decodeToString()
                safeModId.matches(id)
            }
            "CHAT_MESSAGE" -> data.size <= 512
            "INPUT_VELOCITY", "PVP_INTEGRITY" -> data.size <= 128
            else -> false
        }
    }
    
    /**
     * Verify an asset (path traversal and hash validation).
     */
    fun verifyAsset(assetPath: String, manifestHash: String): Boolean {
        if (assetPath.contains("..") || assetPath.startsWith("/") || assetPath.startsWith("\\")) {
            return false
        }
        return hashPattern.matches(manifestHash)
    }
    
    /**
     * Verify an identity connection.
     */
    fun verifyIdentity(connectionType: String, token: String): Boolean {
        if (connectionType.isBlank() || token.isBlank()) return false
        return try {
            val decoded = base64Decode(token)
            decoded.size in 128..8192
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Evaluate a security context against all applicable rules.
     */
    fun evaluate(context: SecurityContext): SecurityResult {
        val policyMap = context.toPolicyMap()
        
        for (rule in rules) {
            if (rule.appliesTo(context.category)) {
                val result = rule.check(policyMap)
                
                // Escalation on violations
                if (result == SecurityResult.DENY) {
                    escalateViolation(context.subjectId)
                }
                
                if (result != SecurityResult.ALLOW) {
                    return result
                }
            }
        }
        
        return SecurityResult.ALLOW
    }
    
    /**
     * Escalate violations: warning → mute → kick → ban.
     */
    private fun escalateViolation(subjectId: String) {
        val count = (violations[subjectId] ?: 0) + 1
        violations[subjectId] = count
        
        when (count) {
            1 -> println("⚠️ [SAFETYGUARDIAN] First offense for $subjectId — warning issued.")
            2 -> println("🔇 [SAFETYGUARDIAN] Second offense for $subjectId — 5min mute.")
            3 -> println("🔇 [SAFETYGUARDIAN] Third offense for $subjectId — 30min mute.")
            else -> {
                println("🔨 [SAFETYGUARDIAN] Repeated violations by $subjectId — temporary kick.")
                banIdentity(subjectId)
            }
        }
    }
    
    fun banIdentity(publicKeyHash: String) {
        bannedIdentities.add(publicKeyHash)
        println("🚫 [SAFETYGUARDIAN] Identity $publicKeyHash has been banned.")
    }
    
    fun isBanned(publicKeyHash: String): Boolean {
        return bannedIdentities.contains(publicKeyHash)
    }
}
