package strata.security

// This file must exist as SecurityGateway.kt
interface SecurityGateway {
    fun validateInput(inputType: String, data: ByteArray): Boolean
    fun authorizeAssetLoad(assetPath: String, manifestHash: String): Boolean
    fun verifyConnection(connectionType: String, identityToken: String): Boolean
    fun checkPolicy(ruleCategory: String, context: Map<String, Any>): SecurityResult
}