package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.EventId

/**
 * This interface is responsible for managing pairs of [MESSAGE] to [EventId], for usage in message relations.
 *
 * Repository MUST ensure one-to-one relation between messages and MUST throw exceptions if it is violated.
 */
public interface MessageRepository<USER : Any, MESSAGE : Any> {
    /**
     * This method registers pair [remoteMessageId]<->[mxEventId] as sent by [sender].
     *
     * This method MUST be idempotent.
     */
    public suspend fun createByRemoteAuthor(remoteMessageId: MESSAGE, mxEventId: EventId)

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