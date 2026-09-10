package strata.security.policy

import strata.security.SecurityResult
import kotlin.math.max
import kotlin.math.min

/**
 * Heuristic-based structure inspector for player builds.
 *
 * The rule avoids literal block-name filtering and instead scores geometry. That makes it harder
 * to bypass with different block materials while reducing false positives for legitimate builds.
 */
class BuildIntegrityRule : SecurityRule {
    override fun appliesTo(category: String): Boolean = category == "BUILD_INTEGRITY"

    override fun check(context: Map<String, Any>): SecurityResult {
        val features = BuildFeatures.from(context)

        if (features.blockCount < 8) return SecurityResult.ALLOW
        if (features.isTrustedBlueprint) return SecurityResult.ALLOW
        if (features.isComplexArchitecture()) return SecurityResult.ALLOW

        val score = prohibitedPrimitiveScore(features)
        // 🟡 Report only — build stays, flagged for human moderator review
        return if (score >= 0.72f) SecurityResult.FLAG_FOR_REVIEW else SecurityResult.ALLOW
    }

    private fun prohibitedPrimitiveScore(features: BuildFeatures): Float {
        var score = 0.0f

        if (features.isSingularPrimitive) score += 0.20f
        if (features.isIsolated) score += 0.12f
        if (features.complexity < 0.28f) score += 0.18f
        if (features.verticalSymmetry > 0.78f) score += 0.16f
        if (features.aspectRatio in 2.0f..5.5f) score += 0.14f
        if (features.topMassRatio > 0.30f && features.baseMassRatio < 0.35f) score += 0.16f
        if (features.appendageCount in 1..2 && features.appendageSymmetry > 0.65f) score += 0.14f
        if (features.crossSectionUniformity > 0.72f) score += 0.10f
        if (features.recentReports >= 2) score += 0.10f

        if (features.architecturalSupportCount >= 2) score -= 0.20f
        if (features.decorativeVariation > 0.45f) score -= 0.15f
        if (features.connectedToApprovedBuild) score -= 0.20f

        return min(1.0f, max(0.0f, score))
    }

    private data class BuildFeatures(
        val width: Float,
        val height: Float,
        val depth: Float,
        val blockCount: Int,
        val aspectRatio: Float,
        val verticalSymmetry: Float,
        val complexity: Float,
        val isSingularPrimitive: Boolean,
        val isIsolated: Boolean,
        val topMassRatio: Float,
        val baseMassRatio: Float,
        val appendageCount: Int,
        val appendageSymmetry: Float,
        val crossSectionUniformity: Float,
        val architecturalSupportCount: Int,
        val decorativeVariation: Float,
        val connectedToApprovedBuild: Boolean,
        val isTrustedBlueprint: Boolean,
        val recentReports: Int
    ) {
        fun isComplexArchitecture(): Boolean {
            if (connectedToApprovedBuild) return true
            if (complexity >= 0.55f && architecturalSupportCount >= 2) return true
            if (decorativeVariation >= 0.55f && blockCount >= 96) return true
            if (!isSingularPrimitive && complexity >= 0.45f && blockCount >= 128) return true
            return false
        }

        companion object {
            fun from(context: Map<String, Any>): BuildFeatures {
                val width = context.floatValue("width", context.floatValue("bbox_width", 1.0f)).coerceAtLeast(1.0f)
                val height = context.floatValue("height", context.floatValue("bbox_height", 1.0f)).coerceAtLeast(1.0f)
                val depth = context.floatValue("depth", context.floatValue("bbox_depth", 1.0f)).coerceAtLeast(1.0f)
                val narrowestFootprint = max(1.0f, min(width, depth))
                val computedAspectRatio = height / narrowestFootprint

                return BuildFeatures(
                    width = width,
                    height = height,
                    depth = depth,
                    blockCount = context.intValue("block_count", 0),
                    aspectRatio = context.floatValue("aspect_ratio", computedAspectRatio),
                    verticalSymmetry = context.floatValue("vertical_symmetry", 0.0f),
                    complexity = context.floatValue("complexity", 0.0f),
                    isSingularPrimitive = context.boolValue("is_singular_primitive", false),
                    isIsolated = context.boolValue("is_isolated", true),
                    topMassRatio = context.floatValue("top_mass_ratio", 0.0f),
                    baseMassRatio = context.floatValue("base_mass_ratio", 1.0f),
                    appendageCount = context.intValue("appendage_count", 0),
                    appendageSymmetry = context.floatValue("appendage_symmetry", 0.0f),
                    crossSectionUniformity = context.floatValue("cross_section_uniformity", 0.0f),
                    architecturalSupportCount = context.intValue("architectural_support_count", 0),
                    decorativeVariation = context.floatValue("decorative_variation", 0.0f),
                    connectedToApprovedBuild = context.boolValue("connected_to_approved_build", false),
                    isTrustedBlueprint = context.boolValue("trusted_blueprint", false),
                    recentReports = context.intValue("recent_reports", 0)
                )
            }
        }
    }
}

private fun Map<String, Any>.floatValue(key: String, default: Float): Float = when (val value = this[key]) {
    is Float -> value
    is Double -> value.toFloat()
    is Int -> value.toFloat()
    is Long -> value.toFloat()
    is Short -> value.toFloat()
    else -> default
}

private fun Map<String, Any>.intValue(key: String, default: Int): Int = when (val value = this[key]) {
    is Int -> value
    is Long -> value.toInt()
    is Short -> value.toInt()
    is Float -> value.toInt()
    is Double -> value.toInt()
    else -> default
}

private fun Map<String, Any>.boolValue(key: String, default: Boolean): Boolean = when (val value = this[key]) {
    is Boolean -> value
    else -> default
}
