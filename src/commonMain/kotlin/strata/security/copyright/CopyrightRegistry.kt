package strata.security.copyright

data class ContentRegistration(
    val creatorId: String,
    val creatorName: String,
    val contentHash: String,
    val contentType: String,
    val signature: String,
    val publicKey: String,
    val metadata: Map<String, String> = emptyMap()
)

data class TakedownRequest(
    val claimantId: String,
    val contentHash: String,
    val reason: String,
    val evidenceHash: String,
    val signature: String
)

object CopyrightRegistry {
    private val registrations = mutableMapOf<String, ContentRegistration>()
    private val creatorKeys = mutableMapOf<String, String>()
    private val contentCreators = mutableMapOf<String, String>()
    private val takedownQueue = mutableListOf<TakedownRequest>()
    private val blockedContent = mutableSetOf<String>()
    private val plagiarizedContent = mutableSetOf<String>()

    fun registerCreator(creatorId: String, publicKey: String) {
        creatorKeys[creatorId] = publicKey
    }

    fun getCreatorPublicKey(creatorId: String): String? = creatorKeys[creatorId]

    fun registerContent(registration: ContentRegistration): Boolean {
        if (registrations.containsKey(registration.contentHash)) return false
        registrations[registration.contentHash] = registration
        contentCreators[registration.contentHash] = registration.creatorId
        return true
    }

    fun findContentByHash(hash: String): ContentRegistration? = registrations[hash]

    fun findContentByCreator(creatorId: String): List<ContentRegistration> =
        registrations.values.filter { it.creatorId == creatorId }

    fun fileTakedown(request: TakedownRequest) {
        takedownQueue.add(request)
    }

    fun processTakedowns(): List<String> {
        val resolved = mutableListOf<String>()
        val iterator = takedownQueue.iterator()
        while (iterator.hasNext()) {
            val req = iterator.next()
            val existing = registrations[req.contentHash]
            if (existing != null && existing.creatorId != req.claimantId) {
                blockedContent.add(req.contentHash)
                resolved.add(req.contentHash)
            }
            iterator.remove()
        }
        return resolved
    }

    fun isBlocked(contentHash: String): Boolean = blockedContent.contains(contentHash)

    fun isPlagiarized(contentHash: String): Boolean = plagiarizedContent.contains(contentHash)

    fun detectPlagiarism(newHash: String): String? {
        for ((existingHash, reg) in registrations) {
            if (existingHash != newHash && reg.contentType != "mod") continue
        }
        return null
    }

    fun reportPlagiarism(originalHash: String, copyHash: String) {
        plagiarizedContent.add(copyHash)
    }

    fun getCreatorOf(hash: String): String? = contentCreators[hash]

    val registeredContentCount: Int get() = registrations.size
    val creatorCount: Int get() = creatorKeys.size
}
