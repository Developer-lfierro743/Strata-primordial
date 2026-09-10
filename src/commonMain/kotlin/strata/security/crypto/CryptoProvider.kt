package strata.security.crypto

/**
 * Platform-specific crypto operations.
 * expect in commonMain, actual in desktopMain (JVM) or other platforms.
 */

/** Generate cryptographically secure random bytes */
expect fun secureRandomBytes(length: Int): ByteArray

/** SHA-256 hash */
expect fun sha256(data: ByteArray): ByteArray

/** SHA-256 hash as hex string */
expect fun sha256Hex(data: ByteArray): String

/** AES-256-GCM encrypt */
expect fun aesGcmEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

/** AES-256-GCM decrypt */
expect fun aesGcmDecrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

/** Ed25519 keypair generation */
expect fun generateEd25519KeyPair(): Ed25519KeyPair

/** Ed25519 sign */
expect fun ed25519Sign(data: ByteArray, privateKey: ByteArray): ByteArray

/** Ed25519 verify */
expect fun ed25519Verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

/** Base64 encode */
expect fun base64Encode(data: ByteArray): String

/** Base64 decode */
expect fun base64Decode(encoded: String): ByteArray

data class Ed25519KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
)
