package strata.security

import strata.security.file.fileExists
import strata.security.file.readFileBytes

/**
 * Dev Configuration — Development mode detection.
 * 
 * In KMP, we check for a local "dev.mode" file or environment variable.
 */
object DevConfig {
    /**
     * Checks if the engine is in Development Mode.
     * Priority:
     * 1. Check for a local "dev.mode" file (easiest to toggle manually).
     * 2. Check for an Environment Variable (best for CI/CD).
     */
    val isDevMode: Boolean by lazy {
        val devFileExists = fileExists("dev.mode")
        val envVar = getEnvVar("STRATA_DEV_MODE") == "true"
        
        devFileExists || envVar
    }
    
    init {
        if (isDevMode) {
            println("👷 [DEV-MODE] Engine running in Development Mode. Security enforcement is relaxed.")
        }
    }
    
    /**
     * Get environment variable (platform-specific).
     * In KMP, this would use expect/actual, but for simplicity
     * we return empty string on unsupported platforms.
     */
    private fun getEnvVar(name: String): String {
        return try {
            // Try to read from a config file instead
            if (fileExists("env.properties")) {
                val props = readFileBytes("env.properties").decodeToString()
                val lines = props.lines()
                for (line in lines) {
                    if (line.startsWith("$name=")) {
                        return line.substringAfter("=").trim()
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
