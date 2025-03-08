package ru.herobrine1st.matrix.bridge.repository

import net.folivo.trixnity.core.model.UserId

public interface PuppetRepository<USER : Any> {

    /**
     * This method returns id of puppet stored using [createPuppet], or null if not found.
     *
     * If returned user id is not a puppet, the behavior is undefined as
     * resulting ID is not controlled by bridge.
     *
     * @param id User id on remote side.
     * @return [UserId] of puppet on local side. If returned id is not a puppet, behavior is undefined.
     */
    public suspend fun getMxUser(id: USER): UserId?

    /**
     * This method stores puppet as a mapping between its matrix and remote id.
     * After that, [getMxUser] should return this mapping.
     */
    public suspend fun createPuppet(mxId: UserId, remoteId: USER)
}