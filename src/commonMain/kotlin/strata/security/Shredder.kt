package strata.security

import strata.security.file.secureShredFile
import strata.security.file.fileExists
import strata.security.file.getFileSize
import strata.security.file.deleteFile

object Shredder {
    private const val PASSES = 3

    fun shred(path: String) {
        if (!fileExists(path)) return
        val len = getFileSize(path)
        if (len == 0L) { deleteFile(path); return }

        try {
            secureShredFile(path, PASSES)
            println("🗑️ [SHREDDER] Permanently deleted: $path ($PASSES passes, $len bytes)")
        } catch (e: Exception) {
            deleteFile(path)
            println("🗑️ [SHREDDER] Force deleted: $path (${e.message})")
        }
    }
}
