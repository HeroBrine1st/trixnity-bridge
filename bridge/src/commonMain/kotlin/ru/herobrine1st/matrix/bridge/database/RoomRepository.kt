package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.RoomId
import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId

public interface RoomRepository<ROOM: RemoteRoomId> {
    public suspend fun getRemoteRoom(id: RoomId): ROOM?
    public suspend fun getMxRoom(id: ROOM): RoomId?
    public suspend fun create(mxId: RoomId, remoteId: ROOM, isDirect: Boolean)
}