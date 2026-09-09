package strata.security.policy

import strata.security.SecurityResult

class SexualRule : SecurityRule {

    override fun appliesTo(category: String): Boolean {
        return category == "CONTENT"
    }

    override fun check(context: Map<String, Any>): SecurityResult {
        val input = context["data"] as? String ?: return SecurityResult.ALLOW

        // Word-boundary matching — "strip" in "strip mining" won't match
        val denyTerms = listOf(
            Regex("""\bnude\b""", RegexOption.IGNORE_CASE),
            Regex("""\bporn\b""", RegexOption.IGNORE_CASE),
            Regex("""\bhentai\b""", RegexOption.IGNORE_CASE),
            Regex("""\bbdsm\b""", RegexOption.IGNORE_CASE),
            Regex("""\bxxx\b""", RegexOption.IGNORE_CASE)
        )
        val reviewTerms = listOf(
            Regex("""\bsexual\b""", RegexOption.IGNORE_CASE),
            Regex("""\bexplicit\b""", RegexOption.IGNORE_CASE),
            Regex("""\berotic\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnsfw\b""", RegexOption.IGNORE_CASE),
            Regex("""\bfetish\b""", RegexOption.IGNORE_CASE),
            Regex("""\blewd\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnudity\b""", RegexOption.IGNORE_CASE)
        )

        val denyCount = denyTerms.count { it.containsMatchIn(input) }
        val reviewCount = reviewTerms.count { it.containsMatchIn(input) }

        return when {
            denyCount >= 2 -> SecurityResult.DENY       // Clear intent
            denyCount == 1 && reviewCount >= 1 -> SecurityResult.DENY  // Mixed signals
            denyCount == 1 || reviewCount >= 2 -> SecurityResult.FLAG_FOR_REVIEW  // Borderline
            reviewCount == 1 -> SecurityResult.FLAG_FOR_REVIEW  // Could be innocent
            else -> SecurityResult.ALLOW
        }
    }
}
