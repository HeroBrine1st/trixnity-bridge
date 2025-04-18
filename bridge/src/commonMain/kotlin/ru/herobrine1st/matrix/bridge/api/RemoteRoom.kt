package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.UserId

/**
 * This data class contains information on a room at the moment of creation.
 *
 * There are two cases:
 * - Automatic provision: as events are not backfilled, the moment of creation is the moment of provision and so this contains actual data
 * - Historic provision: as events, including all state changes, are backfilled, this contains the state of the room
 *   just after the room creation. The room is brought up to actual state by backfilling.
 */
public data class RemoteRoom<USER : Any, ROOM : Any>(
    val id: ROOM,
    val displayName: String?,
    /**
     * The user who created the room.
     *
     * This value SHOULD be null if provision is automatic. This means appservice bot will create the room, indicating
     * to user that history is lost.
     */
    val creator: USER? = null,
    val directData: DirectData<USER>?,
    /**
     * This collection is used to invite the real user to room.
     *
     * There usually are two cases:
     *
     * - Double-puppeted bridge: room bridging is ordered by room admin (or actor admin), which means
     * that admin should be invited to matrix room so that it is known to the world.
     * - Personal bridge: room bridging is triggered by DM or invite to a new room and the owner of actor account should be invited.
     */
    val realMembers: Set<UserId>,
    // TODO
) {
    @Deprecated("Deprecated", replaceWith = ReplaceWith("directData != null"))
    val isDirect: Boolean get() = directData != null

    public data class DirectData<USER : Any>(
        /**
         * A collection of members in the room
         *
         * This collection MUST NOT include actor account id
         */
        // currently it is assumed that only direct rooms can be created with members invited in the same transaction.
        // probably this property should be in RemoteRoom and isDirect should be brought back, idk
        val members: Set<USER>,
    )
}

public interface RoomDataHolder<ROOM : Any, USER : Any> {
    public val roomData: RemoteRoom<ROOM, USER>?
}