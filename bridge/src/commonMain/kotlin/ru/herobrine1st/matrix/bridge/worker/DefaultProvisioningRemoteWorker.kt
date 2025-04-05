package ru.herobrine1st.matrix.bridge.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.getStateEvent
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.RemoteIdToMatrixMapper
import ru.herobrine1st.matrix.bridge.api.RemoteRoom
import ru.herobrine1st.matrix.bridge.api.RemoteUser
import ru.herobrine1st.matrix.bridge.api.worker.MappingRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.ProvisioningRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.ProvisioningRemoteWorker.Event.Remote.Room.Message
import ru.herobrine1st.matrix.bridge.config.BridgeConfig
import ru.herobrine1st.matrix.bridge.repository.PuppetRepository
import ru.herobrine1st.matrix.bridge.repository.RoomRepository
import ru.herobrine1st.matrix.compat.content.ServiceMembersEventContent

public class DefaultProvisioningRemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any>(
    private val client: MatrixClientServerApiClient,
    private val mappingRemoteWorker: MappingRemoteWorker<ACTOR, USER, ROOM, MESSAGE>,
    private val puppetRepository: PuppetRepository<USER>,
    private val roomRepository: RoomRepository<ACTOR, ROOM>,
    idMapperFactory: RemoteIdToMatrixMapper.Factory<ROOM, USER>,
    bridgeConfig: BridgeConfig,
) : ProvisioningRemoteWorker<ACTOR, USER, ROOM, MESSAGE> {
    private val appServiceBotId: UserId = UserId(bridgeConfig.botLocalpart, bridgeConfig.homeserverDomain)
    private val puppetPrefix = bridgeConfig.puppetPrefix
    private val roomAliasPrefix = bridgeConfig.roomAliasPrefix
    private val homeserverDomain = bridgeConfig.homeserverDomain

    private val idMapper = idMapperFactory.create(roomAliasPrefix, puppetPrefix, homeserverDomain)

    private val logger = KotlinLogging.logger { }

    override suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>,
    ) {
        with(mappingRemoteWorker) {
            handleEvent(actorId, roomId, event)
        }
    }

    override fun getEvents(actorId: ACTOR): Flow<ProvisioningRemoteWorker.Event<USER, ROOM, MESSAGE>> =
        mappingRemoteWorker.getEvents(actorId).transform { event ->
            when (event) {
                is MappingRemoteWorker.Event.Remote.Room -> when (event) {
                    is MappingRemoteWorker.Event.Remote.Room.Create<USER, ROOM> -> {
                        val roomId = replicateRemoteRoom(event.roomData)
                        emit(
                            ProvisioningRemoteWorker.Event.Remote.Room.Create(
                                roomId,
                                event.roomId
                            )
                        )
                        clearRoomIdempotencyMarker(roomId)
                    }
                    // TODO update event
                    is MappingRemoteWorker.Event.Remote.Room.Membership<USER, ROOM> -> handleMembershipEvent(event)
                    is MappingRemoteWorker.Event.Remote.Room.Message<USER, ROOM, MESSAGE> -> emit(Message(event.messageData))
                }

                is MappingRemoteWorker.Event.Remote.User -> when (event) {
                    is MappingRemoteWorker.Event.Remote.User.Create<USER> -> emit(
                        ProvisioningRemoteWorker.Event.Remote.User.Create(
                            mxUserId = replicateRemoteUser(event.userData),
                            event.userId
                        )
                    )
                    // TODO update event
                }

                MappingRemoteWorker.Event.Connected -> emit(ProvisioningRemoteWorker.Event.Connected)
                is MappingRemoteWorker.Event.Disconnected -> emit(ProvisioningRemoteWorker.Event.Disconnected(event.reason))
                is MappingRemoteWorker.Event.FatalFailure -> emit(ProvisioningRemoteWorker.Event.FatalFailure(event.reason))
            }
        }


    private suspend fun replicateRemoteUser(
        userData: RemoteUser<USER>,
    ): UserId {
        puppetRepository.getPuppetId(userData.id)?.let {
            return@replicateRemoteUser it
        }

        val username = idMapper.buildPuppetUserId(userData.id).localpart

        val puppetId = client.authentication.register(
            username = username,
            isAppservice = true
        ).map {
            if (it !is UIA.Success<Register.Response>) {
                logger.error { "Unexpected UIA step occurred while creating puppet: $it" }
                error("This should never happen: unexpected UIA")
            }
            it.value.userId
        }.recover {
            if (it is MatrixServerException && it.errorResponse is ErrorResponse.UserInUse) {
                logger.debug(it) { "Got idempotency hit for username ${username}, reusing" }
                return@recover UserId(
                    username,
                    homeserverDomain
                )
            } else throw it
        }.getOrThrow()

        // Initialize this replica before sending to database for recovery purposes
        val remoteUserData = userData

        client.user.setDisplayName(
            puppetId,
            displayName = remoteUserData.displayName,
            asUserId = puppetId
        ).getOrThrow()

        return puppetId
    }

    private suspend fun replicateRemoteRoom(
        roomData: RemoteRoom<USER, ROOM>,
    ): RoomId {
        roomRepository.getMxRoom(roomData.id)?.let {
            return it
        }

        logger.trace { "Replicating remote room $roomData" }


        val displayName = roomData.displayName

        // Alias overrides room name generation via heroes and should be avoided
        // TODO implement human-like flow (i.e. store that room with members <...>
        //      is attempted to be created, attempt, store in database, or find that it is already attempted,
        //      find the room (or create if none) and store in database)
        // It is also known as Canonical DM, but without HS support we fallback to this human-likeness

        val alias = idMapper.buildRoomAlias(roomData.id)
        val creator = roomData.creator?.let { puppetRepository.getPuppetId(it) } ?: appServiceBotId

        val roomId = client.room.createRoom(
            name = displayName,
            roomAliasId = alias, // idempotency via stable alias

            // TODO add a way to provide actor admin here, but it should be done in a way that allows delegation
            //      (e.g. allow anyone to bridge any room of choice)
            // I think the best method is to get an admin for the replicated room
            // This bridge can be aware of that via e.g. registration - you simply write the bot about the account on the other side and verify that via code there.
            // Then lower layer can add admin data, we fetch the pair here and voi la.
            // This has a number of limitations:
            // - some direct rooms do not have an admin (e.g. 1-1 chats if this bridge is personal). This can be emulated via "everyone is admin", which will invite the real matrix user as well as add admin rights to the other peer
            invite = (roomData.directData?.members?.mapTo(mutableSetOf()) {
                // SAFETY: It is guaranteed by MappingRemoteWorker that all members are already provisioned
                puppetRepository.getPuppetId(it)!!
            } ?: emptySet()) + appServiceBotId - creator,
            initialState = listOf(
                InitialStateEvent(
                    content = ServiceMembersEventContent(
                        serviceMembers = listOf(appServiceBotId)
                    ),
                    stateKey = ""
                )
            ),
            powerLevelContentOverride = PowerLevelsEventContent(users = buildMap {
                if (creator != appServiceBotId) set(appServiceBotId, 100L)
            }),
            preset = CreateRoom.Request.Preset.PRIVATE,
            isDirect = roomData.directData != null,
            asUserId = creator
        ).recover {
            if (it !is MatrixServerException || it.errorResponse !is ErrorResponse.RoomInUse) throw it
            logger.debug(it) { "Got idempotency hit for alias ${alias.full}, fetching and reusing existing room" }
            val roomId = client.room.getRoomAlias(alias).getOrThrow().roomId
            if (logger.isTraceEnabled()) {
                client.room.getState(roomId, asUserId = appServiceBotId).onSuccess { state ->
                    logger.trace { "Listing state of that room: " + state.joinToString("\n", prefix = "\n") }
                }
            }

            val members = client.room.getJoinedMembers(roomId).getOrThrow().joined.keys
            if (appServiceBotId !in members) {
                error { "Room ${alias.full} has no appservice bot! It probably needs to be migrated" }
            }
            roomId
        }.getOrThrow()

        return roomId
    }

    private suspend fun clearRoomIdempotencyMarker(roomId: RoomId) {
        val aliasId = client.room.getStateEvent<CanonicalAliasEventContent>(roomId).getOrThrow().alias
        logger.debug { "Removing canonical alias ${aliasId?.full} as room is added to database" }
        if (aliasId != null) client.room.deleteRoomAlias(aliasId) // this actually removes alias, but it leaves event in room
            .getOrThrow()
        client.room.sendStateEvent(roomId, CanonicalAliasEventContent())
            .getOrThrow() // this hides alias from users, but does not remove it
    }

    private suspend fun inviteAndJoinPuppetToRoom(mxRoomId: RoomId, mxPuppetId: UserId, asServiceUser: Boolean) {
        client.room.inviteUser(
            mxRoomId,
            mxPuppetId,
            reason = "Falling back to automatic join",
            asUserId = appServiceBotId // bot is joined in every bridged room
        ).onFailure {
            if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
                val members = client.room.getJoinedMembers(mxRoomId).getOrThrow().joined.keys
                if (mxPuppetId in members) {
                    logger.warn(it) { "Tried to invite already joined user $mxPuppetId to $mxRoomId, ignoring" }
                    return
                } else throw it
            } else throw it
        }
            .getOrThrow()

        if (asServiceUser) {
            val newContent = client.room.getStateEvent<ServiceMembersEventContent>(mxRoomId)
                .recover {
                    // TODO verify that it is "NotFound"
                    if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) {
                        ServiceMembersEventContent(
                            serviceMembers = listOf(appServiceBotId)
                        )
                    } else throw it
                }.map {
                    it.copy(serviceMembers = it.serviceMembers + mxPuppetId)
                }.getOrThrow()
            client.room.sendStateEvent(mxRoomId, newContent).getOrThrow()
        }

        // probably idempotent
        client.room.joinRoom(
            mxRoomId,
            reason = "Falling back to automatic join",
            asUserId = mxPuppetId
        ).getOrThrow()
    }

    private suspend fun handleMembershipEvent(event: MappingRemoteWorker.Event.Remote.Room.Membership<USER, ROOM>) {
        // SAFETY: It is guaranteed by MappingRemoteWorker that room and users are already provisioned
        val roomId = roomRepository.getMxRoom(event.roomId)!!
        val stateKey = puppetRepository.getPuppetId(event.stateKey)!!
        val sender = when (event.sender) {
            event.stateKey -> stateKey
            // FIXME bot join is asynchronous, this may lead to race condition
            null -> appServiceBotId
            else -> puppetRepository.getPuppetId(event.sender)!!
        }

        when (event.membership) {
            Membership.JOIN -> client.room.joinRoom(roomId, asUserId = stateKey).getOrThrow()

            // spec says it is only join where stateKey should be equal to sender.
            // It is not reasonable to allow knocking on behalf of someone else
            // FIXME find clarification
            Membership.KNOCK -> client.room.knockRoom(roomId, asUserId = stateKey).getOrThrow()

            Membership.LEAVE if event.sender == event.stateKey -> client.room.leaveRoom(
                roomId,
                asUserId = stateKey
            ).getOrThrow()

            Membership.LEAVE -> client.room.kickUser(
                roomId,
                stateKey,
                asUserId = sender
            ).getOrThrow()

            Membership.INVITE -> client.room.inviteUser(
                roomId,
                stateKey,
                asUserId = sender
            ).onFailure {
                if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
                    val members = client.room.getJoinedMembers(roomId).getOrThrow().joined.keys
                    if (stateKey in members) {
                        logger.warn(it) { "Tried to invite already joined user $stateKey to $roomId, ignoring" }
                        Unit
                    } else throw it
                } else throw it
            }.getOrThrow()

            Membership.BAN -> client.room.banUser(
                roomId,
                stateKey,
                asUserId = sender
            )
        }
    }
}