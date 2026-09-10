package strata.security.iiv

data class IIVToken(
    val identityId: String,
    val publicKey: String,
    val nonce: String,
    val questionnaire: IIVQuestionnaire,
    val signature: String
) {
    fun signedPayload(): String = questionnaire.canonicalPayload(identityId, nonce)
}
