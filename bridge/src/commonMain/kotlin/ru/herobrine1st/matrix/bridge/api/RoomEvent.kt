package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId
import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId

public sealed interface RoomEvent<USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId> {
    /**
     * An id of remote room this event belongs to
     */
    public val roomId: ROOM

    /**
     * A unique id of this event. Value should be unique among all events from the same side.
     */
    public val eventId: RemoteEventId

    public data class MessageEvent<USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId>(
        override val roomId: ROOM,
        override val eventId: RemoteEventId,
        val sender: USER,
        val content: MessageEventContent,
        /**
         * If not null, can later be used to reference matrix event by provided value.
         *
         * This value MUST be unique or null, even if replacing event
         *
         * @see ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI.getMessageEventId
         */
        val messageId: MESSAGE? = null,
    ) : RoomEvent<USER, ROOM, MESSAGE>

    public data class RoomCreation<USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId>(
        override val roomId: ROOM,
        override val eventId: RemoteEventId,
        override val roomData: RemoteRoom? = null
    ) : RoomEvent<USER, ROOM, MESSAGE>, RoomDataHolder

    public data class RoomMember<USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId>(
        override val roomId: ROOM,
        override val eventId: RemoteEventId,
        val userId: USER,
        val membership: Membership,
        override val userData: RemoteUser<USER>? = null
    ) : RoomEvent<USER, ROOM, MESSAGE>, UserDataHolder<USER>
}