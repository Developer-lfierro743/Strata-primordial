package strata.security

object ReputationManager {
    // A list of "Blacklisted" Public Key hashes
    private val blacklistedIdentities = mutableSetOf<String>()

    fun banIdentity(publicKeyHash: String) {
        blacklistedIdentities.add(publicKeyHash)
        println("[SECURITY] Identity $publicKeyHash has been added to the local ban list.")
    }

    fun isBanned(publicKeyHash: String): Boolean {
        return blacklistedIdentities.contains(publicKeyHash)
    }
}
