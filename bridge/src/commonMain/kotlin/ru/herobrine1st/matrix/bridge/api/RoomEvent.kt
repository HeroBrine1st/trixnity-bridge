package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId

public sealed interface RoomEvent<USER : Any, ROOM : Any, MESSAGE : Any> {
    /**
     * An id of remote room this event belongs to
     */
    public val roomId: ROOM

    /**
     * A unique id of this event. Value should be unique among all events from the same side.
     */
    public val eventId: RemoteEventId

    public data class MessageEvent<USER : Any, ROOM : Any, MESSAGE : Any>(
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

    public data class RoomCreation<USER : Any, ROOM : Any, MESSAGE : Any>(
        override val roomId: ROOM,
        override val eventId: RemoteEventId,
        override val roomData: RemoteRoom<ROOM, USER>? = null,
    ) : RoomEvent<USER, ROOM, MESSAGE>, RoomDataHolder<ROOM, USER>

    public data class RoomMember<USER : Any, ROOM : Any, MESSAGE : Any>(
        override val roomId: ROOM,
        override val eventId: RemoteEventId,
        val userId: USER,
        val membership: Membership,
        override val userData: RemoteUser<USER>? = null
    ) : RoomEvent<USER, ROOM, MESSAGE>, UserDataHolder<USER>
}