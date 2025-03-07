package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.api.RemoteWorker

/**
 * This interface is responsible for managing pairs of [MESSAGE] to [EventId], for usage in message relations.
 *
 * Repository MUST ensure one-to-one relation between messages and MUST throw exceptions if it is violated.
 */
public interface MessageRepository<MESSAGE : Any> {
    /**
     * This method registers pair [remoteMessageId]<->[mxEventId]. This can be used later by [RemoteWorker] to reference events on both sides.
     *
     * This method MUST be idempotent.
     */
    public suspend fun createRelation(remoteMessageId: MESSAGE, mxEventId: EventId)
}