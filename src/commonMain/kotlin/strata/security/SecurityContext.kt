package strata.security

data class SecurityContext(
    val category: String,
    val subjectId: String,
    val action: String,
    val objectType: String,
    val objectId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun toPolicyMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "sender_id" to subjectId,
            "action" to action,
            "object_type" to objectType
        )
        objectId?.let { base["object_id"] = it }
        base.putAll(metadata)
        return base
    }
}
