package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId

/**
 * This interface is responsible for managing pairs of [RemoteMessageId] to [EventId], for usage in message relations.
 *
 * Repository MUST ensure one-to-one relation between messages and MUST throw exceptions if it is violated.
 */
// TODO this abstraction is dependent on specific implementation and should be reconsidered
public interface MessageRepository<USER : RemoteUserId, MESSAGE : RemoteMessageId> {
    /**
     * This method registers pair [remoteMessageId]<->[mxEventId] as sent by [sender].
     *
     * This method MUST be idempotent.
     */
    // TODO sender can be fetched from homeserver to reduce state hoarding
    public suspend fun createByRemoteAuthor(remoteMessageId: MESSAGE, mxEventId: EventId, sender: USER)

    /**
     * This method registers pair [remoteMessageId]<->[mxEventId] as sent by real user on mx side (i.e. null).
     *
     * This is because author of event is required only when it needs to replicate a redaction
     * from remote side to mx side, but other way it works flawless. Another reason is because sender is not registered
     * as puppet because it is not a puppet but a real user.
     *
     * This method MUST be idempotent.
     */
    public suspend fun createByMxAuthor(remoteMessageId: MESSAGE, mxEventId: EventId)
}