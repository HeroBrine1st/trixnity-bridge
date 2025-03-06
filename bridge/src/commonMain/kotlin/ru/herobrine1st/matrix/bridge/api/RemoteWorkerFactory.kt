package ru.herobrine1st.matrix.bridge.api

/**
 * A provider for RemoteWorker. Guaranteed to be used exactly once and most probably be disposed right away.
 */
public fun interface RemoteWorkerFactory<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> {
    /**
     * @param api API for RemoteWorker to use
     * @return A [RemoteWorker] instance for remote network
     */
    public fun getRemoteWorker(api: RemoteWorkerAPI<USER, ROOM, MESSAGE>): RemoteWorker<ACTOR, USER, ROOM, MESSAGE>
}