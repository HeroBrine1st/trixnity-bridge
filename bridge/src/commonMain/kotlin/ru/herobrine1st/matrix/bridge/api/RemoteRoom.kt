package ru.herobrine1st.matrix.bridge.api

public data class RemoteRoom<ROOM : Any>(
    val id: ROOM,
    val displayName: String?,
    val isDirect: Boolean
    // TODO
)

public interface RoomDataHolder<ROOM : Any> {
    public val roomData: RemoteRoom<ROOM>?
}