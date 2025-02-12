package ru.herobrine1st.matrix.bridge.api.value

/**
 * ID of room on remote side.
 */

public interface RemoteRoomId {
    /**
     * This function has same semantics as [toString] but result is used to build an alias.
     *
     * Resulting value MUST be unique for every room, even between different actors (resulting value can include actor id to prevent collisions).
     *
     * @return string that can be used as part of [room alias](https://spec.matrix.org/latest/appendices/#room-aliases)
     */
    public fun toAliasPart(): String
}