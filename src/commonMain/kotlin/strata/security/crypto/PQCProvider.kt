package strata.security.crypto

/**
 * PQC Provider — Post-Quantum Cryptography.
 * Uses AES-256-GCM for symmetric encryption.
 * 
 * Note: ML-KEM-768 (Kyber) is not yet available in KMP.
 * We use AES-256-GCM as the primary encryption and plan to add
 * ML-KEM when KMP support is available.
 */
object PQCProvider {
    private const val AES_KEY_BITS = 256
    private const val IV_SIZE = 12

    data class PQKeyMaterial(
        val sessionKey: ByteArray,
        val iv: ByteArray
    )

    /**
     * Generate a random session key for AES-256-GCM.
     */
    fun generateSessionKey(): ByteArray {
        return secureRandomBytes(AES_KEY_BITS / 8)
    }

    /**
     * Generate a random IV for AES-GCM.
     */
    fun generateIV(): ByteArray {
        return secureRandomBytes(IV_SIZE)
    }

    /**
     * Create key material from a shared secret.
     */
    fun createKeyMaterial(sharedSecret: ByteArray): PQKeyMaterial {
        val key = sharedSecret.copyOf(AES_KEY_BITS / 8)
        val iv = generateIV()
        return PQKeyMaterial(key, iv)
    }

    /**
     * AEAD encrypt using AES-256-GCM.
     */
    fun aeadEncrypt(data: ByteArray, sessionKey: ByteArray, iv: ByteArray): ByteArray {
        return aesGcmEncrypt(data, sessionKey, iv)
    }

    /**
     * AEAD decrypt using AES-256-GCM.
     */
    fun aeadDecrypt(encrypted: ByteArray, sessionKey: ByteArray, iv: ByteArray): ByteArray {
        return aesGcmDecrypt(encrypted, sessionKey, iv)
    }

    /**
     * Generate cryptographically secure random bytes.
     */
    fun secureRandomBytes(length: Int): ByteArray {
        return strata.security.crypto.secureRandomBytes(length)
    }
}
