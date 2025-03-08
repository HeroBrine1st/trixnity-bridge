package ru.herobrine1st.matrix.bridge.repository

import net.folivo.trixnity.core.model.RoomId

/**
 * This repository links remote and local ids of rooms, saving actor ownership and whether a given room is direct.
 * For the purposes of bridging, it is considered to be the same room if a link exists between remote and local room ids.
 *
 * This repository SHOULD ensure relations between [ROOM] and [RoomId] are one-to-one (i.e. by issuing UNIQUE constraint on both columns).
 */
public interface RoomRepository<ACTOR : Any, ROOM : Any> {
    /**
     * @param id Room id to search remote id for
     * @return Remote id of the same room
     */
    public suspend fun getRemoteRoom(id: RoomId): ROOM?

    /**
     * @param id Remote id to search room id for
     * @return Room id of the same room
     */
    public suspend fun getMxRoom(id: ROOM): RoomId?

    /**
     * Save pair [mxId]-[remoteId] in database as room owned by [actorId] and being direct if [isDirect] is true.
     * It is guaranteed that [remoteId] relates to [actorId] in cases when [remoteId] contains [ACTOR],
     * allowing to strip [ACTOR] from [remoteId]. Implementations SHOULD check that it is the case.
     *
     * [actorId] and [isDirect] can be ignored if bridge doesn't need it.
     *
     * This method MUST be idempotent.
     */
    public suspend fun create(actorId: ACTOR, mxId: RoomId, remoteId: ROOM, isDirect: Boolean)
}