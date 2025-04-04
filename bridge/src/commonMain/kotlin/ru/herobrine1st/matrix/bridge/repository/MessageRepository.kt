package ru.herobrine1st.matrix.bridge.repository

import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker

/**
 * This interface is responsible for managing pairs of [MESSAGE] to [EventId], for usage in message relations.
 *
 * Repository MUST ensure one-to-one relation between message IDs and MUST throw exceptions if it is violated.
 */
public interface MessageRepository<MESSAGE : Any> {
    /**
     * This method registers pair [remoteMessageId]<->[mxEventId]. This can be used later by [RemoteWorker] to reference events on both sides.
     *
     * This method MUST be idempotent.
     */
    public suspend fun createRelation(remoteMessageId: MESSAGE, mxEventId: EventId)

    /**
     * Provides a way to get mapping of event IDs.
     *
     * @param id remote ID of message
     * @return [EventId] of the same event on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: MESSAGE): EventId?

    /**
     * Provides a way to get mapping of event IDs.
     *
     * @param id the matrix event ID
     * @return [MESSAGE] of the same message on local side, or null if there's no record
     */
    public suspend fun getMessageEventId(id: EventId): MESSAGE?
}