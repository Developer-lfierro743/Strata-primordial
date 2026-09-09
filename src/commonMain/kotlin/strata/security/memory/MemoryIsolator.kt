package strata.security.memory

import strata.security.crypto.secureRandomBytes

/**
 * Memory Isolator — Secure memory operations.
 * 
 * In KMP, we use ByteArray with secure memory practices
 * instead of Java FFM MemorySegment.
 */
object MemoryIsolator {

    /**
     * Allocate a zeroed byte array (simulates secure memory).
     */
    fun allocate(size: Int, zeroed: Boolean = false): ByteArray {
        return if (zeroed) ByteArray(size) else secureRandomBytes(size)
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    fun secureCompare(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Wipe a byte array (overwrite with zeros).
     */
    fun wipeByteArray(data: ByteArray) {
        for (i in data.indices) {
            data[i] = 0
        }
    }

    /**
     * Fill a byte array with random data.
     */
    fun fillRandom(data: ByteArray) {
        val random = secureRandomBytes(data.size)
        random.copyInto(data)
    }

    /**
     * Secure wipe — overwrite with random data then zeros.
     */
    fun secureWipe(data: ByteArray) {
        fillRandom(data)
        wipeByteArray(data)
    }
}
