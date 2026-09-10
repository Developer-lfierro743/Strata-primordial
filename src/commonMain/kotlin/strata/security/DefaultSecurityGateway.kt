package strata.security

import strata.security.policy.AVCManager

/**
 * Default Security Gateway — Implements SecurityGateway interface.
 * 
 * Delegates to SafetyGuardian and AVCManager for policy enforcement.
 */
class DefaultSecurityGateway : SecurityGateway {

    fun checkPolicy(context: SecurityContext): SecurityResult = checkPolicy(context.category, context.toPolicyMap())

    override fun validateInput(inputType: String, data: ByteArray): Boolean {
        val result = SafetyGuardian.verifyAction(inputType, data)
        if (!result) {
            println("⛔ [SECURITY] !!! Input Denied: $inputType !!!")
        }
        return result
    }

    override fun authorizeAssetLoad(assetPath: String, manifestHash: String): Boolean {
        val result = SafetyGuardian.verifyAsset(assetPath, manifestHash)
        if (!result) {
            println("📛 [SECURITY] !!! Unauthorized Asset Blocked: $assetPath !!!")
        }
        return result
    }

    override fun verifyConnection(connectionType: String, identityToken: String): Boolean {
        val result = SafetyGuardian.verifyIdentity(connectionType, identityToken)
        if (!result) {
            println("🔌 [SECURITY] !!! Connection Auth Failed: $connectionType !!!")
        }
        return result
    }

    override fun checkPolicy(ruleCategory: String, context: Map<String, Any>): SecurityResult {
        val result = AVCManager.query(ruleCategory, context)

        if (result != SecurityResult.ALLOW) {
            println("🛑 [SECURITY] !!! Policy Violation !!!")
            println("  Category: $ruleCategory")
            println("  Result:   $result")
            println("  Context:  $context")
        }

        return result
    }
}
