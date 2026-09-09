package strata.security.iiv

data class IIVQuestionnaire(
    val declaredIntent: String,
    val targetAction: String,
    val recipientId: String? = null,
    val reason: String,
    val expectedDurationMinutes: Int,
    val asksForOffPlatformContact: Boolean = false,
    val asksForCredentials: Boolean = false,
    val requestsSecrecy: Boolean = false,
    val claimsAuthority: Boolean = false,
    val involvesMinor: Boolean = false,
    val acceptsSafetyRules: Boolean = false,
    val acceptsLogging: Boolean = false
) {
    fun canonicalPayload(identityId: String, nonce: String): String {
        return listOf(
            "identity=$identityId",
            "nonce=$nonce",
            "intent=$declaredIntent",
            "action=$targetAction",
            "recipient=${recipientId ?: ""}",
            "reason=$reason",
            "duration=$expectedDurationMinutes",
            "off_platform=$asksForOffPlatformContact",
            "credentials=$asksForCredentials",
            "secrecy=$requestsSecrecy",
            "authority=$claimsAuthority",
            "minor=$involvesMinor",
            "rules=$acceptsSafetyRules",
            "logging=$acceptsLogging"
        ).joinToString("|")
    }
}
