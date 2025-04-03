package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.events.MessageEventContent
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId

public data class RemoteMessageEventData<USER : Any, ROOM : Any, MESSAGE : Any>(
    override val roomId: ROOM,
    override val eventId: RemoteEventId,
    override val sender: USER,
    override val content: MessageEventContent,
    override val messageId: MESSAGE? = null,
) : IRemoteMessageEventData<USER, ROOM, MESSAGE>

public interface IRemoteMessageEventData<USER : Any, ROOM : Any, MESSAGE : Any> {
    public val roomId: ROOM
    public val eventId: RemoteEventId
    public val sender: USER
    public val content: MessageEventContent

    /**
     * If not null, can later be used to reference matrix event by provided value.
     *
     * This value MUST be unique or null, even if replacing event
     *
     * @see ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI.getMessageEventId
     */
    public val messageId: MESSAGE?
}
