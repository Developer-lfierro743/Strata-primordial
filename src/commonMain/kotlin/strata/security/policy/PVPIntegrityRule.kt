package strata.security.policy

import strata.security.SecurityResult

class PVPIntegrityRule : SecurityRule {

    override fun appliesTo(category: String): Boolean {
        // This rule activates for input handling and movement
        return category == "INPUT_VELOCITY" || category == "PVP_INTEGRITY"
    }

    override fun check(context: Map<String, Any>): SecurityResult {
        val velocity = context["velocity"] as? Double ?: return SecurityResult.ALLOW
        val maxAllowedVelocity = 20.0 // Catches speed hacks, allows normal walking/jumping

        // Kernel-level physics constraint: If the velocity exceeds your
        // engine's "human-limit," it's likely a speed-hack or fly-hack injection.
        if (velocity > maxAllowedVelocity) {
            return SecurityResult.DENY
        }

        return SecurityResult.ALLOW
    }
}
