package strata.security.policy

import strata.security.SecurityResult
import kotlin.time.TimeSource

/**
 * Social Trust Monitor — Behavioral analysis for chat safety.
 * 
 * In KMP, we use kotlin.time instead of java.time, and
 * mutableMapOf instead of ConcurrentHashMap.
 */
object SocialTrustMonitor {
    private const val WINDOW_SECONDS = 15 * 60L
    private const val MAX_EVENTS_PER_PAIR = 32
    private const val FLAG_THRESHOLD = 0.65f
    private const val DENY_THRESHOLD = 1.10f

    private val conversations = mutableMapOf<ConversationKey, MutableList<SocialSignal>>()
    private val senderProfiles = mutableMapOf<String, SenderTrustProfile>()
    private val timeSource = TimeSource.Monotonic

    fun inspect(context: Map<String, Any>): SecurityResult {
        val senderId = context.stringValue("sender_id") ?: return SecurityResult.ALLOW
        val recipientId = context.stringValue("recipient_id") ?: context.stringValue("target_id") ?: return SecurityResult.ALLOW
        val message = context.stringValue("message") ?: return SecurityResult.ALLOW
        val now = timeSource.markNow().elapsedNow().inWholeSeconds
        val key = ConversationKey(senderId, recipientId)

        val profile = senderProfiles.getOrPut(senderId) { SenderTrustProfile() }

        val event = SocialSignal(
            atEpochSecond = now,
            offPlatform = offPlatformRegex.containsMatchIn(message),
            secrecy = secrecyRegex.containsMatchIn(message),
            authorityClaim = authorityRegex.containsMatchIn(message),
            pressure = pressureRegex.containsMatchIn(message),
            credentialRequest = credentialRegex.containsMatchIn(message),
            personalBoundary = personalBoundaryRegex.containsMatchIn(message),
            identityMismatch = context.boolValue("identity_mismatch", false),
            senderReputation = context.floatValue("sender_reputation", 1.0f),
            identityAgeHours = context.floatValue("identity_age_hours", 9999.0f)
        )

        val events = conversations.getOrPut(key) { mutableListOf() }
        events.add(event)
        trim(events, now)
        val risk = cumulativeRisk(events, profile)
        val decision = decide(risk, profile, event)
        profile.record(decision, event)
        return decision
    }

    fun reset() {
        conversations.clear()
        senderProfiles.clear()
    }

    private fun trim(events: MutableList<SocialSignal>, nowEpochSecond: Long) {
        events.removeAll { nowEpochSecond - it.atEpochSecond > WINDOW_SECONDS }
        while (events.size > MAX_EVENTS_PER_PAIR) {
            events.removeFirst()
        }
    }

    private fun cumulativeRisk(events: List<SocialSignal>, profile: SenderTrustProfile): Float {
        var risk = 0.0f
        var offPlatformCount = 0
        var secrecyCount = 0
        var pressureCount = 0
        var authorityCount = 0
        var boundaryCount = 0

        for (event in events) {
            if (event.offPlatform) {
                risk += 0.24f
                offPlatformCount++
            }
            if (event.secrecy) {
                risk += 0.30f
                secrecyCount++
            }
            if (event.authorityClaim) {
                risk += 0.18f
                authorityCount++
            }
            if (event.pressure) {
                risk += 0.16f
                pressureCount++
            }
            if (event.credentialRequest) risk += 0.55f
            if (event.personalBoundary) {
                risk += 0.45f
                boundaryCount++
            }
            if (event.identityMismatch) risk += 0.24f
            if (event.senderReputation < 0.25f) risk += 0.10f
            if (event.identityAgeHours < 24.0f && (event.offPlatform || event.authorityClaim)) risk += 0.12f
        }

        if (offPlatformCount >= 2 && secrecyCount >= 1) risk += 0.25f
        if (authorityCount >= 1 && pressureCount >= 1) risk += 0.20f
        if (boundaryCount >= 1 && secrecyCount >= 1) risk += 0.30f
        if (events.size >= 4 && offPlatformCount >= 1) risk += 0.15f

        risk += profile.adaptiveRiskBias()
        return risk.coerceAtLeast(0.0f)
    }

