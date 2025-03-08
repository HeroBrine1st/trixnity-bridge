package ru.herobrine1st.matrix.bridge.internal

import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.repository.MessageRepository

internal class EventHandlerScopeImpl<MESSAGE : Any>(
    val event: ClientEvent.RoomEvent<*>,
    private val messageRepository: MessageRepository<MESSAGE>
) : EventHandlerScope<MESSAGE> {
    override suspend fun linkMessageId(id: MESSAGE) {
        messageRepository.createRelation(id, event.id)
    }
}