package ru.herobrine1st.matrix.bridge.api.worker

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.RemoteRoom
import ru.herobrine1st.matrix.bridge.api.RemoteUser
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId

// This RemoteWorker supplements ProvisioningRemoteWorker with entities to be provisioned, along with other events
// mapping - this worker "maps" the remote, like creating a world map
public interface MappingRemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> {
    public suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>
    )

    // this method intercepts every event, looks if room/user is not known and if so fetches info and passes all data to
    // ProvisioningRemoteWorker
    public fun getEvents(actorId: ACTOR): Flow<Event<USER, ROOM, MESSAGE>>

    public sealed interface Event<out USER : Any, out ROOM : Any, out MESSAGE : Any> {
        public data object Connected : Event<Nothing, Nothing, Nothing>

        public data class Disconnected(val reason: String = "") : Event<Nothing, Nothing, Nothing>

        public data class FatalFailure(val reason: String) : Event<Nothing, Nothing, Nothing>

        public sealed interface Remote<USER : Any, ROOM : Any, MESSAGE : Any> : Event<USER, ROOM, MESSAGE> {
            public sealed interface Room<USER : Any, ROOM : Any, MESSAGE : Any> : Remote<USER, ROOM, MESSAGE> {
                public val roomId: ROOM
                public val eventId: RemoteEventId

                public data class Create<ROOM : Any>(
                    val roomData: RemoteRoom<ROOM>,
                    override val eventId: RemoteEventId
                ) : Room<Nothing, ROOM, Nothing> {
                    override val roomId: ROOM by roomData::id
                }

                // TODO membership
                // TODO message - use DRY somehow
            }

            public sealed interface User<USER : Any> : Remote<USER, Nothing, Nothing> {
                public val userId: USER
                public val eventId: RemoteEventId

                public data class Create<USER : Any>(
                    override val eventId: RemoteEventId,
                    val userData: RemoteUser<USER>
                ) : User<USER> {
                    override val userId: USER by userData::id
                }
            }
        }

    }
}
