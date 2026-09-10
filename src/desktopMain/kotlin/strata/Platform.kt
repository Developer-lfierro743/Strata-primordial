package strata

/**
 * Desktop (mingwX64) implementation of platform utilities.
 */

/**
 * Returns a safe default processor count.
 * In production, Main.kt uses SDL_GetNumLogicalCPUCores() directly for the
 * actual value (this function is only used as a fallback in WorldStreamer
 * default parameters and test environments).
 */
actual fun availableProcessors(): Int = 4
