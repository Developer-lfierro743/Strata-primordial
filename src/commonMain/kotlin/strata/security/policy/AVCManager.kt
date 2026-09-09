package strata.security.policy

import strata.security.SecurityResult
import strata.security.ReputationManager
import strata.security.iiv.IIVGate
import strata.security.copyright.ContentProtectionRule
import strata.security.copyright.ModScanner

object AVCManager {
    private val violations = mutableMapOf<String, Int>()

    val modScanner = ModScanner()
    private val rules = mutableListOf<SecurityRule>()

    fun registerRule(rule: SecurityRule) {
        rules.add(rule)
    }

    private val sensitiveCategories = setOf(
        "COMMUNICATION_INTEGRITY",
        "INPUT_VELOCITY",
        "PVP_INTEGRITY",
        "MOD_LOAD",
        "CONTENT",
        "BUILD_INTEGRITY",
        "ASSET_LOAD",
        "CONNECTION_IDENTITY",
        "IDENTITY_INTENT_VERIFICATION",
        "PRIVATE_CHAT_REQUEST",
        "OFF_PLATFORM_CONTACT",
        "ACCOUNT_HELP",
        "AUTHORITY_CLAIM",
        "MINOR_CONTACT",
        "SERVER_JOIN_TRUST",
        "CONTENT_LOAD",
        "COPYRIGHT_CHECK",
        "MOD_SCAN"
    )

    init {
        rules.add(SexualRule())
        rules.add(ModdingRule())
        rules.add(PVPIntegrityRule())
        rules.add(ChatGuardian())
        rules.add(BuildIntegrityRule())
        rules.add(IIVRule())
        rules.add(ContentProtectionRule())
        rules.add(modScanner)
    }

    fun query(ruleCategory: String, context: Map<String, Any>): SecurityResult {
        if (ruleCategory.isBlank()) return SecurityResult.DENY

        val senderId = context["sender_id"] as? String

        // 1. REPUTATION CHECK
        if (senderId != null && strata.security.ReputationManager.isBanned(senderId)) {
            return SecurityResult.DENY
        }

        if (ruleCategory == "COMMUNICATION_INTEGRITY") {
            val socialResult = SocialTrustMonitor.inspect(context)
            if (socialResult != SecurityResult.ALLOW) return socialResult
        }

        if (IIVGate.requiresIIV(ruleCategory, context)) {
            return IIVGate.verify(ruleCategory, context)
        }

        // 2. POLICY ENFORCEMENT
        var matchedRule = false
        for (rule in rules) {
            if (rule.appliesTo(ruleCategory)) {
                matchedRule = true
                val result = rule.check(context)

                // 3. ESCALATION (no perma-ban on first offense)
                if (result == SecurityResult.DENY && senderId != null) {
                    val count = (violations[senderId] ?: 0) + 1
                    violations[senderId] = count
                    when (count) {
                        1 -> println("⚠️ [SECURITY] First offense for $senderId — warning issued.")
                        2 -> println("🔇 [SECURITY] Second offense for $senderId — 5min mute.")
                        3 -> println("🔇 [SECURITY] Third offense for $senderId — 30min mute.")
                        else -> {
                            println("🔨 [SECURITY] Repeated violations by $senderId — temporary kick.")
                            strata.security.ReputationManager.banIdentity(senderId)
                        }
                    }
                }

                if (result != SecurityResult.ALLOW) return result
            }
        }

        if (!matchedRule && sensitiveCategories.contains(ruleCategory)) {
            println("⚠️ [SECURITY] No policy rule registered for sensitive category: $ruleCategory")
            return SecurityResult.DENY
        }

        return SecurityResult.ALLOW
    }
}