    private fun decide(risk: Float, profile: SenderTrustProfile, event: SocialSignal): SecurityResult {
        val thresholdShift = profile.thresholdShift(event)
        val flagThreshold = FLAG_THRESHOLD + thresholdShift
        val denyThreshold = DENY_THRESHOLD + thresholdShift
        return when {
            risk >= denyThreshold -> SecurityResult.DENY
            risk >= flagThreshold -> SecurityResult.FLAG_FOR_REVIEW
            else -> SecurityResult.ALLOW
        }
    }

    private data class ConversationKey(val senderId: String, val recipientId: String)

    private class SenderTrustProfile(
        var allowedMessages: Int = 0,
        var flaggedMessages: Int = 0,
        var deniedMessages: Int = 0,
        var confirmedReports: Int = 0,
        var recentOffPlatformAttempts: Int = 0,
        var recentSecrecyAttempts: Int = 0
    ) {
        fun record(result: SecurityResult, signal: SocialSignal) {
            when (result) {
                SecurityResult.ALLOW -> allowedMessages++
                SecurityResult.FLAG_FOR_REVIEW -> flaggedMessages++
                SecurityResult.DENY -> deniedMessages++
            }
            if (signal.offPlatform) recentOffPlatformAttempts++
            if (signal.secrecy) recentSecrecyAttempts++
            recentOffPlatformAttempts = recentOffPlatformAttempts.coerceAtMost(8)
            recentSecrecyAttempts = recentSecrecyAttempts.coerceAtMost(8)
        }

        fun adaptiveRiskBias(): Float {
            var bias = 0.0f
            bias += confirmedReports.coerceAtMost(4) * 0.25f
            bias += flaggedMessages.coerceAtMost(5) * 0.06f
            bias += deniedMessages.coerceAtMost(5) * 0.10f
            if (recentOffPlatformAttempts >= 3) bias += 0.18f
            if (recentSecrecyAttempts >= 2) bias += 0.16f
            if (allowedMessages >= 30 && flaggedMessages == 0 && deniedMessages == 0 && confirmedReports == 0) bias -= 0.12f
            return bias
        }

        fun thresholdShift(signal: SocialSignal): Float {
            var shift = 0.0f
            if (confirmedReports > 0) shift -= 0.18f
            if (flaggedMessages >= 2) shift -= 0.08f
            if (deniedMessages >= 1) shift -= 0.10f
            if (signal.identityAgeHours < 24.0f) shift -= 0.05f
            if (allowedMessages >= 30 && flaggedMessages == 0 && deniedMessages == 0 && confirmedReports == 0) shift += 0.08f
            return shift.coerceIn(-0.35f, 0.12f)
        }
    }

    private data class SocialSignal(
        val atEpochSecond: Long,
        val offPlatform: Boolean,
        val secrecy: Boolean,
        val authorityClaim: Boolean,
        val pressure: Boolean,
        val credentialRequest: Boolean,
        val personalBoundary: Boolean,
        val identityMismatch: Boolean,
        val senderReputation: Float,
        val identityAgeHours: Float
    )
}

private val offPlatformRegex = Regex("""(?i)(discord|snapchat|telegram|whatsapp|dm\s+me|private\s+chat|add\s+me)""")
private val secrecyRegex = Regex("""(?i)(don't\s+tell|do\s+not\s+tell|keep\s+this\s+secret|between\s+us|hide\s+this|delete\s+these\s+messages)""")
private val authorityRegex = Regex("""(?i)(admin|staff|moderator|owner|mojang\s+staff|microsoft\s+support|verify\s+your\s+rank)""")
private val pressureRegex = Regex("""(?i)(right\s+now|hurry|urgent|last\s+chance|you\s+will\s+be\s+banned|prove\s+it\s+now)""")
private val credentialRegex = Regex("""(?i)(password|recovery\s+code|backup\s+code|2fa|token|session\s+id|account\s+code|email\s+code)""")
private val personalBoundaryRegex = Regex("""(?i)(age\s+check|home\s+alone|send\s+me\s+pics|cam\s+to\s+cam|talk\s+privately)""")

private fun Map<String, Any>.stringValue(key: String): String? = this[key] as? String

private fun Map<String, Any>.floatValue(key: String, default: Float): Float = when (val value = this[key]) {
    is Float -> value
    is Double -> value.toFloat()
    is Int -> value.toFloat()
    is Long -> value.toFloat()
    is Short -> value.toFloat()
    else -> default
}

private fun Map<String, Any>.boolValue(key: String, default: Boolean): Boolean = when (val value = this[key]) {
    is Boolean -> value
    else -> default
}
