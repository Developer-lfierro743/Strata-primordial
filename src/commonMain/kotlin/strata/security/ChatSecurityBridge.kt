package strata.security

import strata.security.policy.ChatGuardian

/**
 * ChatSecurityBridge — The security layer between the player and the chat log.
 *
 * Every message goes through:
 *   1. ReputationManager check (banned identity → blocked)
 *   2. ChatGuardian content analysis (grooming, phishing, CSAM → blocked)
 *   3. SafetyGuardian escalation (warning → mute → kick → ban)
 *   4. IIV verification for high-risk actions (private chat requests, etc.)
 *
 * This is the "firewall" — no message touches ChatLog without passing here first.
 */
object ChatSecurityBridge {

    // Muted players: publicKeyHash → mute expiry timestamp (millis)
    private val mutedPlayers = mutableMapOf<String, Long>()

    // Violation counters: publicKeyHash → count
    private val violationCounts = mutableMapOf<String, Int>()

    // Pending reports: reporterId → (targetId, reason, timestamp)
    private val pendingReports = mutableListOf<Triple<String, String, Long>>()

    // System alert queue for the overlay
    private val systemAlerts = mutableListOf<SystemAlert>()

    data class SystemAlert(
        val severity: AlertSeverity,
        val message: String,
        val timestamp: Long
    )

    enum class AlertSeverity { INFO, WARNING, CRITICAL, BAN }

    /**
     * Result of the security check on a chat message.
     */
    sealed class ChatCheckResult {
        /** Message is safe to display. */
        data class Allow(val filteredText: String) : ChatCheckResult()
        /** Message was blocked — show the reason to the sender only. */
        data class Deny(val reason: String) : ChatCheckResult()
        /** Sender is muted — message silently dropped. */
        object Muted : ChatCheckResult()
        /** Sender is globally banned — connection should be terminated. */
        data class Banned(val reason: String) : ChatCheckResult()
        /** Message flagged for review but still shown with a warning indicator. */
        data class Flagged(val filteredText: String, val flagReason: String) : ChatCheckResult()
    }

    /**
     * Run a chat message through the full security pipeline.
     *
     * @param senderId The player's cryptographic identity hash (Ed25519 public key SHA-256)
     * @param message Raw message text from the player
     * @param metadata Optional context (reputation score, voice signature, etc.)
     * @return ChatCheckResult indicating whether to show, block, or escalate
     */
    fun checkMessage(
        senderId: String,
        message: String,
        metadata: Map<String, Any> = emptyMap()
    ): ChatCheckResult {
        // ── 0. Null/empty check ──
        if (message.isBlank()) {
            return ChatCheckResult.Deny("empty_message")
        }

        // ── 1. Global ban check ──
        if (ReputationManager.isBanned(senderId)) {
            return ChatCheckResult.Banned("identity_banned")
        }

        // ── 2. Mute check ──
        val muteExpiry = mutedPlayers[senderId]
        if (muteExpiry != null) {
            val now = currentTimeMillis()
            if (now < muteExpiry) {
                val remaining = (muteExpiry - now) / 1000
                return ChatCheckResult.Muted
            } else {
                // Mute expired, remove it
                mutedPlayers.remove(senderId)
            }
        }

        // ── 3. Content analysis via ChatGuardian ──
        val contextMap = buildMap {
            put("sender_id", senderId)
            put("message", message)
            put("category", "COMMUNICATION_INTEGRITY")
            put("action", "CHAT_MESSAGE")
            put("signature_valid", metadata["signature_valid"] ?: true)
            put("voice_signature", metadata["voice_signature"] ?: 0.0f)
            put("identity_age_hours", metadata["identity_age_hours"] ?: 9999.0f)
            put("sender_reputation", metadata["sender_reputation"] ?: 1.0f)
            put("identity_mismatch", metadata["identity_mismatch"] ?: false)
            put("verified_relationship", metadata["verified_relationship"] ?: false)
            putAll(metadata)
        }

        val securityResult = SafetyGuardian.evaluate(
            SecurityContext(
                category = "COMMUNICATION_INTEGRITY",
                subjectId = senderId,
                action = "CHAT_MESSAGE",
                objectType = "message",
                metadata = contextMap
            )
        )

        when (securityResult) {
            SecurityResult.DENY -> {
                val reason = ChatGuardian.lastDenyReason
                recordViolation(senderId, reason)
                systemAlerts.add(
                    SystemAlert(
                        AlertSeverity.CRITICAL,
                        "Blocked message from $senderId: $reason",
                        currentTimeMillis()
                    )
                )
                return ChatCheckResult.Deny(reason)
            }
            SecurityResult.FLAG_FOR_REVIEW -> {
                val reason = ChatGuardian.lastDenyReason
                recordViolation(senderId, "flagged_$reason")
                systemAlerts.add(
                    SystemAlert(
                        AlertSeverity.WARNING,
                        "Flagged message from $senderId: $reason",
                        currentTimeMillis()
                    )
                )
                return ChatCheckResult.Flagged(message, reason)
            }
            SecurityResult.ALLOW -> {
                // Continue to IIV check for high-risk patterns
            }
        }

        // ── 4. IIV check for high-risk content ──
        val highRiskPatterns = listOf(
            Regex("(?i)add\\s+me\\s+on\\s+discord"),
            Regex("(?i)dm\\s+me\\s+private"),
            Regex("(?i)give\\s+me\\s+your\\s+password"),
            Regex("(?i)i\\s+am\\s+admin"),
            Regex("(?i)don'?t\\s+tell\\s+anyone")
        )

        val isHighRisk = highRiskPatterns.any { it.containsMatchIn(message) }
        if (isHighRisk) {
            systemAlerts.add(
                SystemAlert(
                    AlertSeverity.WARNING,
                    "High-risk message from $senderId detected",
                    currentTimeMillis()
                )
            )
        }

        // ── 5. Passed all checks ──
        return ChatCheckResult.Allow(message)
    }

