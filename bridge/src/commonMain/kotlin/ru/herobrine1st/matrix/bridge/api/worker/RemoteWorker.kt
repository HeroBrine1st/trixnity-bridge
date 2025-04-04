package ru.herobrine1st.matrix.bridge.api.worker

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import ru.herobrine1st.matrix.bridge.api.*
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId

/**
 * An interface to remote side.
 *
 * This interface has four type parameters that are used throughout the bridge; each of them is an id of respective entity.
 * This allows arbitrary types, however, those types have to be supported by repositories.
 *
 * @param ACTOR - exact type of actor ID
 * @param USER - exact type of remote puppet ID
 * @param ROOM - exact type of remote room ID
 * @param MESSAGE - exact type of remote message ID
 */
public interface RemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> {
    /**
     * This method is called when an event on matrix side is fired and delivered to application service.
     *
     * This method could be called multiple times with the same [event] in case of network or other failures.
     * Implementation of this method SHOULD be idempotent. [ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker.Event.Remote.Room.eventId] can be used for that.
     *
     * Implementation MUST dispatch [event] immediately, suspending until it is done. A successful return means that
     * event dispatched successfully, and in most cases it won't be used again in calls to this method.
     *
     * If this worker can't dispatch [event] to remote side, but it is possible later and worth retrying (e.g. network I/O error),
     * a relevant exception MUST be thrown.
     *
     * If [event] cannot be dispatched to remote side (e.g. event is not supported or an application-level error occurred),
     * implementation MUST throw [ru.herobrine1st.matrix.bridge.exception.UnhandledEventException].
     *
     * @param actorId id of actor is recommended to use for dispatching event to remote side
     * @param roomId ID of corresponding room on remote side
     * @param event A matrix event
     * @throws Throwable in case of any error
     * @throws ru.herobrine1st.matrix.bridge.exception.EventHandlingException in case of edge case errors
     */
    // TODO add exceptions for "permission denied" and other, because state events can be invalid and so should be deleted right away
    //      Another exceptions:
    //      - UnsupportedEventException (and change doc above), so that we can respond back with error
    public suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>
    )

    /**
     * This method provides a subscription to remote side events.
     *
     * There's no constraints on implementation of this method, be it long-polling, web sockets, periodic polling etc.
     * It is up to implementation how to get events and convert them to [Event].
     *
     * Implementations SHOULD NOT throw any exceptions outside of [kotlinx.coroutines.flow.Flow]. A fitting
     * place for exceptions at this stage is initialization of an implementation.
     *
     * Implementations SHOULD pass exceptions down this flow in case of network or other failures. Implementations MUST
     * be able to recover after that in subsequent [kotlinx.coroutines.flow.Flow]s obtained by repeated call to this method, except on [Event.FatalFailure].
     *
     * @param actorId ID of remote actor to get events from
     * @return Flow of events on remote side
     */
    public fun getEvents(actorId: ACTOR): Flow<Event<USER, ROOM, MESSAGE>>

    /**
     * Fetch remote user
     *
     * Implementation SHOULD return a fresh instance of user data, e.g. it is verified by remote server
     * (e.g. Cache-Control header) or fetched fully from it. If implementation does know about changes immediately and
     * no query is required, it may be useful to use [ru.herobrine1st.matrix.bridge.api.UserDataHolder.userData] field in supported events.
     *
     * This method is called in response to [ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker.Event.Remote.Room.Membership] event with [ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker.Event.Remote.Room.Membership.userData]
     * field set to null (the default).
     *
     * @param actorId actor that is recommended to use while fetching remote user
     * @param id User to fetch
     * @return Fresh [ru.herobrine1st.matrix.bridge.api.RemoteUser] data instance
     */
    public suspend fun getUser(actorId: ACTOR, id: USER): RemoteUser<USER>

    /**
     * Fetch remote room
     *
     * Implementation MUST make sure the returned instance is fresh, i.e. in most cases it is either verified by remote server
     * (e.g. Cache-Control header) or fetched fully from it. If implementation does know about changes immediately and
     * no query is required, it may be useful to use [ru.herobrine1st.matrix.bridge.api.RoomDataHolder.roomData] field in supported events.
     *
     * This method is called in response to [ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker.Event.Remote.Room.Create] event with [ru.herobrine1st.matrix.bridge.api.worker.RemoteWorker.Event.Remote.Room.Create.roomData]
     * field set to null (the default).
     *
     * @param actorId actor that is recommended to use while fetching remote room
     * @param id Room to fetch
     * @return Fresh [ru.herobrine1st.matrix.bridge.api.RemoteRoom] data instance
     */
    public suspend fun getRoom(actorId: ACTOR, id: ROOM): RemoteRoom<USER, ROOM>

    /**
     * This method provides a [Flow] of remote users in remote room, denoted by [remoteId].
     *
     * Resulting flow MUST NOT contain actor account as defined by
     * [ru.herobrine1st.matrix.bridge.repository.ActorRepository.getMxUserOfActorPuppet].
     *
     * [Flow] gives ability to use pagination, but it is up to implementation how to fetch users from remote room. This method
     * can return remote user data in [RemoteUser] class to avoid additional [getUser] request or set it to null,
     * at [RemoteWorker] discretion. In the latter case, if puppet is not created yet, [getUser] is called to get user data.
     * Generally this method should not return user data, but if it is cheap to get user data in bulk,
     * it is an efficient optimisation to return this data here. The returned data SHOULD be fresh as defined by [getUser].
     * [RemoteUser.id] MUST be the same as first value of a pair.
     *
     * Implementation SHOULD NOT throw any exceptions outside of [Flow]. Implementation SHOULD pass exceptions down
     * this flow in case of network or other failures.
     *
     * @param actorId id of actor that is recommended to use while fetching remote room members
     * @param remoteId An id denoting requested room
     * @return Flow of users in room. Each pair has two values: id of user and its data.
     */
    public fun getRoomMembers(actorId: ACTOR, remoteId: ROOM): Flow<Pair<USER, RemoteUser<USER>?>>


    public sealed interface Event<out USER : Any, out ROOM : Any, out MESSAGE : Any> {
        /**
         * RemoteWorker successfully connected to remote side.
         */
        public data object Connected : Event<Nothing, Nothing, Nothing>

        /**
         * RemoteWorker disconnected from remote side. Automatically fired in case of errors.
         *
         * @param reason A human-readable reason of this disconnection
         */
        public data class Disconnected(val reason: String = "") : Event<Nothing, Nothing, Nothing>

        /**
         * RemoteWorker is absolutely sure it faced an irrecoverable error.
         *
         * Examples: remote authorization revocation, account ban.
         *
         * No further attempts of recovering this actor will be made in this process.
         *
         * @param reason A human-readable reason
         */
        public data class FatalFailure(val reason: String) : Event<Nothing, Nothing, Nothing>

        /**
         * An event on remote side
         */
        public sealed interface Remote<USER : Any, ROOM : Any, MESSAGE : Any> : Event<USER, ROOM, MESSAGE> {
            public sealed interface Room<USER : Any, ROOM : Any, MESSAGE : Any> : Remote<USER, ROOM, MESSAGE> {
                /**
                 * An id of remote room this event belongs to
                 */
                public val roomId: ROOM

                public data class Message<USER : Any, ROOM : Any, MESSAGE : Any>(
                    override val roomId: ROOM,
                    val eventId: RemoteEventId,
                    val sender: USER,
                    val content: MessageEventContent,
                    /**
                     * If not null, can later be used to reference matrix event by provided value.
                     *
                     * This value MUST be unique or null, even if replacing event
                     *
                     * @see ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI.getMessageEventId
                     */
                    val messageId: MESSAGE? = null,
                ) : Room<USER, ROOM, MESSAGE>

                public data class Create<USER : Any, ROOM : Any, MESSAGE : Any>(
                    override val roomId: ROOM,
                    override val roomData: RemoteRoom<USER, ROOM>? = null,
                ) : Room<USER, ROOM, MESSAGE>, RoomDataHolder<USER, ROOM>

                public data class Membership<USER : Any, ROOM : Any, MESSAGE : Any>(
                    override val roomId: ROOM,
                    val sender: USER?,
                    val stateKey: USER,
                    val membership: net.folivo.trixnity.core.model.events.m.room.Membership,
                    override val userData: RemoteUser<USER>? = null,
                ) : Room<USER, ROOM, MESSAGE>, UserDataHolder<USER>
            }
        }
    }
}