package ru.herobrine1st.matrix.bridge.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.applicationserviceapi.server.ApplicationServiceApiServerHandler
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.getStateEvent
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.*
import ru.herobrine1st.matrix.bridge.api.ErrorNotifier
import ru.herobrine1st.matrix.bridge.api.RemoteIdToMatrixMapper
import ru.herobrine1st.matrix.bridge.api.RemoteUser
import ru.herobrine1st.matrix.bridge.api.RemoteWorkerFactory
import ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker
import ru.herobrine1st.matrix.bridge.config.BridgeConfig
import ru.herobrine1st.matrix.bridge.exception.EventHandlingException
import ru.herobrine1st.matrix.bridge.exception.NoSuchActorException
import ru.herobrine1st.matrix.bridge.exception.UnhandledEventException
import ru.herobrine1st.matrix.bridge.internal.EventHandlerScopeImpl
import ru.herobrine1st.matrix.bridge.internal.RemoteWorkerAPIImpl
import ru.herobrine1st.matrix.bridge.internal.RemoteWorkerFatalFailureException
import ru.herobrine1st.matrix.bridge.repository.*
import ru.herobrine1st.matrix.compat.content.ServiceMembersEventContent
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

private const val DELAY_ON_ERROR_SECONDS = 1
private const val DELAY_EXPONENTIAL_BACKOFF_COEFFICIENT = 2.0

