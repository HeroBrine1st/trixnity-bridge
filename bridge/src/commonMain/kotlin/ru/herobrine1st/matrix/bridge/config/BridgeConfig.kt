package ru.herobrine1st.matrix.bridge.config

public data class BridgeConfig(
    val homeserverDomain: String,
    val botLocalpart: String,
    val puppetPrefix: String,
    val roomAliasPrefix: String,
    val provisioning: Provisioning,
    val presence: Presence
) {
    public data class Provisioning(val whitelist: List<Regex>, val blacklist: List<Regex>)

    public data class Presence(val remote: Boolean, val local: Boolean)

    public companion object // for extension methods
}
