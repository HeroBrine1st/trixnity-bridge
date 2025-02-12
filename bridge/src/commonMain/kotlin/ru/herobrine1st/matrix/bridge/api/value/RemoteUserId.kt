package ru.herobrine1st.matrix.bridge.api.value

/**
 * ID of user on remote side (e.g. its account id)
 */
public interface RemoteUserId {
    /**
     * This function has same semantics as [toString] but result is used to build a username.
     *
     * Resulting value MUST be unique for every user, even between different actors (resulting value can include actor id to prevent collisions).
     *
     * @return string that can be used as part of localpart of [user identifier](https://spec.matrix.org/latest/appendices/#user-identifiers)
     */
    public fun toUsernamePart(): String
}