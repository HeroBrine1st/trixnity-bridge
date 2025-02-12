package ru.herobrine1st.matrix.bridge.api

import ru.herobrine1st.matrix.bridge.api.value.RemoteActorId
import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId
import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId

/**
 * A provider for RemoteWorker. Guaranteed to be used exactly once and most probably be disposed right away.
 */
public fun interface RemoteWorkerFactory<ACTOR : RemoteActorId, USER : RemoteUserId, ROOM : RemoteRoomId, MESSAGE : RemoteMessageId> {
    /**
     * @param api API for RemoteWorker to use
     * @return A [RemoteWorker] instance for remote network
     */
    public fun getRemoteWorker(api: RemoteWorkerAPI<USER, ROOM, MESSAGE>): RemoteWorker<ACTOR, USER, ROOM, MESSAGE>
}