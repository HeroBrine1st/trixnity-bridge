package ru.herobrine1st.matrix.bridge.repository

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker
import ru.herobrine1st.matrix.bridge.exception.NoSuchActorException

public interface ActorRepository<ACTOR : Any> {
    /**
     * This method returns [UserId] of a real user if actor is a personal account. This applies only to personal bridges.
     *
     * The returned value can be thought of as "owner" of an [ACTOR].
     *
     * @return A real user of actor if it is a personal actor, otherwise null.
     * @throws NoSuchActorException if actor is not found
     */
    public suspend fun getLocalUserIdForActor(remoteActorId: ACTOR): UserId?

    /**
     * This method returns actor that can handle [event].
     *
     * For personal bridges it is recommended to use [ClientEvent.RoomEvent.sender] with backing storage saving "ownership" of users to actors.
     * For general bridges [ClientEvent.RoomEvent.roomId] can be used with backing storage saving "ownership" of rooms to actors.
     * More sophisticated algorithms can be used; however, those two are extremely common.
     *
     * @return Actor ID that will be passed to remote worker as recommended actor to use, or null if event is not to be processed.
     * @see [BasicRemoteWorker.handleEvent]
     */
    public suspend fun getActorIdByEvent(event: ClientEvent.RoomEvent<*>): ACTOR?

    /**
     * This method returns a puppet of actor account on the remote side if it is created.
     *
     * Actor account is the remote account that remote worker uses to act on remote network.
     *
     * This bridge has ability to replicate events emitted from actor but bypassed the bridge
     * (e.g. when you manually go to remote network and send messages from account connected to bridge).
     *
     * For this feature to work, the bridge needs to create a puppet of actor account and invite it to room.
     * When that puppet is joined to room, it is shown as hero, which is not desirable. To fix that,
     * [MSC4171: Service members](https://github.com/matrix-org/matrix-spec-proposals/pull/4171) is used.
     *
     * This method returns user id of user that corresponds to that puppet. If there's no puppet for this actor's account
     * in database, this method SHOULD return null. It can also return null if repository can't correspond puppet to actor,
     * though functionality will be degraded.
     *
     * If this method returns null and actor account puppet is already created, it implies that puppet is not a
     * service member as per MSC4171. This allows returning null if MSC4171 is not needed for [actorId].
     *
     * @param actorId the ID of the actor's to find the corresponding [UserId] for.
     * @return [UserId] of corresponding puppet from database. This is different to [getLocalUserIdForActor] as returned
     * id is in namespace of the application service.
     */
    // probably MSC4171 can be required in some rooms and not required in other, this method currently has no vote on that
    // if this method gets a room id, RemoteWorker.getRoomMembers doc should be updated
    public suspend fun getMxUserOfActorPuppet(actorId: ACTOR): UserId?

    /**
     * This method provides bridge with the information about currently available workers.
     *
     * If new actor is emerged, it will be automatically subscribed to via [BasicRemoteWorker.getEvents]
     * If actor is removed, subscription will be automatically terminated. RemoteWorker SHOULD NOT try to complete it itself,
     * but it is possible to return FatalFailure in case of authentication revocation or other internal bridge failures due to
     * account removal.
     *
     * @return Flow of currently available remote actor ids
     */
    public fun getActorsFlow(): Flow<List<ACTOR>>
}