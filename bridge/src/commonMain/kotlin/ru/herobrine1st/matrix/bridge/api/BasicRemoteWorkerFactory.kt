package ru.herobrine1st.matrix.bridge.api

import ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker

/**
 * A provider for RemoteWorker. Guaranteed to be used exactly once and most probably be disposed right away.
 */
public fun interface BasicRemoteWorkerFactory<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> {
    /**
     * @param api API for RemoteWorker to use
     * @return A [ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker] instance for remote network
     */
    public fun getRemoteWorker(api: RemoteWorkerAPI<USER, ROOM, MESSAGE>): BasicRemoteWorker<ACTOR, USER, ROOM, MESSAGE>
}