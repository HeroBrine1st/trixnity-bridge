package ru.herobrine1st.matrix.bridge.api.value

/**
 * This class is used to distinguish different requests between actors.
 *
 * For example: one actor has access to 1 room and another actor to second room. If bridge fetches
 * users in room, it uses [RemoteActorId] of actor that has access to it to signal [ru.herobrine1st.matrix.bridge.api.RemoteWorker.getRoomMembers]
 * what actor to use. [ru.herobrine1st.matrix.bridge.api.RemoteWorker] may use or ignore it, depending on its capabilities and other internal state.
 */

public interface RemoteActorId {
    /**
     * This function has same semantics as [toString] but result is used to build an alias.
     *
     * Resulting value MUST be unique for every actor.
     *
     * @return string that can be used as part of [room alias](https://spec.matrix.org/latest/appendices/#room-aliases)
     */
    public fun toAliasPart(): String
}