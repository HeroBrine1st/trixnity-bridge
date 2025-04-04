package ru.herobrine1st.matrix.bridge.internal

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI
import ru.herobrine1st.matrix.bridge.repository.MessageRepository
import ru.herobrine1st.matrix.bridge.repository.PuppetRepository
import ru.herobrine1st.matrix.bridge.repository.RoomRepository

internal class RemoteWorkerAPIImpl<USER : Any, ROOM : Any, MESSAGE : Any>(
    val messageRepository: MessageRepository<MESSAGE>,
    val puppetRepository: PuppetRepository<USER>,
    val roomRepository: RoomRepository<*, ROOM>
) : RemoteWorkerAPI<USER, ROOM, MESSAGE> {
    override suspend fun getMessageEventId(id: MESSAGE) = messageRepository.getMessageEventId(id)

    override suspend fun getMessageEventId(id: EventId) = messageRepository.getMessageEventId(id)

    override suspend fun getPuppetId(id: USER) = puppetRepository.getPuppetId(id)

    override suspend fun getPuppetId(id: UserId) = puppetRepository.getPuppetId(id)

    override suspend fun getRoomId(id: ROOM): RoomId? = roomRepository.getMxRoom(id)

    override suspend fun getRoomId(id: RoomId): ROOM? = roomRepository.getRemoteRoom(id)

    override suspend fun isRoomBridged(id: ROOM) = roomRepository.isRoomBridged(id)

}