public class AppServiceWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any>(
    applicationJob: Job,
    private val client: MatrixClientServerApiClient,
    remoteWorkerFactory: RemoteWorkerFactory<ACTOR, USER, ROOM, MESSAGE>,
    repositorySet: RepositorySet<ACTOR, USER, ROOM, MESSAGE>,
    idMapperFactory: RemoteIdToMatrixMapper.Factory<ROOM, USER>,
    bridgeConfig: BridgeConfig,
    private val errorNotifier: ErrorNotifier = ErrorNotifier { _, _, _ -> },
) : ApplicationServiceApiServerHandler {

    private val actorRepository: ActorRepository<ACTOR> = repositorySet.actorRepository
    private val messageRepository: MessageRepository<MESSAGE> = repositorySet.messageRepository
    private val puppetRepository: PuppetRepository<USER> = repositorySet.puppetRepository
    private val roomRepository: RoomRepository<ACTOR, ROOM> = repositorySet.roomRepository
    private val transactionRepository: TransactionRepository = repositorySet.transactionRepository

    private val appServiceBotId: UserId = UserId(bridgeConfig.botLocalpart, bridgeConfig.homeserverDomain)
    private val puppetPrefix = bridgeConfig.puppetPrefix
    private val roomAliasPrefix = bridgeConfig.roomAliasPrefix
    private val homeserverDomain = bridgeConfig.homeserverDomain
    private val whitelist = bridgeConfig.provisioning.whitelist
    private val blacklist = bridgeConfig.provisioning.blacklist

    private val idMapper = idMapperFactory.create(roomAliasPrefix, puppetPrefix, homeserverDomain)

    private val remoteWorker: RemoteWorker<ACTOR, USER, ROOM, MESSAGE> = remoteWorkerFactory.getRemoteWorker(
        RemoteWorkerAPIImpl(messageRepository, puppetRepository, roomRepository)
    )

    // FIXME apparently applicationJob is SupervisorJob
    // errors render bridge non-functional but do not kill process
    private val coroutineScope = CoroutineScope(Job(parent = applicationJob) + Dispatchers.Default)
    private val logger = KotlinLogging.logger { }

    init {
        logger.info { "Using application job $applicationJob" }
    }

    override suspend fun addTransaction(
        txnId: String,
        events: List<ClientEvent.RoomEvent<*>>
    ) {
        if (events.isEmpty()) return // Short-circuit, don't even bother processing

        if (transactionRepository.isTransactionProcessed(txnId)) return
        logger.debug { "Received transaction $txnId" }
        logger.trace { events.joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.toString() } }

        val handledEvents = transactionRepository.getHandledEventsInTransaction(txnId)

        events.forEach { event ->
            event.content.let { content ->
                // Join to every invited room
                if (event !is ClientEvent.RoomEvent.StateEvent) return@let
                if (event.stateKey != appServiceBotId.toString()) return@let
                if (content !is MemberEventContent) return@let
                if (content.membership != Membership.INVITE) return@let
                client.room.joinRoom(event.roomId, asUserId = appServiceBotId).getOrThrow()
                return@forEach
            }
            if (event.sender.isBridgeControlled()) {
                return@forEach
            }

            if (event.id in handledEvents) return@forEach
            if (blacklist.any { it.matches(event.sender.full) }) return@forEach
            if (whitelist.isNotEmpty() && whitelist.none { it.matches(event.sender.full) }) return@forEach

            val remoteRoomId = roomRepository.getRemoteRoom(event.roomId) ?: run {
                // TODO handle it here, it is either a command to bridge bot or new room that needs to be bridged
                logger.warn { "Got event from unbridged room: $event" }
                // probably it is event to our $appServiceBotId
                return@forEach
            }
            // TODO it is possible that suitable actor presence is implied by remoteRoomId being not null
            val actorId = actorRepository.getActorIdByEvent(event) ?: return@forEach

            val scope = EventHandlerScopeImpl(event, messageRepository)

            with(remoteWorker) {
                try {
                    scope.handleEvent(actorId, remoteRoomId, event)
                } catch (e: EventHandlingException) {
                    logger.error(e) { "An error occurred while handling event" }
                    logger.trace { "Event: $event" }
                    when (e) {
                        is UnhandledEventException -> {
                            client.room.sendMessageEvent(
                                event.roomId,
                                RoomMessageEventContent.TextBased.Notice(
                                    body = "This event is not handled due to error: ${e.message}",
                                    relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(event.id))
                                ),
                                asUserId = appServiceBotId
                            ).onFailure {
                                // Every room must have a bridge bot
                                logger.error(it) { "An error occurred while notifying users about exception" }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errorNotifier.notify("An error occurred while handling event $event", t, true)
                    throw t
                }
            }
            transactionRepository.onEventHandled(txnId, event.id)
        }

        transactionRepository.onTransactionProcessed(txnId)
    }

    override suspend fun hasRoomAlias(roomAlias: RoomAliasId) {

    }

    override suspend fun hasUser(userId: UserId) {

    }

    override suspend fun ping(txnId: String?) {
        // Do nothing: spec only requires 200 status
        logger.info { "Got homeserver ping: $txnId" }
    }

    // TODO use ktor lifecycle (https://ktor.io/docs/server-events.html)
    // (this worker can also be transformed to a ktor plugin)
    public fun startup() {
        subscribeToRemoteSide()
    }

    public suspend fun createAppServiceBot() {
        var delay = DELAY_ON_ERROR_SECONDS.seconds
        while (true) {
            client.user.getProfile(appServiceBotId)
                .onSuccess {
                    logger.info { "Skipped creating $appServiceBotId bot" }
                    return // User exist
                }
                .onFailure {
                    if (it is MatrixServerException && (it.statusCode == HttpStatusCode.NotFound || it.statusCode == HttpStatusCode.Forbidden)) {
                        // Try to create
                        client.authentication.register(
                            username = appServiceBotId.localpart,
                            isAppservice = true
                        ).onSuccess { response ->
                            if (response !is UIA.Success) {
                                logger.error { "Couldn't create $appServiceBotId bot due to UIA error: $response" }
                                // Do not return, block application instead
                            } else {
                                logger.info { "Created $appServiceBotId bot" }
                                return // User exists now
                            }
                        }.onFailure { throwable ->
                            logger.error(throwable) { "Couldn't create $appServiceBotId bot" }
                        }
                    } else {
                        logger.error(it) { "Couldn't check bridge bot user existence" }
                    }
                }
            logger.info { "Retrying in $delay" }
            delay(delay)
            delay *= DELAY_EXPONENTIAL_BACKOFF_COEFFICIENT
        }
    }


    private fun subscribeToRemoteSide() {
        val terminated = mutableSetOf<ACTOR>() // actors that are in fatal failure
        val jobs = mutableMapOf<ACTOR, Job>()
        actorRepository.getActorsFlow().onEach { actors ->
            // Subscribe to new actors, preserve subscription to currently existing and cancel subscription to dropped actors
            actors.forEach { actorId ->
                if (actorId in terminated) return@forEach
                if (actorId in jobs) return@forEach
                jobs[actorId] = flow {
                    while (true) {
                        emitAll(remoteWorker.getEvents(actorId))
                        logger.warn { "RemoteWorker events flow completed, restarting" }
                    }
                }
                    .retryWhen { cause, attempt ->
                        if (attempt == 0L) {
                            emit(RemoteWorker.Event.Disconnected(cause.toString()))
                        }
                        errorNotifier.notify("An error occurred in RemoteWorker", cause, true)
                        logger.error(cause) { "An error occurred in RemoteWorker" }
                        val delay =
                            DELAY_ON_ERROR_SECONDS.seconds * DELAY_EXPONENTIAL_BACKOFF_COEFFICIENT.pow(attempt.toInt())
                        logger.error { "Delaying for $delay" }
                        delay(delay)
                        true
                    }
                    .onEach {
                        if (it is RemoteWorker.Event.FatalFailure) {
                            errorNotifier.notify("Got $it from $actorId", null, false)
                            logger.error { "An irrecoverable error occurred in RemoteWorker: $it" }
                            logger.error { "Stopping subscription now" }
                            terminated += actorId
                            throw RemoteWorkerFatalFailureException()
                        }
                    }
                    .onEach { event ->
                        handleWorkerEvent(actorId, event)
                    }
                    .retryWhen { cause, attempt ->
                        if (cause is RemoteWorkerFatalFailureException) {
                            false
                        } else {
                            errorNotifier.notify("Got internal error on $actorId", cause, true)
                            val delay =
                                DELAY_ON_ERROR_SECONDS.seconds * DELAY_EXPONENTIAL_BACKOFF_COEFFICIENT.pow(attempt.toInt())
                            logger.error(cause) { "Got internal error on $actorId, retrying after $delay" }
                            delay(delay)
                            true
                        }
                    }
                    .catch {
                        if (it !is RemoteWorkerFatalFailureException) throw it
                    }
                    .launchIn(coroutineScope)
            }
            (jobs.keys - actors).forEach {
                jobs.remove(it)?.cancel()
            }
        }.launchIn(coroutineScope)

    }

    private suspend fun handleWorkerEvent(actorId: ACTOR, event: RemoteWorker.Event<USER, ROOM, MESSAGE>) = try {
        when (event) {
            RemoteWorker.Event.Connected -> {}
            is RemoteWorker.Event.Disconnected -> {}
            is RemoteWorker.Event.FatalFailure -> {}
            is RemoteWorker.Event.Remote -> handleRemoteEvent(actorId, event)
        }
    } catch (e: NoSuchActorException) {
        logger.warn(e) { "Actor $actorId is not registered but returned an event. Ignoring." }
        errorNotifier.notify("Actor $actorId is not registered but returned an event. Ignoring.", e, false)
    }

    private suspend fun handleRemoteEvent(actorId: ACTOR, remoteEvent: RemoteWorker.Event.Remote<USER, ROOM, MESSAGE>) {
        when (val event = remoteEvent) {
            is RemoteWorker.Event.Remote.Room.Message -> {
                val mxPuppetId = replicateRemoteUser(
                    userIdToReplicate = event.sender,
                    actorId = actorId
                )
                val mxRoomId = replicateRemoteRoom(
                    roomIdToReplicate = event.roomId,
                    actorId = actorId,
                    invitePuppet = mxPuppetId
                )

                val eventId = client.room.sendMessageEvent(
                    mxRoomId,
                    eventContent = event.content,
                    txnId = event.eventId.value,
                    asUserId = mxPuppetId
                ).recover {
                    if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
                        inviteAndJoinPuppetToRoom(
                            mxRoomId,
                            mxPuppetId,
                            actorRepository.getMxUserOfActorPuppet(actorId) == mxPuppetId
                        )
                        return@recover client.room.sendMessageEvent(
                            mxRoomId,
                            eventContent = event.content,
                            txnId = event.eventId.value,
                            asUserId = mxPuppetId
                        ).getOrThrow()
                    } else throw it
                }.getOrThrow()

                if (event.messageId != null) {
                    messageRepository.createRelation(event.messageId, eventId)
                }
            }

            is RemoteWorker.Event.Remote.Room.Create -> TODO()
            is RemoteWorker.Event.Remote.Room.Membership -> TODO()
        }
    }

    private suspend fun replicateRemoteUser(
        userIdToReplicate: USER,
        actorId: ACTOR,
        userData: RemoteUser<USER>? = null
    ): UserId {
        puppetRepository.getPuppetId(userIdToReplicate)?.let {
            return@replicateRemoteUser it
        }


        val username = idMapper.buildPuppetUserId(userIdToReplicate).localpart

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
        val remoteUserData = userData ?: remoteWorker.getUser(actorId, userIdToReplicate)
        check(remoteUserData.id == userIdToReplicate) { "User data $remoteUserData does not correspond to user $userIdToReplicate! (userData=$userData)" }

        client.user.setDisplayName(
            puppetId,
            displayName = remoteUserData.displayName,
            asUserId = puppetId
        ).getOrThrow()

        puppetRepository.createPuppet(
            mxId = puppetId,
            remoteId = userIdToReplicate
        )

        return puppetId
    }

    private suspend fun replicateRemoteRoom(
        roomIdToReplicate: ROOM,
        actorId: ACTOR,
        invitePuppet: UserId,
    ): RoomId {
        roomRepository.getMxRoom(roomIdToReplicate)?.let {
            return it
        }

        // TODO this method only handles unmanaged rooms when RemoteWorker does not send room creation events
        //      it should be generalised to handle cases when RemoteWorker is actually aware about new rooms
        // The fact that room is created by appServiceBotId is the marker that room is provisioned automatically
        // it is also possible to require RemoteWorker to send room creation events, eliminating necessity of automatic provision

        // new idea: it is definitely possible to disable all automatic provisions.
        // Then, RemoteWorker will have only two methods: handleEvent and getEvents. All user and provision is done via getEvents
        // using corresponding events.
        // But automatic provision is useful, so it is possible to create a middle layer that implements RemoteWorker interface
        // and delegates everything to actual remote worker. This middle layer is aware if given room is unbridged and given user
        // is not provisioned. It can use that to query actual remote worker and get required information from it,
        // and then to create events like RoomCreation automatically, together with sending actual event directly after.

        // new_new idea: room creation and idempotency code can itself be extracted
        // this leads to this worker not handling room or user creation, so that it only uses already provisioned entities

        // Create room from scratch
        val remoteRoom = remoteWorker.getRoom(actorId, roomIdToReplicate)
        logger.trace { "Replicating remote root $roomIdToReplicate using data $remoteRoom" }
        val correspondingLocalUser = actorRepository.getLocalUserIdForActor(actorId)
        require(correspondingLocalUser != invitePuppet) { "User $invitePuppet is used both as local user and as a puppet, this will lead to undefined behavior!" }
        if (remoteRoom.isDirect && correspondingLocalUser == null) {
            logger.warn { "Found direct room $roomIdToReplicate but actor $actorId has no local user, is it intended?" }
        }

        val displayName = remoteRoom.displayName

        if (displayName != null && remoteRoom.isDirect)
            logger.warn { "Room $roomIdToReplicate is direct but has display name. Is it intended?" }

        // TODO due to alias being preferred to name generated from room heroes
        //      and invite with isDirect=true being possible only on room creation
        //      this approach is flawed
        //      This bridge should not use aliases for idempotent room creation
        //      This bridge should check that server does not have a room between invitePuppet (which should be enforced
        //      not to be actor account puppet) and correspondingLocalUser
        //      This eliminates aliases and local room repository (though it is needed as a cache)
        // p.s. to increase performance, it is possible to store that a room is attempted to be created before it is created
        // this allows to skip checking if there's no database record (it can contain list of invited puppets to identify room, though
        // this list is already available here without database due to full idempotency)
        // also listing invites/joined rooms of appServiceBot and excluding already stored rooms from resulting list is useful

        val alias = idMapper.buildRoomAlias(roomIdToReplicate)
        val serviceMembersEvent = ServiceMembersEventContent(
            serviceMembers = listOfNotNull(
                appServiceBotId,
                // if room creation is triggered by bridge bypass
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
            isDirect = remoteRoom.isDirect
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

        client.room.joinRoom( // probably idempotent
            roomId,
            asUserId = invitePuppet
        ).recover {
            if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
                logger.warn { "Got uninvited puppet $invitePuppet after (probably) idempotency hit on room $roomId ($alias), inviting and joining" }
                inviteAndJoinPuppetToRoom(
                    roomId,
                    invitePuppet,
                    actorRepository.getMxUserOfActorPuppet(actorId) == invitePuppet
                )
            } else throw it
        }.getOrThrow()

        // TODO error handling, currently it throws it up
        // currently only for direct rooms, TODO make it for all rooms
        // also this code deprecates `invitePuppet` argument, if `getRoomMembers` is obligated to return all members from room
        if (remoteRoom.isDirect) coroutineScope {
            remoteWorker.getRoomMembers(actorId, roomIdToReplicate)
                .filter { it.first != invitePuppet }
                .map {
                    async {
                        val userId = replicateRemoteUser(it.first, actorId, it.second)
                        inviteAndJoinPuppetToRoom(
                            mxRoomId = roomId,
                            mxPuppetId = userId,
                            asServiceUser = false // it is implied by contract on getRoomMembers
                        )
                    }
                }
                .buffer() // separate coroutines
                .collect { it.await() }
        }
        roomRepository.create(
            actorId = actorId,
            mxId = roomId,
            remoteId = roomIdToReplicate,
            isDirect = remoteRoom.isDirect
        )

        // idempotency: remove alias as room is already in database
        logger.debug { "Removing canonical alias ${alias.full} as room is added to database" }
        client.room.deleteRoomAlias(alias).getOrThrow() // this actually removes alias, but it leaves event in room
        client.room.sendStateEvent(roomId, CanonicalAliasEventContent())
            .getOrThrow() // this hides alias from users, but does not remove it
        return roomId
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

    // migration code, either to be deleted or properly extracted
    public suspend fun migrateRoom(actorId: ACTOR, roomId: RoomId, remoteRoomId: ROOM, isDirect: Boolean) {
        if (roomRepository.getRemoteRoom(roomId) != null) {
            logger.info { "Room $roomId is already fully migrated" }
            return
        }
        logger.info { "Migrating room $roomId" }

        // Every phase (except 0 and 4) has guard condition for idempotency
        val members = client.room.getJoinedMembers(roomId)
            .onFailure {
                if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
                    logger.warn { "Can't migrate room $roomId: ${it.message}" }
                    return
                }
            }
            .getOrThrow().joined.keys
        val gateway = run {
            logger.debug { "Phase 0: Searching for gateway" }
            val gateway =
                members.find { it.isBridgeControlled() } ?: run {
                    logger.warn { "Room $roomId has no puppets! Can't migrate" }
                    return@migrateRoom
                }
            val powerLevels =
                client.room.getStateEvent<PowerLevelsEventContent>(roomId, asUserId = gateway).getOrThrow()
            if (powerLevels.users[gateway] != 100L) {
                members.find { it.isBridgeControlled() && powerLevels.users[it] == 100L }
                    ?: run {
                        logger.warn { "Room $roomId has no puppets with admin rights! Can't migrate" }
                        logger.debug { "Members: $members, powerLevels: ${powerLevels.users}" }
                        return@migrateRoom
                    }
            } else gateway
        }

        run {
            if (appServiceBotId in members) return@run

            logger.debug { "Phase 1: Inviting appservice bot" }
            client.room.inviteUser(roomId, appServiceBotId, reason = "Migration", asUserId = gateway).getOrThrow()
            client.room.joinRoom(roomId, asUserId = appServiceBotId).getOrThrow()
        }

        run {
            val powerLevels =
                client.room.getStateEvent<PowerLevelsEventContent>(roomId, asUserId = gateway).getOrThrow()
            if (powerLevels.users[appServiceBotId] == 100L) return@run
            logger.debug { "Phase 2: Setting appservice bot power levels" }
            client.room.sendStateEvent(
                roomId,
                powerLevels.copy(users = powerLevels.users + (appServiceBotId to 100L)),
                asUserId = gateway
            )
                .getOrThrow()
        }

        run {
            val initialServiceMembers = client.room.getStateEvent<ServiceMembersEventContent>(roomId)
                .recover {
                    if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) null
                    else throw it
                }
                .getOrThrow()
                ?.also {
                    if (appServiceBotId in it.serviceMembers) return@run
                }
            logger.debug { "Phase 3: Initialising service members" }
            val serviceMembers = when (initialServiceMembers) {
                null -> ServiceMembersEventContent(
                    serviceMembers = listOf(appServiceBotId)
                )

                else -> initialServiceMembers.copy(serviceMembers = initialServiceMembers.serviceMembers + appServiceBotId)
            }
            client.room.sendStateEvent(roomId = roomId, eventContent = serviceMembers).getOrThrow()
        }

        run {
            logger.debug { "Phase 4: Storing in database" }
            roomRepository.create(actorId, roomId, remoteRoomId, isDirect)
        }
    }

    // TODO it is a very loose check, consider asking HS about that
    private fun UserId.isBridgeControlled() = (localpart.startsWith(puppetPrefix) && domain == homeserverDomain)
            || this == appServiceBotId
}