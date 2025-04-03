package ru.herobrine1st.matrix.bridge.api

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.ClientEvent

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
     * Implementation of this method SHOULD be idempotent. [RoomEvent.eventId] can be used for that.
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
     * It is up to implementation how to get events and convert them to [WorkerEvent].
     *
     * Implementations SHOULD NOT throw any exceptions outside of [Flow]. A fitting
     * place for exceptions at this stage is initialization of an implementation.
     *
     * Implementations SHOULD pass exceptions down this flow in case of network or other failures. Implementations MUST
     * be able to recover after that in subsequent [Flow]s obtained by repeated call to this method, except on [WorkerEvent.FatalFailure].
     *
     * @param actorId ID of remote actor to get events from
     * @return Flow of events on remote side
     */
    public fun getEvents(actorId: ACTOR): Flow<WorkerEvent<USER, ROOM, MESSAGE>>

    /**
     * Fetch remote user
     *
     * Implementation SHOULD return a fresh instance of user data, e.g. it is verified by remote server
     * (e.g. Cache-Control header) or fetched fully from it. If implementation does know about changes immediately and
     * no query is required, it may be useful to use [UserDataHolder.userData] field in supported events.
     *
     * This method is called in response to [RoomEvent.RoomMember] event with [RoomEvent.RoomMember.userData]
     * field set to null (the default).
     *
     * @param actorId actor that is recommended to use while fetching remote user
     * @param id User to fetch
     * @return Fresh [RemoteUser] data instance
     */
    public suspend fun getUser(actorId: ACTOR, id: USER): RemoteUser<USER>

    /**
     * Fetch remote room
     *
     * Implementation MUST make sure the returned instance is fresh, i.e. in most cases it is either verified by remote server
     * (e.g. Cache-Control header) or fetched fully from it. If implementation does know about changes immediately and
     * no query is required, it may be useful to use [RoomDataHolder.roomData] field in supported events.
     *
     * This method is called in response to [RoomEvent.RoomCreation] event with [RoomEvent.RoomCreation.roomData]
     * field set to null (the default).
     *
     * @param actorId actor that is recommended to use while fetching remote room
     * @param id Room to fetch
     * @return Fresh [RemoteRoom] data instance
     */
    public suspend fun getRoom(actorId: ACTOR, id: ROOM): RemoteRoom<ROOM, USER>

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

}