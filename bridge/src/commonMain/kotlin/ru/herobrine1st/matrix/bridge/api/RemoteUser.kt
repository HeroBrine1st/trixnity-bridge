package ru.herobrine1st.matrix.bridge.api

import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId

/**
 * A representation of a user on remote network. Must be the same for all chats on the same network for the same user.
 *
 * All fields can be changed in subsequent updates for particular user denoted by [remoteId].
 * [remoteId] should never change, as it is used to identify remote users and create mappings between users and puppets.
 *
 * @param displayName Display name for this user.
 * @param remoteId Unique id on remote network. Use never-changing identifiers to create this value.
 *
 */
public data class RemoteUser<USER : RemoteUserId>(
    val remoteId: USER,
    val displayName: String,
    // TODO avatarUrl
)

public interface UserDataHolder<USER : RemoteUserId> {
    public val userData: RemoteUser<USER>?
}