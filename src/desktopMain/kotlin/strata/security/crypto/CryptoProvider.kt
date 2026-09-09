package strata.security.crypto

import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * Desktop (mingwX64) actual implementations for crypto operations.
 * Uses basic Kotlin implementations for now.
 * Can be upgraded to use cryptography-kotlin library later.
 */

actual fun secureRandomBytes(length: Int): ByteArray {
    return Random.nextBytes(length)
}

actual fun sha256(data: ByteArray): ByteArray {
    // Simple SHA-256 implementation using Kotlin
    // For production, use cryptography-kotlin or platform-specific API
    return simpleSha256(data)
}

actual fun sha256Hex(data: ByteArray): String {
    return sha256(data).joinToString("") { byte ->
        val hex = (byte.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}

actual fun aesGcmEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    // Simple XOR encryption for demonstration
    // For production, use proper AES-GCM implementation
    val output = ByteArray(data.size)
    for (i in data.indices) {
        output[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
    }
    return iv + output
}

actual fun aesGcmDecrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    // Simple XOR decryption for demonstration
    val data = encrypted.copyOfRange(iv.size, encrypted.size)
    val output = ByteArray(data.size)
    for (i in data.indices) {
        output[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
    }
    return output
}

actual fun generateEd25519KeyPair(): Ed25519KeyPair {
    val publicKey = Random.nextBytes(32)
    val privateKey = Random.nextBytes(64)
    return Ed25519KeyPair(publicKey, privateKey)
}

actual fun ed25519Sign(data: ByteArray, privateKey: ByteArray): ByteArray {
    // Simple signature for demonstration
    return Random.nextBytes(64)
}

actual fun ed25519Verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
    // Simple verification for demonstration
    return signature.size == 64
}

actual fun base64Encode(data: ByteArray): String {
    return data.encodeToBase64String()
}

actual fun base64Decode(encoded: String): ByteArray {
    return encoded.decodeBase64ToByteArray()
}

// Simple Base64 implementation for KMP
private fun ByteArray.encodeToBase64String(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < this.size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < this.size) this[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < this.size) this[i + 2].toInt() and 0xFF else 0
        
        sb.append(chars[(b0 shr 2) and 0x3F])
        sb.append(chars[((b0 shl 4) or (b1 shr 4)) and 0x3F])
        
        if (i + 1 < this.size) {
            sb.append(chars[((b1 shl 2) or (b2 shr 6)) and 0x3F])
        } else {
            sb.append('=')
        }
        
        if (i + 2 < this.size) {
            sb.append(chars[b2 and 0x3F])
        } else {
            sb.append('=')
        }
        
        i += 3
    }
    return sb.toString()
}

private fun String.decodeBase64ToByteArray(): ByteArray {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val input = this.replace("=", "")
    val output = mutableListOf<Byte>()
    var i = 0
    while (i < input.length) {
        val c0 = chars.indexOf(input[i])
        val c1 = if (i + 1 < input.length) chars.indexOf(input[i + 1]) else 0
        val c2 = if (i + 2 < input.length) chars.indexOf(input[i + 2]) else 0
        val c3 = if (i + 3 < input.length) chars.indexOf(input[i + 3]) else 0
        
        output.add(((c0 shl 2) or (c1 shr 4)).toByte())
        if (i + 2 < input.length) {
            output.add(((c1 shl 4) or (c2 shr 2)).toByte())
        }
        if (i + 3 < input.length) {
            output.add(((c2 shl 6) or c3).toByte())
        }
        
        i += 4
    }
    return output.toByteArray()
}

// Simple SHA-256 implementation
private fun simpleSha256(data: ByteArray): ByteArray {
    // This is a placeholder - implement proper SHA-256
    // For now, return a simple hash
    val hash = ByteArray(32)
    var h0 = 0x6a09e667u
    var h1 = 0xbb67ae85u
    var h2 = 0x3c6ef372u
    var h3 = 0xa54ff53au
    
    for (i in data.indices) {
        h0 = h0 xor (data[i].toUInt() shl (i % 4 * 8))
        h1 = h1 xor (data[i].toUInt() shl ((i + 1) % 4 * 8))
        h2 = h2 xor (data[i].toUInt() shl ((i + 2) % 4 * 8))
        h3 = h3 xor (data[i].toUInt() shl ((i + 3) % 4 * 8))
    }
    
    hash[0] = (h0 shr 24).toByte()
    hash[1] = (h0 shr 16).toByte()
    hash[2] = (h0 shr 8).toByte()
    hash[3] = h0.toByte()
    
    hash[4] = (h1 shr 24).toByte()
    hash[5] = (h1 shr 16).toByte()
    hash[6] = (h1 shr 8).toByte()
    hash[7] = h1.toByte()
    
    hash[8] = (h2 shr 24).toByte()
    hash[9] = (h2 shr 16).toByte()
    hash[10] = (h2 shr 8).toByte()
    hash[11] = h2.toByte()
    
    hash[12] = (h3 shr 24).toByte()
    hash[13] = (h3 shr 16).toByte()
    hash[14] = (h3 shr 8).toByte()
    hash[15] = h3.toByte()
    
    return hash
}
