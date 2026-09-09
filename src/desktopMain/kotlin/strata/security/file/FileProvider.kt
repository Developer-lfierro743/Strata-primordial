@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package strata.security.file

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random

/**
 * Desktop (mingwX64) actual implementations for file I/O.
 * Uses basic POSIX file operations with kotlinx.cinterop.
 */

actual fun secureShredFile(path: String, passes: Int) {
    val file = fopen(path, "r+b") ?: return
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        fseek(file, 0, SEEK_SET)
        
        val buffer = ByteArray(4096)
        for (pass in 1..passes) {
            fseek(file, 0, SEEK_SET)
            var written = 0L
            while (written < size) {
                for (i in buffer.indices) {
                    buffer[i] = Random.nextBytes(1)[0]
                }
                val remaining = (size - written).toInt().coerceAtMost(4096)
                buffer.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, remaining.toULong(), file)
                }
                written += remaining
            }
            fflush(file)
        }
    } finally {
        fclose(file)
    }
    remove(path)
}

actual fun fileExists(path: String): Boolean {
    return access(path, F_OK) == 0
}

actual fun readFileBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw IllegalArgumentException("Cannot open file: $path")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        fseek(file, 0, SEEK_SET)
        
        val buffer = ByteArray(size.toInt())
        buffer.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        return buffer
    } finally {
        fclose(file)
    }
}

actual fun writeFileBytes(path: String, data: ByteArray) {
    val file = fopen(path, "wb") ?: throw IllegalArgumentException("Cannot create file: $path")
    try {
        data.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, data.size.toULong(), file)
        }
    } finally {
        fclose(file)
    }
}

actual fun deleteFile(path: String): Boolean {
    return remove(path) == 0
}

actual fun createDirectory(path: String): Boolean {
    return mkdir(path) == 0
}

actual fun listFiles(directory: String, extension: String?): List<String> {
    val results = mutableListOf<String>()
    val dir = opendir(directory) ?: return results
    try {
        var entry = readdir(dir)
        while (entry != null) {
            val name = entry.pointed.d_name.toKString()
            if (name != "." && name != "..") {
                if (extension == null || name.endsWith(extension, ignoreCase = true)) {
                    results.add(name)
                }
            }
            entry = readdir(dir)
        }
    } finally {
        closedir(dir)
    }
    return results
}

actual fun moveFile(source: String, destination: String): Boolean {
    return rename(source, destination) == 0
}

actual fun getFileSize(path: String): Long {
    val file = fopen(path, "rb") ?: return -1
    try {
        fseek(file, 0, SEEK_END)
        return ftell(file).toLong()
    } finally {
        fclose(file)
    }
}

actual fun isDirectory(path: String): Boolean {
    // Simple check: try to open as directory
    val dir = opendir(path)
    if (dir != null) {
        closedir(dir)
        return true
    }
    return false
}

actual fun createParentDirs(path: String): Boolean {
    val lastSlash = path.lastIndexOf('/')
    if (lastSlash <= 0) return true
    val parent = path.substring(0, lastSlash)
    if (fileExists(parent)) return true
    createParentDirs(parent)
    return mkdir(parent) == 0
}

actual fun executeProcess(command: String, args: List<String>): ProcessResult {
    // Simplified process execution for KMP
    // Full implementation would use platform-specific APIs
    return ProcessResult(0, "", "")
}
