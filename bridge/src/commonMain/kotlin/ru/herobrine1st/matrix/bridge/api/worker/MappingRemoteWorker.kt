package ru.herobrine1st.matrix.bridge.api.worker

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.*

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

                public data class Create<USER : Any, ROOM : Any>(
                    val roomData: RemoteRoom<USER, ROOM>,
                ) : Room<USER, ROOM, Nothing> {
                    override val roomId: ROOM by roomData::id
                }

                // Represents state machine fields from https://spec.matrix.org/latest/client-server-api/#mroommember
                public data class Membership<USER : Any, ROOM : Any>(
                    override val roomId: ROOM,
                    // ignored on JOIN and KNOCK
                    val sender: USER?, // if null and [membership] allows for sender other than stateKey (e.g. invite by someone else) then it is appservice bot
                    val stateKey: USER,
                    val membership: net.folivo.trixnity.core.model.events.m.room.Membership,
                ) : Room<USER, ROOM, Nothing>

                public data class Message<USER : Any, ROOM : Any, MESSAGE : Any>(
                    val messageData: RemoteMessageEventData<USER, ROOM, MESSAGE>,
                ) : Room<USER, ROOM, MESSAGE>, IRemoteMessageEventData<USER, ROOM, MESSAGE> by messageData
            }

            public sealed interface User<USER : Any> : Remote<USER, Nothing, Nothing> {
                public val userId: USER

                public data class Create<USER : Any>(
                    val userData: RemoteUser<USER>
                ) : User<USER> {
                    override val userId: USER by userData::id
                }
            }
        }

    }
}
