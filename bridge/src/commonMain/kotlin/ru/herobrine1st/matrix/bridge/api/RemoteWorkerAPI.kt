package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.api.RoomEvent.MessageEvent
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId
import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId

public interface RemoteWorkerAPI<USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId> {
    /**
     * Provides a way to get internal mapping of event IDs.
     *
     * @param id The same value as was previously used in [MessageEvent.messageId]
     * @return [EventId] of the same event on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: MESSAGE): EventId?

    /**
     * Provides a way to get internal mapping of event IDs.
     *
     * @param id The matrix event ID
     * @return [RemoteMessageId] of the same message on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: EventId): MESSAGE?

    /**
     * This method returns author of message, or null if there's no record.
     *
     * This method MUST return null if message author is not a puppet.
     *
     * @param id The same value as was previously used in [MessageEvent.messageId]
     * @return [ru.herobrine1st.matrix.bridge.api.value.RemoteUserId] of author of this message, or null
     */
    public suspend fun getMessageAuthor(id: MESSAGE): USER?

    /**
     * @param id remote ID of puppet
     * @return matrix id of the same puppet or null if not found
     */
    // TODO replicate if there's no such user
    public suspend fun getPuppetId(id: USER): UserId?

    /**
     * @param id matrix ID of puppet
     * @return remote id of the same puppet or null if not found
     */
    public suspend fun getPuppetId(id: UserId): USER?


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