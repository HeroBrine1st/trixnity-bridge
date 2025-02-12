package ru.herobrine1st.matrix.bridge.api

import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId

public interface EventHandlerScope<MESSAGE : RemoteMessageId> {
    /**
     * Adds [id]<->[ClientEvent.RoomEvent.id] mapping to internal database
     *
     * @return [Unit] if successful
     */
    public suspend fun linkMessageId(id: MESSAGE)
}