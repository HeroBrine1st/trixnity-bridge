package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId

/**
 * This interface builds matrix ids given remote ids.
 */
public interface RemoteIdToMatrixMapper<ROOM : Any, USER : Any> {
    public fun buildRoomAlias(remoteRoomId: ROOM): RoomAliasId

    public fun buildPuppetUserId(remoteUserId: USER): UserId

    public fun interface Factory<ROOM : Any, USER : Any> {
        public fun create(
            roomAliasPrefix: String,
            puppetPrefix: String,
            homeserverDomain: String
        ): RemoteIdToMatrixMapper<ROOM, USER>
    }
}