package strata.security.test

import strata.security.copyright.ModScanner
import strata.security.copyright.ModSafety
import strata.security.copyright.ThreatLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ModScannerTest {

    private val scanner = ModScanner()

    @Test
    fun `test Fapcraft mod is blocked`() {
        val path = "C:\\Users\\luis\\Dev\\Fapcraft.1.12.2.v1.1.jar"
        val result = scanner.scanFile(path)
        
        assertTrue(result.blocked, "Fapcraft should be blocked")
        assertEquals(ModSafety.MINECRAFT, result.safety, "Should be classified as Minecraft mod")
        assertEquals(ThreatLevel.MEDIUM, result.threatLevel, "Should be MEDIUM threat")
        assertTrue(result.fileName.contains("Fapcraft"), "Filename should contain Fapcraft")
        println("\n📋 FAPCRAFT SCAN RESULT:")
        println("  File: ${result.fileName}")
        println("  Size: ${result.sizeBytes} bytes")
        println("  Safety: ${result.safety.label}")
        println("  Threat: ${result.threatLevel.label}")
        println("  Blocked: ${result.blocked}")
        println("  Reasons: ${result.reasons.joinToString(", ")}")
        println("  Details:\n${result.details}")
    }

    @Test
    fun `test empty mods folder returns zero scans`() {
        val report = scanner.scanFolder("test_mods_empty")
        assertEquals(0, report.scannedCount, "Should scan 0 mods in empty folder")
        assertEquals(0, report.blockedCount, "Should block 0 mods")
    }

    @Test
    fun `test non-existent file returns not a mod result`() {
        val result = scanner.scanFile("C:\\nonexistent.jar")
        assertFalse(result.blocked, "Non-existent file should not be blocked")
        assertTrue(result.details.contains("Not a mod archive"), "Should indicate not a mod")
    }
}