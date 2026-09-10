package strata.security.policy

import strata.security.SecurityResult

interface SecurityRule {
    fun appliesTo(category: String): Boolean
    fun check(context: Map<String, Any>): SecurityResult
}
