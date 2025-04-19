package ru.herobrine1st.matrix.bridge.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.transform
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.Membership
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.RemoteMessageEventData
import ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI
import ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.MappingRemoteWorker
import ru.herobrine1st.matrix.bridge.repository.PuppetRepository
import ru.herobrine1st.matrix.bridge.repository.RoomRepository

public class DefaultMappingRemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any>(
    private val client: MatrixClientServerApiClient,
    private val puppetRepository: PuppetRepository<USER>,
    private val roomRepository: RoomRepository<ACTOR, ROOM>,
    api: RemoteWorkerAPI<USER, ROOM, MESSAGE>,
    remoteWorkerFactory: BasicRemoteWorker.Factory<ACTOR, USER, ROOM, MESSAGE>,
) : MappingRemoteWorker<ACTOR, USER, ROOM, MESSAGE> {
    private val remoteWorker = remoteWorkerFactory.getRemoteWorker(api)
    private val logger = KotlinLogging.logger { }

    override suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>,
    ) {
        with(remoteWorker) {
            handleEvent(actorId, roomId, event)
        }
    }

    override fun getEvents(actorId: ACTOR): Flow<MappingRemoteWorker.Event<USER, ROOM, MESSAGE>> =
        remoteWorker.getEvents(actorId).transform { event ->
            when (event) {
                BasicRemoteWorker.Event.Connected -> emit(MappingRemoteWorker.Event.Connected)
                is BasicRemoteWorker.Event.Disconnected -> emit(MappingRemoteWorker.Event.Disconnected(event.reason))
                is BasicRemoteWorker.Event.FatalFailure -> emit(MappingRemoteWorker.Event.FatalFailure(event.reason))
                is BasicRemoteWorker.Event.Remote -> when (event) {
                    is BasicRemoteWorker.Event.Remote.Room.Create -> emit(
                        MappingRemoteWorker.Event.Remote.Room.Create(
                            roomData = event.roomData ?: remoteWorker.getRoom(actorId, event.roomId)
                                .also { check(it.id == event.roomId) }
                        )
                    )

                    is BasicRemoteWorker.Event.Remote.Room.Membership -> {
                        ensureRoomExists(actorId, event)
                        ensureUserExists(actorId, event.stateKey)
                        event.sender?.let {
                            ensureUserExists(actorId, it)
                            // there's no guarantee about event.sender membership
                            if (it != event.stateKey) ensureUserJoined(event.roomId, it)
                        }

                        emit(
                            MappingRemoteWorker.Event.Remote.Room.Membership(
                                event.roomId, event.sender, event.stateKey, event.membership
                            )
                        )
                    }

                    is BasicRemoteWorker.Event.Remote.Room.Message -> {
                        ensureRoomExists(actorId, event)
                        ensureUserExists(actorId, event.sender)
                        ensureUserJoined(event.roomId, event.sender)

                        emit(
                            MappingRemoteWorker.Event.Remote.Room.Message(
                                RemoteMessageEventData(
                                    roomId = event.roomId,
                                    eventId = event.eventId,
                                    sender = event.sender,
                                    content = event.content,
                                    messageId = event.messageId
                                )
                            )
                        )
                    }

                    is BasicRemoteWorker.Event.Remote.Room.RealUserMembership<USER, ROOM> -> emit(
                        MappingRemoteWorker.Event.Remote.Room.RealUserMembership(
                            roomId = event.roomId,
                            sender = event.sender,
                            stateKey = event.stateKey,
                            membership = event.membership
                        )
                    )
                }
            }
        }

    private suspend fun FlowCollector<MappingRemoteWorker.Event<USER, ROOM, MESSAGE>>.ensureRoomExists(
        actorId: ACTOR,
        event: BasicRemoteWorker.Event.Remote.Room<USER, ROOM, MESSAGE>,
    ) {
        if (!roomRepository.isRoomBridged(event.roomId)) {
            logger.debug { "Emitting automatic room ${event.roomId} provision request" }
            emit(
                MappingRemoteWorker.Event.Remote.Room.Create(
                    roomData = remoteWorker.getRoom(actorId, event.roomId)
                        .also { check(it.id == event.roomId) }
                )
            )
        }
    }

    private suspend fun FlowCollector<MappingRemoteWorker.Event<USER, ROOM, MESSAGE>>.ensureUserExists(
        actorId: ACTOR,
        user: USER,
    ) {
        if (puppetRepository.getPuppetId(user) == null) {
            logger.debug { "Emitting automatic user $user provision request" }
            emit(
                MappingRemoteWorker.Event.Remote.User.Create(
                    userData = remoteWorker.getUser(actorId, user)
                )
            )
        }
    }

    private suspend fun FlowCollector<MappingRemoteWorker.Event<USER, ROOM, MESSAGE>>.ensureUserJoined(
        room: ROOM,
        user: USER,
    ) {
        val roomId = roomRepository.getMxRoom(room)!! // SAFETY: internally guaranteed by ensureRoomExists
        val userId = puppetRepository.getPuppetId(user)!! // SAFETY: internally guaranteed by ensureUserExists
        val members = client.room.getJoinedMembers(roomId).getOrThrow()
        if (userId !in members.joined) {
            logger.debug { "Emitting automatic user $user invite and join request" }
            emit(
                MappingRemoteWorker.Event.Remote.Room.Membership(
                    roomId = room,
                    sender = null,
                    stateKey = user,
                    membership = Membership.INVITE
                )
            )
            emit(
                MappingRemoteWorker.Event.Remote.Room.Membership(
                    roomId = room,
                    sender = user,
                    stateKey = user,
                    membership = Membership.JOIN
                )
            )
        }
    }
}