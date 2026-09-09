package strata.security.iiv

data class IIVDecision(
    val approved: Boolean,
    val score: Int,
    val reasons: List<String>
)
