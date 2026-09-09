package strata.security.policy

import strata.security.SecurityResult
import strata.security.iiv.IIVEngine
import strata.security.iiv.IIVToken

class IIVRule : SecurityRule {
    override fun appliesTo(category: String): Boolean = category == "IDENTITY_INTENT_VERIFICATION"

    override fun check(context: Map<String, Any>): SecurityResult {
        val token = context["iiv_token"] as? IIVToken ?: return SecurityResult.DENY
        val decision = IIVEngine.verifyToken(token)
        return when {
            decision.approved -> SecurityResult.ALLOW
            decision.score >= 80 -> SecurityResult.DENY
            else -> SecurityResult.FLAG_FOR_REVIEW
        }
    }
}
