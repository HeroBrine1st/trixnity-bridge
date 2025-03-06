package ru.herobrine1st.matrix.bridge.api


public sealed interface WorkerEvent<out USER : Any, out ROOM : Any, out MESSAGE : Any> {
    /**
     * RemoteWorker successfully connected to remote side.
     */
    public data object Connected : WorkerEvent<Nothing, Nothing, Nothing>

    /**
     * RemoteWorker disconnected from remote side. Automatically fired in case of errors.
     *
     * @param reason A human-readable reason of this disconnection
     */
    public data class Disconnected(val reason: String = "") : WorkerEvent<Nothing, Nothing, Nothing>

    /**
     * RemoteWorker is absolutely sure it faced an irrecoverable error.
     *
     * Examples: remote authorization revocation, account ban.
     *
     * No further attempts of recovering this actor will be made in this process.
     *
     * @param reason A human-readable reason
     */
    public data class FatalFailure(val reason: String) : WorkerEvent<Nothing, Nothing, Nothing>

    /**
     * An event on remote side
     */
    public data class RemoteEvent<USER : Any, ROOM : Any, MESSAGE : Any>(
        val event: RoomEvent<USER, ROOM, MESSAGE>
    ) : WorkerEvent<USER, ROOM, MESSAGE>
}