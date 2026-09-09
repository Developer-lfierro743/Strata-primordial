package strata.security.iiv

import strata.security.SecurityResult

object IIVGate {
    private val gatedCategories = setOf(
        "PRIVATE_CHAT_REQUEST",
        "OFF_PLATFORM_CONTACT",
        "ACCOUNT_HELP",
        "AUTHORITY_CLAIM",
        "MINOR_CONTACT",
        "MOD_LOAD",
        "SERVER_JOIN_TRUST"
    )

    fun requiresIIV(category: String, context: Map<String, Any>): Boolean {
        if (category in gatedCategories) return true
        val action = context["action"] as? String
        if (action in gatedCategories) return true
        return context["requires_iiv"] == true
    }

    fun verify(category: String, context: Map<String, Any>): SecurityResult {
        if (!requiresIIV(category, context)) return SecurityResult.ALLOW

        val token = context["iiv_token"] as? IIVToken ?: return SecurityResult.DENY
        if (token.questionnaire.targetAction != category && token.questionnaire.targetAction != context["action"]) {
            return SecurityResult.DENY
        }

        val decision = IIVEngine.verifyToken(token)
        return when {
            decision.approved -> SecurityResult.ALLOW
            decision.score >= 80 -> SecurityResult.DENY
            else -> SecurityResult.FLAG_FOR_REVIEW
        }
    }
}
