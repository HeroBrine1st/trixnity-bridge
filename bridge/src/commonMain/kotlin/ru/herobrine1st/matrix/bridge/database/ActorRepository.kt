package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.api.value.RemoteActorId

public interface ActorRepository<ACTOR : RemoteActorId> {
    // this is the user that "owns" that actor
    // TODO there are actors without owner (i.e. it is a double-puppeted actor), returning null is the same as "not found"?
    //      provide an exception
    public suspend fun getLocalUserIdForActor(remoteActorId: ACTOR): UserId?

    // userId is the user that owns the actor
    public suspend fun getActorIdByLocalUserId(userId: UserId): ACTOR?

    /**
     * This method returns a puppet of actor account on remote side if it is created.
     *
     * Actor account is the remote account that [ru.herobrine1st.matrix.bridge.api.RemoteWorker] uses to act on remote network.
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
     * service member as per MSC4171. This allows to return null if MSC4171 is not needed for [actorId].
     *
     * @param actorId the ID of the actor's to find the corresponding [UserId] for.
     * @return [UserId] of corresponding puppet from database. This is different to [getLocalUserIdForActor] as returned
     * id is in namespace of the application service.
     */
    // probably MSC4171 can be required in some rooms and not required in other, this method currently has no vote on that
    // if this method gets a room id, RemoteWorker.getRoomMembers doc should be updated
    public suspend fun getMxUserOfActorPuppet(actorId: ACTOR): UserId?
}