package strata.security.crypto

/**
 * RKP Manager — Remote Key Provisioning.
 * Uses Ed25519 signatures for identity verification.
 * 
 * In KMP, we use our own CryptoProvider expect/actual instead of java.security.
 */
object RKPManager {
    private var keyPair: Ed25519KeyPair? = null
    
    init {
        keyPair = generateEd25519KeyPair()
    }
    
    fun getPublicKey(): String {
        return base64Encode(keyPair?.publicKey ?: byteArrayOf())
    }
    
    fun signAction(data: String): String {
        val pair = keyPair ?: throw IllegalStateException("Key pair not initialized")
        val signature = ed25519Sign(data.encodeToByteArray(), pair.privateKey)
        return base64Encode(signature)
    }
    
    fun verifySignature(publicKeyBase64: String, data: String, signatureBase64: String): Boolean {
        return try {
            val publicKey = base64Decode(publicKeyBase64)
            val signature = base64Decode(signatureBase64)
            ed25519Verify(data.encodeToByteArray(), signature, publicKey)
        } catch (_: Exception) {
            false
        }
    }
}
