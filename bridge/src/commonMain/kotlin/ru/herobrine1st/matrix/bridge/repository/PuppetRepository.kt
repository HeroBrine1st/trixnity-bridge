package ru.herobrine1st.matrix.bridge.repository

import net.folivo.trixnity.core.model.UserId

public interface PuppetRepository<USER : Any> {

    /**
     * This method returns matrix id of puppet stored using [createPuppet], or null if not found.
     *
     * If returned user id is not a puppet, the behavior is undefined as
     * resulting ID is not controlled by bridge.
     *
     * @param id User id on remote side.
     * @return [UserId] of puppet on local side. If returned id is not a puppet, behavior is undefined.
     */
    public suspend fun getPuppetId(id: USER): UserId?

    /**
     * This method returns remote id of puppet stored using [createPuppet], or null if not found.
     *
     * @param id matrix ID of puppet
     * @return remote id of the same puppet or null if not found
     */
    public suspend fun getPuppetId(id: UserId): USER?

    /**
     * This method stores puppet as a mapping between its matrix and remote id.
     * After that, [getPuppetId] should return this mapping.
     */
    public suspend fun createPuppet(mxId: UserId, remoteId: USER)
}