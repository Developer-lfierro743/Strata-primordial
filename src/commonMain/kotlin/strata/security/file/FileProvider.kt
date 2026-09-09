package strata.security.file

/**
 * Platform-specific file I/O operations.
 * expect in commonMain, actual in desktopMain.
 */

/** Secure file shredding (multi-pass overwrite) */
expect fun secureShredFile(path: String, passes: Int = 3)

/** Check if file exists */
expect fun fileExists(path: String): Boolean

/** Read file as bytes */
expect fun readFileBytes(path: String): ByteArray

/** Write bytes to file */
expect fun writeFileBytes(path: String, data: ByteArray)

/** Delete file */
expect fun deleteFile(path: String): Boolean

/** Create directory */
expect fun createDirectory(path: String): Boolean

/** List files in directory */
expect fun listFiles(directory: String, extension: String? = null): List<String>

/** Move file */
expect fun moveFile(source: String, destination: String): Boolean

/** Get file size */
expect fun getFileSize(path: String): Long

/** Check if directory */
expect fun isDirectory(path: String): Boolean

/** Create parent directories */
expect fun createParentDirs(path: String): Boolean

/** Process execution */
expect fun executeProcess(command: String, args: List<String> = emptyList()): ProcessResult

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
