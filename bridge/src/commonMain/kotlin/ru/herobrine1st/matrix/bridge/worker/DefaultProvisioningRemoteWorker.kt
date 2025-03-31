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
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.RemoteIdToMatrixMapper
import ru.herobrine1st.matrix.bridge.api.RemoteRoom
import ru.herobrine1st.matrix.bridge.api.RemoteUser
import ru.herobrine1st.matrix.bridge.api.worker.MappingRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.ProvisioningRemoteWorker
import ru.herobrine1st.matrix.bridge.config.BridgeConfig
import ru.herobrine1st.matrix.bridge.repository.*
import ru.herobrine1st.matrix.compat.content.ServiceMembersEventContent

public class DefaultProvisioningRemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any>(
    private val client: MatrixClientServerApiClient,
    private val mappingRemoteWorker: MappingRemoteWorker<ACTOR, USER, ROOM, MESSAGE>,
    idMapperFactory: RemoteIdToMatrixMapper.Factory<ROOM, USER>,
    bridgeConfig: BridgeConfig,
    repositorySet: RepositorySet<ACTOR, USER, ROOM, MESSAGE>
) : ProvisioningRemoteWorker<ACTOR, USER, ROOM, MESSAGE> {
    private val actorRepository: ActorRepository<ACTOR> = repositorySet.actorRepository
    private val messageRepository: MessageRepository<MESSAGE> = repositorySet.messageRepository
    private val puppetRepository: PuppetRepository<USER> = repositorySet.puppetRepository
    private val roomRepository: RoomRepository<ACTOR, ROOM> = repositorySet.roomRepository
    private val transactionRepository: TransactionRepository = repositorySet.transactionRepository

    private val appServiceBotId: UserId = UserId(bridgeConfig.botLocalpart, bridgeConfig.homeserverDomain)
    private val puppetPrefix = bridgeConfig.puppetPrefix
    private val roomAliasPrefix = bridgeConfig.roomAliasPrefix
    private val homeserverDomain = bridgeConfig.homeserverDomain

    private val idMapper = idMapperFactory.create(roomAliasPrefix, puppetPrefix, homeserverDomain)

    private val logger = KotlinLogging.logger { }

    override suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>
    ) {
        TODO("Not yet implemented")
    }

    override fun getEvents(actorId: ACTOR): Flow<ProvisioningRemoteWorker.Event<USER, ROOM, MESSAGE>> =
        mappingRemoteWorker.getEvents(actorId).transform {
            when (it) {
                is MappingRemoteWorker.Event.Remote.Room -> when (it) {
                    is MappingRemoteWorker.Event.Remote.Room.Create<ROOM> -> {
                        val roomId = replicateRemoteRoom(it.roomData, actorId, TODO())
                        emit(
                            ProvisioningRemoteWorker.Event.Remote.Room.Create(
                                roomId,
                                it.roomId,
                                it.eventId
                            )
                        )
                        clearRoomIdempotencyMarker(roomId)
                    }
                    // TODO update event
                    // TODO membership event
                    // TODO passthrough message events
                }

                is MappingRemoteWorker.Event.Remote.User -> when (it) {
                    is MappingRemoteWorker.Event.Remote.User.Create<USER> -> {
                        val userId = replicateRemoteUser(it.userData)
                        emit(
                            ProvisioningRemoteWorker.Event.Remote.User.Create(
                                userId,
                                it.userId,
                                it.eventId
                            )
                        )
                    }
                    // TODO update event
                }

                else -> TODO("Passthrough")
            }
            TODO()
        }


    private suspend fun replicateRemoteUser(
        userData: RemoteUser<USER>
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
        roomData: RemoteRoom<ROOM>,
        actorId: ACTOR,
        invitePuppet: UserId,
    ): RoomId {
        // TODO this method has too much responsibilities
        //  - Handles direct rooms fully by itself
        //  - Handles bridge bypasses fully by itself
        // the goal is to create room, handle direct invites but not joins (they should be handled by memberships)
        // there may be an issue if remote side supports adding members to direct rooms after creation. Matrix does not support that.
        // so probably list of parameters is:
        // - room data
        // - puppets to be invited (empty if not direct but check is not required), including local user
        // - the "creator" of the room
        // then history backfilling will join the puppets either when they join on remote side or instantly as remote side automatically accepts invites

        roomRepository.getMxRoom(roomData.id)?.let {
            return it
        }

        // Create room from scratch
        logger.trace { "Replicating remote room $roomData" }

        val correspondingLocalUser = actorRepository.getLocalUserIdForActor(actorId)
        require(correspondingLocalUser != invitePuppet) { "User $invitePuppet is used both as local user and as a puppet, this will lead to undefined behavior!" }
        if (roomData.isDirect && correspondingLocalUser == null) {
            logger.warn { "Found direct room $roomData but actor $actorId has no local user, is it intended?" }
        }

        val displayName = roomData.displayName

        if (displayName != null && roomData.isDirect)
            logger.warn { "Room $roomData is direct but has display name. Is it intended?" }

        // Alias overrides room name generation via heroes and should be avoided
        // TODO implement human-like flow (i.e. store that room with members <...>
        //      is attempted to be created, attempt, store in database, or find that it is already attempted,
        //      find the room (or create if none) and store in database)
        // It is also known as Canonical DM, but without HS support we fallback to this human-likeness

        val alias = idMapper.buildRoomAlias(roomData.id)
        val serviceMembersEvent = ServiceMembersEventContent(
            serviceMembers = listOfNotNull(
                appServiceBotId,
                // if room creation is triggered by bridge bypass
                // TODO in this case we also need to add the other heroes - which is not happening!
                actorRepository.getMxUserOfActorPuppet(actorId)?.takeIf { it == invitePuppet }
            )
        )
        val roomId = client.room.createRoom(
            name = displayName,
            roomAliasId = alias, // idempotency via stable alias
            // it is vital to invite correspondingLocalUser here as isDirect works only here
            invite = setOfNotNull(correspondingLocalUser, invitePuppet),
            initialState = listOf(
                // this "direct" room can have up to 4 members (actual user, actual puppet, sender for "sent successfully" via read marks and puppet of actual user for synchronisation cases)
                InitialStateEvent(
                    content = serviceMembersEvent,
                    stateKey = ""
                )
            ),
            // TODO do something with non-personal bridge creating rooms without ability to enter them (bot commands can be used)
            preset = CreateRoom.Request.Preset.PRIVATE,
            isDirect = roomData.isDirect
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

        // TODO handle by membership events
//        client.room.joinRoom( // probably idempotent
//            roomId,
//            asUserId = invitePuppet
//        ).recover {
//            if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
//                logger.warn { "Got uninvited puppet $invitePuppet after (probably) idempotency hit on room $roomId ($alias), inviting and joining" }
//                inviteAndJoinPuppetToRoom(
//                    roomId,
//                    invitePuppet,
//                    actorRepository.getMxUserOfActorPuppet(actorId) == invitePuppet
//                )
//            } else throw it
//        }.getOrThrow()

        // TODO handle it by membership events
//        if (remoteRoom.isDirect) coroutineScope {
//            remoteWorker.getRoomMembers(actorId, roomIdToReplicate)
//                .filter { it.first != invitePuppet }
//                .map {
//                    async {
//                        val userId = replicateRemoteUser(it.first, actorId, it.second)
//                        inviteAndJoinPuppetToRoom(
//                            mxRoomId = roomId,
//                            mxPuppetId = userId,
//                            asServiceUser = false // it is implied by contract on getRoomMembers
//                        )
//                    }
//                }
//                .buffer() // separate coroutines
//                .collect { it.await() }
//        }
        return roomId
    }

    private suspend fun clearRoomIdempotencyMarker(roomId: RoomId) {
        val aliasId = client.room.getStateEvent<CanonicalAliasEventContent>(roomId).getOrThrow().alias
        logger.debug { "Removing canonical alias ${aliasId.full} as room is added to database" }
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
}