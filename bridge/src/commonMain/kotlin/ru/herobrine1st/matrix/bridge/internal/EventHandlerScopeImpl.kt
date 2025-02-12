package ru.herobrine1st.matrix.bridge.internal

import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId
import ru.herobrine1st.matrix.bridge.database.MessageRepository

internal class EventHandlerScopeImpl<USER : RemoteUserId, MESSAGE : RemoteMessageId>(
    val event: ClientEvent.RoomEvent<*>,
    private val messageRepository: MessageRepository<USER, MESSAGE>
) : EventHandlerScope<MESSAGE> {
    override suspend fun linkMessageId(id: MESSAGE) {
        messageRepository.createByMxAuthor(id, event.id)
    }
}