package ru.herobrine1st.matrix.bridge.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.applicationserviceapi.server.ApplicationServiceApiServerHandler
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.getStateEvent
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import ru.herobrine1st.matrix.bridge.api.ErrorNotifier
import ru.herobrine1st.matrix.bridge.api.RemoteIdToMatrixMapper
import ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.MappingRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.ProvisioningRemoteWorker
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
    remoteWorkerFactory: ProvisioningRemoteWorker.Factory<ACTOR, USER, ROOM, MESSAGE>,
    appServiceWorkerRepositorySet: AppServiceWorkerRepositorySet<ACTOR, USER, ROOM, MESSAGE>,
    bridgeConfig: BridgeConfig,
    private val errorNotifier: ErrorNotifier = ErrorNotifier { _, _, _ -> },
) : ApplicationServiceApiServerHandler {
    public constructor(
        applicationJob: Job,
        client: MatrixClientServerApiClient,
        remoteWorkerFactory: BasicRemoteWorker.Factory<ACTOR, USER, ROOM, MESSAGE>,
        idMapperFactory: RemoteIdToMatrixMapper.Factory<ROOM, USER>,
        appServiceWorkerRepositorySet: AppServiceWorkerRepositorySet<ACTOR, USER, ROOM, MESSAGE>,
        bridgeConfig: BridgeConfig,
        errorNotifier: ErrorNotifier = ErrorNotifier { _, _, _ -> },
    ) : this(
        applicationJob,
        client,
        MappingRemoteWorker.Factory { api ->
            DefaultMappingRemoteWorker(
                client,
                appServiceWorkerRepositorySet.puppetRepository,
                appServiceWorkerRepositorySet.roomRepository,
                api,
                remoteWorkerFactory
            )
        },
        idMapperFactory,
        appServiceWorkerRepositorySet,
        bridgeConfig,
        errorNotifier
    )

    public constructor(
        applicationJob: Job,
        client: MatrixClientServerApiClient,
        remoteWorkerFactory: MappingRemoteWorker.Factory<ACTOR, USER, ROOM, MESSAGE>,
        idMapperFactory: RemoteIdToMatrixMapper.Factory<ROOM, USER>,
        appServiceWorkerRepositorySet: AppServiceWorkerRepositorySet<ACTOR, USER, ROOM, MESSAGE>,
        bridgeConfig: BridgeConfig,
        errorNotifier: ErrorNotifier = ErrorNotifier { _, _, _ -> },
    ) : this(
        applicationJob,
        client,
        ProvisioningRemoteWorker.Factory { api ->
            DefaultProvisioningRemoteWorker(
                client,
                appServiceWorkerRepositorySet.puppetRepository,
                appServiceWorkerRepositorySet.roomRepository,
                idMapperFactory,
                bridgeConfig,
                api,
                remoteWorkerFactory
            )
        },
        appServiceWorkerRepositorySet,
        bridgeConfig,
        errorNotifier
    )


    private val actorRepository: ActorRepository<ACTOR> = appServiceWorkerRepositorySet.actorRepository
    private val messageRepository: MessageRepository<MESSAGE> = appServiceWorkerRepositorySet.messageRepository
    private val puppetRepository: PuppetRepository<USER> = appServiceWorkerRepositorySet.puppetRepository
    private val roomRepository: RoomRepository<ACTOR, ROOM> = appServiceWorkerRepositorySet.roomRepository
    private val transactionRepository: TransactionRepository = appServiceWorkerRepositorySet.transactionRepository

    private val appServiceBotId: UserId = UserId(bridgeConfig.botLocalpart, bridgeConfig.homeserverDomain)
    private val puppetPrefix = bridgeConfig.puppetPrefix
    private val homeserverDomain = bridgeConfig.homeserverDomain
    private val whitelist = bridgeConfig.provisioning.whitelist
    private val blacklist = bridgeConfig.provisioning.blacklist

    private val remoteWorker: ProvisioningRemoteWorker<ACTOR, USER, ROOM, MESSAGE> = remoteWorkerFactory.get(
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
        events: List<ClientEvent.RoomEvent<*>>,
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
                            emit(ProvisioningRemoteWorker.Event.Disconnected(cause.toString()))
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
                        if (it is ProvisioningRemoteWorker.Event.FatalFailure) {
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

    private suspend fun handleWorkerEvent(actorId: ACTOR, event: ProvisioningRemoteWorker.Event<USER, ROOM, MESSAGE>) =
        try {
            when (event) {
                ProvisioningRemoteWorker.Event.Connected -> {}
                is ProvisioningRemoteWorker.Event.Disconnected -> {}
                is ProvisioningRemoteWorker.Event.FatalFailure -> {}
                is ProvisioningRemoteWorker.Event.Remote -> handleRemoteEvent(actorId, event)
            }
        } catch (e: NoSuchActorException) {
            logger.warn(e) { "Actor $actorId is not registered but returned an event. Ignoring." }
            errorNotifier.notify("Actor $actorId is not registered but returned an event. Ignoring.", e, false)
        }

    private suspend fun handleRemoteEvent(
        actorId: ACTOR,
        remoteEvent: ProvisioningRemoteWorker.Event.Remote<USER, ROOM, MESSAGE>,
    ) {
        when (val event = remoteEvent) {
            is ProvisioningRemoteWorker.Event.Remote.Room.Message -> {
                // SAFETY: User and room presence is guaranteed by MappingRemoteWorker
                val mxPuppetId = puppetRepository.getPuppetId(event.sender)!!
                val mxRoomId = roomRepository.getMxRoom(event.roomId)!!

                val eventId = client.room.sendMessageEvent(
                    mxRoomId,
                    eventContent = event.content,
                    txnId = event.eventId.value,
                    asUserId = mxPuppetId
                ).getOrThrow() // SAFETY: User membership is guaranteed to be JOINED by MappingRemoteWorker
//                    .recover {
//                    if (it is MatrixServerException && it.errorResponse is ErrorResponse.Forbidden) {
//                        inviteAndJoinPuppetToRoom(
//                            mxRoomId,
//                            mxPuppetId,
//                            actorRepository.getMxUserOfActorPuppet(actorId) == mxPuppetId
//                        )
//                        return@recover client.room.sendMessageEvent(
//                            mxRoomId,
//                            eventContent = event.content,
//                            txnId = event.eventId.value,
//                            asUserId = mxPuppetId
//                        ).getOrThrow()
//                    } else throw it
//                }

                if (event.messageId != null) {
                    messageRepository.createRelation(event.messageId, eventId)
                }
            }

            is ProvisioningRemoteWorker.Event.Remote.Room.Create -> roomRepository.create(
                actorId,
                event.mxRoomId,
                event.roomId
            )

            is ProvisioningRemoteWorker.Event.Remote.User.Create -> puppetRepository.createPuppet(
                event.mxUserId,
                event.userId
            )
        }
    }

    // migration code, either to be deleted or properly extracted
    public suspend fun migrateRoom(actorId: ACTOR, roomId: RoomId, remoteRoomId: ROOM) {
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
            roomRepository.create(actorId, roomId, remoteRoomId)
        }
    }

    // TODO it is a very loose check, consider asking HS about that
    private fun UserId.isBridgeControlled() = (localpart.startsWith(puppetPrefix) && domain == homeserverDomain)
            || this == appServiceBotId
}