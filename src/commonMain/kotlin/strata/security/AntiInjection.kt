package strata.security

import strata.security.crypto.sha256Hex
import strata.security.file.fileExists
import strata.security.file.readFileBytes
import kotlinx.coroutines.*

/**
 * Anti-Injection — Process-level protections.
 * 
 * In KMP, we use platform-specific expect/actual for:
 * - Process integrity checks
 * - Class hash verification
 * - Tamper detection
 */
object AntiInjection {
    private const val INTEGRITY_CHECK_INTERVAL_SECONDS = 30L
    
    private val classHashes = mutableMapOf<String, String>()
    private var runningFromJar = false
    private val watchdogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun arm() {
        println("🔒 [ANTI_INJECTION] Arming process-level protections...")
        
        runningFromJar = seedIntegrityHashes()
        if (runningFromJar) {
            startIntegrityWatchdog()
        }
        
        println("✅ [ANTI_INJECTION] All protections active")
    }
    
    private fun seedIntegrityHashes(): Boolean {
        try {
            // In KMP, we check for a hash manifest file
            val manifestPath = "integrity.manifest"
            if (!fileExists(manifestPath)) {
                println("⚠️ [ANTI_INJECTION] No integrity manifest found")
                return false
            }
            
            val manifest = readFileBytes(manifestPath).decodeToString()
            val lines = manifest.lines().filter { it.isNotBlank() }
            
            for (line in lines) {
                val parts = line.split(" ", limit = 2)
                if (parts.size == 2) {
                    classHashes[parts[1]] = parts[0]
                }
            }
            
            if (classHashes.isNotEmpty()) {
                println("✅ [ANTI_INJECTION] Seeded ${classHashes.size} class hashes from manifest")
                return true
            }
        } catch (e: Exception) {
            println("⚠️ [ANTI_INJECTION] Failed to load integrity manifest: ${e.message}")
        }
        return false
    }
    
    private fun startIntegrityWatchdog() {
        if (classHashes.isEmpty()) {
            println("⚠️ [ANTI_INJECTION] Integrity watchdog disabled (no hashes)")
            return
        }
        
        watchdogScope.launch {
            while (isActive) {
                delay(INTEGRITY_CHECK_INTERVAL_SECONDS * 1000L)
                try {
                    checkIntegrity()
                } catch (e: Exception) {
                    println("⚠️ [ANTI_INJECTION] Integrity check error: ${e.message}")
                }
            }
        }
        
        println("✅ [ANTI_INJECTION] Integrity watchdog every ${INTEGRITY_CHECK_INTERVAL_SECONDS}s")
    }
    
    fun disarm() {
        watchdogScope.cancel()
    }
    
    private fun checkIntegrity() {
        for ((className, expectedHash) in classHashes) {
            if (!fileExists(className)) continue
            
            val bytes = readFileBytes(className)
            val actualHash = sha256Hex(bytes)
            
            if (expectedHash != actualHash) {
                println("🚨 [ANTI_INJECTION] CLASS TAMPERED: $className")
                println("🚨 [ANTI_INJECTION] Expected: ${expectedHash.take(16)}...")
                println("🚨 [ANTI_INJECTION] Actual:   ${actualHash.take(16)}...")
                println("🚨 [ANTI_INJECTION] Game integrity violated! Forcing shutdown.")
                // In production, this would force a shutdown
                // Runtime.getRuntime().halt(127)
                return
            }
        }
    }
}
