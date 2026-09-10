package strata.security.copyright

import strata.security.file.readFileBytes
import strata.security.file.fileExists

/**
 * Asset Image Scanner — Scans image assets for NSFW content.
 * 
 * In KMP, we use simplified byte pattern analysis instead of javax.imageio.
 */
object AssetImageScanner {
    
    /**
     * Scan an image file for NSFW content.
     */
    fun scan(imagePath: String): HsvAnalysisResult {
        if (!fileExists(imagePath)) {
            return HsvAnalysisResult(
                fileName = imagePath,
                flagged = false,
                confidentSkinRatio = 0f,
                genitalRatio = 0f,
                skinClusters = 0,
                reasons = listOf("File not found")
            )
        }
        
        val bytes = readFileBytes(imagePath)
        return HsvAnalyzer.analyze(bytes, imagePath.substringAfterLast("/"))
    }
    
    /**
     * Scan image bytes for NSFW content.
     */
    fun scanBytes(imageBytes: ByteArray, fileName: String): HsvAnalysisResult {
        return HsvAnalyzer.analyze(imageBytes, fileName)
    }
}
