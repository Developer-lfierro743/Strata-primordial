package strata.security.memory

/**
 * 🛡️ Secure Memory Arena — Isolated memory region.
 * 
 * In KMP, we use ByteArray with secure memory practices
 * instead of Java 25 FFM Arena.
 */
class SecureMemoryArena(val size: Int) : AutoCloseable {
    private var data = ByteArray(size)
    private var closed = false

    fun writeByte(offset: Int, value: Byte) {
        check(!closed) { "Arena is closed" }
        data[offset] = value
    }

    fun readByte(offset: Int): Byte {
        check(!closed) { "Arena is closed" }
        return data[offset]
    }

    fun writeShort(offset: Int, value: Short) {
        check(!closed) { "Arena is closed" }
        data[offset] = (value.toInt() and 0xFF).toByte()
        data[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    fun readShort(offset: Int): Short {
        check(!closed) { "Arena is closed" }
        return ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    fun writeInt(offset: Int, value: Int) {
        check(!closed) { "Arena is closed" }
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    fun readInt(offset: Int): Int {
        check(!closed) { "Arena is closed" }
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun toByteArray(): ByteArray {
        check(!closed) { "Arena is closed" }
        return data.copyOf()
    }

    override fun close() {
        if (!closed) {
            MemoryIsolator.secureWipe(data)
            closed = true
        }
    }
}
