package strata

/**
 * Platform-specific utility functions.
 * Uses `expect`/`actual` for Kotlin Multiplatform.
 */

/** Returns the number of available CPU cores (used to scale worker count). */
expect fun availableProcessors(): Int
