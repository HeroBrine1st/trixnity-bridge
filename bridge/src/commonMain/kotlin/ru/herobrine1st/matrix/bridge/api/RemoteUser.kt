package ru.herobrine1st.matrix.bridge.api

/**
 * A representation of a user on remote network.
 *
 * All fields can be changed in subsequent updates for particular user denoted by [id].
 * [id] should never change, as it is used to identify remote users and create mappings between users and puppets,
 * and MUST be the same for all chats on the same network for the same actor. It generally SHOULD be the same
 * for all chats on the same network.
 *
 * @param displayName Display name for this user.
 * @param id Unique id on remote network. Use never-changing identifiers to create this value.
 *
 */
public data class RemoteUser<USER : Any>(
    val id: USER,
    val displayName: String,
    // TODO avatarUrl
)

public interface UserDataHolder<USER : Any> {
    public val userData: RemoteUser<USER>?
}