package strata.security.iiv

import strata.security.crypto.RKPManager

object IIVEngine {
    private val safeIntentPattern = Regex("^[a-zA-Z0-9 _.-]{3,80}$")
    private val highRiskActions = setOf(
        "PRIVATE_CHAT_REQUEST",
        "OFF_PLATFORM_CONTACT",
        "MOD_LOAD",
        "SERVER_JOIN_TRUST",
        "AUTHORITY_CLAIM",
        "MINOR_CONTACT",
        "ACCOUNT_HELP"
    )

    fun createLocalToken(identityId: String, nonce: String, questionnaire: IIVQuestionnaire): IIVToken {
        val payload = questionnaire.canonicalPayload(identityId, nonce)
        return IIVToken(
            identityId = identityId,
            publicKey = RKPManager.getPublicKey(),
            nonce = nonce,
            questionnaire = questionnaire,
            signature = RKPManager.signAction(payload)
        )
    }

    fun verifyToken(token: IIVToken): IIVDecision {
        val signatureValid = try {
            RKPManager.verifySignature(token.publicKey, token.signedPayload(), token.signature)
        } catch (_: Exception) {
            false
        }
        if (!signatureValid) return IIVDecision(false, 100, listOf("invalid_signature"))

        return score(token.questionnaire)
    }

    fun score(questionnaire: IIVQuestionnaire): IIVDecision {
        val reasons = mutableListOf<String>()
        var score = 0

        if (!safeIntentPattern.matches(questionnaire.declaredIntent)) {
            score += 20
            reasons.add("invalid_intent_text")
        }
        if (questionnaire.reason.length < 8) {
            score += 12
            reasons.add("weak_reason")
        }
        if (questionnaire.targetAction in highRiskActions) {
            score += 15
            reasons.add("high_risk_action")
        }
        if (questionnaire.asksForOffPlatformContact) {
            score += 35
            reasons.add("off_platform_contact")
        }
        if (questionnaire.asksForCredentials) {
            score += 60
            reasons.add("credential_request")
        }
        if (questionnaire.requestsSecrecy) {
            score += 45
            reasons.add("secrecy_request")
        }
        if (questionnaire.claimsAuthority) {
            score += 20
            reasons.add("authority_claim")
        }
        if (questionnaire.involvesMinor) {
            score += 25
            reasons.add("minor_context")
        }
        if (!questionnaire.acceptsSafetyRules) {
            score += 25
            reasons.add("rules_not_accepted")
        }
        if (!questionnaire.acceptsLogging && questionnaire.targetAction in highRiskActions) {
            score += 20
            reasons.add("logging_not_accepted")
        }
        if (questionnaire.expectedDurationMinutes !in 1..180) {
            score += 10
            reasons.add("unusual_duration")
        }

        if (questionnaire.targetAction == "MINOR_CONTACT" && questionnaire.asksForOffPlatformContact) {
            score += 35
            reasons.add("minor_off_platform_contact")
        }
        if (questionnaire.claimsAuthority && !questionnaire.acceptsLogging) {
            score += 20
            reasons.add("authority_without_audit")
        }

        return IIVDecision(score < 50, score, reasons)
    }
}
