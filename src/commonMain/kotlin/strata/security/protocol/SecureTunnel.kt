package strata.security.protocol

import strata.security.crypto.aesGcmEncrypt
import strata.security.crypto.aesGcmDecrypt
import strata.security.crypto.secureRandomBytes
import strata.security.crypto.base64Encode
import strata.security.crypto.base64Decode

/**
 * Secure Tunnel — AES-256-GCM encrypted communication.
 * 
 * Encrypts game traffic to prevent packet sniffing and tampering.
 * Uses AES-GCM for authenticated encryption.
 */
class SecureTunnel(private val sessionKey: ByteArray) {

    private val ivSize = 12

    /**
     * 🔒 Encrypts a message before it's sent over the network.
     * This ensures no one can "sniff" the packet to see your game state.
     */
    fun encrypt(data: String): String {
        val iv = secureRandomBytes(ivSize)
        val encryptedBytes = aesGcmEncrypt(data.encodeToByteArray(), sessionKey, iv)
        return base64Encode(iv + encryptedBytes)
    }

    /**
     * 🔓 Decrypts an incoming message and verifies it.
     * If the data was tampered with, this will fail.
     */
    fun decrypt(encryptedData: String): String {
        val packet = base64Decode(encryptedData)
        require(packet.size > ivSize) { "Invalid encrypted packet" }

        val iv = packet.copyOfRange(0, ivSize)
        val encryptedBytes = packet.copyOfRange(ivSize, packet.size)
        val decryptedBytes = aesGcmDecrypt(encryptedBytes, sessionKey, iv)
        return decryptedBytes.decodeToString()
    }
}