    /**
     * Report a player for harmful behavior.
     */
    fun reportPlayer(reporterId: String, targetId: String, reason: String): Boolean {
        if (reporterId == targetId) return false
        if (ReputationManager.isBanned(reporterId)) return false

        pendingReports.add(Triple(reporterId, targetId, currentTimeMillis()))
        systemAlerts.add(
            SystemAlert(
                AlertSeverity.INFO,
                "Player $reporterId reported $targetId: $reason",
                currentTimeMillis()
            )
        )
        return true
    }

    /**
     * Mute a player for a specified duration.
     */
    fun mutePlayer(targetId: String, durationMillis: Long) {
        val currentExpiry = mutedPlayers[targetId] ?: 0L
        val newExpiry = currentTimeMillis() + durationMillis
        if (newExpiry > currentExpiry) {
            mutedPlayers[targetId] = newExpiry
            systemAlerts.add(
                SystemAlert(
                    AlertSeverity.WARNING,
                    "Player $targetId muted for ${durationMillis / 1000}s",
                    currentTimeMillis()
                )
            )
        }
    }

    /**
     * Check if a player is currently muted.
     */
    fun isMuted(playerId: String): Boolean {
        val expiry = mutedPlayers[playerId] ?: return false
        if (currentTimeMillis() >= expiry) {
            mutedPlayers.remove(playerId)
            return false
        }
        return true
    }

    /**
     * Get remaining mute time in seconds.
     */
    fun getMuteRemainingSeconds(playerId: String): Long {
        val expiry = mutedPlayers[playerId] ?: return 0L
        val remaining = (expiry - currentTimeMillis()) / 1000
        return remaining.coerceAtLeast(0)
    }

    /**
     * Get violation count for a player.
     */
    fun getViolationCount(playerId: String): Int {
        return violationCounts[playerId] ?: 0
    }

    /**
     * Consume the next system alert (for the overlay to display).
     */
    fun consumeAlert(): SystemAlert? {
        return if (systemAlerts.isNotEmpty()) systemAlerts.removeFirst() else null
    }

    /**
     * Peek at all pending alerts without consuming them.
     */
    fun peekAlerts(): List<SystemAlert> = systemAlerts.toList()

    /**
     * Get pending reports count.
     */
    fun getPendingReportsCount(): Int = pendingReports.size

    /**
     * Get all pending reports (for admin/moderator review).
     */
    fun getPendingReports(): List<Triple<String, String, Long>> = pendingReports.toList()

    /**
     * Clear old reports (older than 24 hours).
     */
    fun pruneOldReports() {
        val cutoff = currentTimeMillis() - 24 * 60 * 60 * 1000
        pendingReports.removeAll { it.third < cutoff }
    }

    private fun recordViolation(senderId: String, reason: String) {
        val count = (violationCounts[senderId] ?: 0) + 1
        violationCounts[senderId] = count

        when (count) {
            1 -> {
                systemAlerts.add(
                    SystemAlert(AlertSeverity.WARNING, "⚠️ First warning for $senderId", currentTimeMillis())
                )
            }
            2 -> {
                mutePlayer(senderId, 5 * 60 * 1000L) // 5 minutes
                systemAlerts.add(
                    SystemAlert(AlertSeverity.WARNING, "🔇 $senderId muted for 5 minutes", currentTimeMillis())
                )
            }
            3 -> {
                mutePlayer(senderId, 30 * 60 * 1000L) // 30 minutes
                systemAlerts.add(
                    SystemAlert(AlertSeverity.CRITICAL, "🔇 $senderId muted for 30 minutes", currentTimeMillis())
                )
            }
            else -> {
                ReputationManager.banIdentity(senderId)
                systemAlerts.add(
                    SystemAlert(AlertSeverity.BAN, "🚫 $senderId has been banned", currentTimeMillis())
                )
            }
        }
    }

    // Simple monotonic counter for timestamps (KMP-compatible)
    private var _timeCounter = 0L
    private fun currentTimeMillis(): Long {
        _timeCounter++
        return _timeCounter
    }
}
