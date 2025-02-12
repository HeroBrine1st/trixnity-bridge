package ru.herobrine1st.matrix.bridge.api

import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId

public data class RemoteRoom(
    val id: RemoteRoomId,
    val displayName: String?,
    val isDirect: Boolean
    // TODO
)

public interface RoomDataHolder {
    public val roomData: RemoteRoom?
}