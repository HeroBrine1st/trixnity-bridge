package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

public interface RemoteWorkerAPI<USER, ROOM, MESSAGE> {
    /**
     * Provides a way to get internal mapping of event IDs.
     *
     * @param id The same value as was previously used in [ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker.Event.Remote.Room.Message.messageId]
     * @return [EventId] of the same event on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: MESSAGE): EventId?

    /**
     * Provides a way to get internal mapping of event IDs.
     *
     * @param id The matrix event ID
     * @return message ID of the same message on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: EventId): MESSAGE?

    /**
     * @param id remote ID of puppet
     * @return matrix ID of the same puppet or null if not found
     */
    // TODO replicate if there's no such user
    public suspend fun getPuppetId(id: USER): UserId?

    /**
     * @param id matrix ID of puppet
     * @return remote ID of the same puppet or null if not found
     */
    public suspend fun getPuppetId(id: UserId): USER?

    /**
     * Provides a way to get internal mapping of room IDs.
     *
     * @param id remote ID of room
     * @return matrix id of the same room or null if not found
     */
    public suspend fun getRoomId(id: ROOM): RoomId?

    /**
     * Provides a way to get internal mapping of room IDs.
     *
     * @param id matrix ID of room
     * @return remote ID of the same room or null if not found
     */
    public suspend fun getRoomId(id: RoomId): ROOM?

    /**
     * Returns true if room denoted by [id] is bridged.
     *
     * Bridged room is room that is known by both this application service and homeserver, and also by remote side
     * (the latter implied by calling this method)
     *
     * This knowledge can be used for prefilling newly bridged room with history.
     *
     * @param id Remote room to check
     * @return True if room is bridged
     */
    public suspend fun isRoomBridged(id: ROOM): Boolean
}