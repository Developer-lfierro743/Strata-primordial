package strata.security.policy

import strata.security.SecurityResult

class ChatGuardian : SecurityRule {

    private val aiVoiceConfidenceThreshold = 0.85f
    private val maxMessageLength = 512

    // 🎯 Phishing / scam links
    private val phishingRegex = Regex("""(?i)(click\s+this\s+link|give\s+me\s+your\s+token|password\s+reset|verify\s+account|free\s+rank|free\s+cape|claim\s+reward)""")
    private val suspiciousDomainRegex = Regex("""(?i)(https?://[a-zA-Z0-9.-]+\.(xyz|top|site|info|club|click|link))""")

    // 🧑‍🤝‍🧑 Off-platform contact (grooming vector)
    private val offPlatformRegex = Regex("""(?i)(add\s+me\s+on\s+(discord|snapchat|telegram|whatsapp)|dm\s+me|private\s+chat)""")

    // 🤫 Secrecy requests (grooming vector)
    private val secrecyRegex = Regex("""(?i)(don't\s+tell|do\s+not\s+tell|keep\s+this\s+secret|between\s+us|hide\s+this\s+from|delete\s+these\s+messages)""")

    // 👑 Authority impersonation
    private val authorityRegex = Regex("""(?i)(i\s+am\s+(admin|staff|moderator|owner)|mojang\s+staff|microsoft\s+support)""")

    // ⏰ Urgency/pressure tactics
    private val pressureRegex = Regex("""(?i)(right\s+now|hurry|last\s+chance|urgent|prove\s+it\s+now)""")

    // 🔑 Credential harvesting
    private val credentialRegex = Regex("""(?i)(password|recovery\s+code|backup\s+code|2fa|session\s+id)""")

    // 🚨 Personal boundary violations (grooming vector)
    private val personalBoundaryRegex = Regex("""(?i)(send\s+me\s+pics|cam\s+to\s+cam|can\s+we\s+talk\s+privately|are\s+you\s+home\s+alone)""")

    override fun appliesTo(category: String): Boolean {
        return category == "COMMUNICATION_INTEGRITY"
    }

    override fun check(context: Map<String, Any>): SecurityResult {
        val signatureValid = context["signature_valid"] as? Boolean
        if (signatureValid == false) {
            lastDenyReason = "unsigned_message"
            return SecurityResult.DENY
        }

        val messageContent = context["message"] as? String ?: return SecurityResult.ALLOW
        if (messageContent.length > maxMessageLength) {
            lastDenyReason = "message_too_long"
            return SecurityResult.DENY
        }

        val riskScore = socialEngineeringRisk(messageContent, context)
        return when {
            riskScore >= 0.85f -> { lastDenyReason = "high_risk_detected"; SecurityResult.DENY }
            riskScore >= 0.55f -> { lastDenyReason = "moderate_risk_detected"; SecurityResult.FLAG_FOR_REVIEW }
            else -> { lastDenyReason = ""; SecurityResult.ALLOW }
        }
    }

    private fun socialEngineeringRisk(message: String, context: Map<String, Any>): Float {
        var score = 0.0f

        if (phishingRegex.containsMatchIn(message)) score += 0.75f
        if (suspiciousDomainRegex.containsMatchIn(message)) score += 0.30f
        if (offPlatformRegex.containsMatchIn(message)) score += 0.25f
        if (secrecyRegex.containsMatchIn(message)) score += 0.35f
        if (authorityRegex.containsMatchIn(message)) score += 0.20f
        if (pressureRegex.containsMatchIn(message)) score += 0.18f
        if (credentialRegex.containsMatchIn(message)) score += 0.55f
        if (personalBoundaryRegex.containsMatchIn(message)) score += 0.40f

        val audioSignature = context.floatValue("voice_signature", 0.0f)
        if (audioSignature > aiVoiceConfidenceThreshold) score += 0.50f

        val identityAgeHours = context.floatValue("identity_age_hours", 9999.0f)
        if (identityAgeHours < 24.0f && (offPlatformRegex.containsMatchIn(message) || authorityRegex.containsMatchIn(message))) {
            score += 0.18f
        }

        val priorWarnings = context.intValue("prior_social_warnings", 0)
        if (priorWarnings > 0) score += (priorWarnings.coerceAtMost(3) * 0.12f)

        val reputation = context.floatValue("sender_reputation", 1.0f)
        if (reputation < 0.25f) score += 0.16f

        val identityMismatch = context.boolValue("identity_mismatch", false)
        if (identityMismatch) score += 0.32f

        val verifiedRelationship = context.boolValue("verified_relationship", false)
        if (verifiedRelationship) score -= 0.15f

        return score.coerceIn(0.0f, 1.0f)
    }

    companion object {
        var lastDenyReason: String = ""
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
