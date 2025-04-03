package ru.herobrine1st.matrix.bridge.api.worker

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.api.EventHandlerScope
import ru.herobrine1st.matrix.bridge.api.IRemoteMessageEventData
import ru.herobrine1st.matrix.bridge.api.RemoteMessageEventData
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId

/**
 * This RemoteWorker has ability to provision entities unknown to the bridge, according to events
 * [MappingRemoteWorker.Event.Remote.User.Create] and [MappingRemoteWorker.Event.Remote.Room.Create]
 *
 * Responsibilities:
 *
 * - Provisioning rooms/users/user memberships, including updates
 * - Dispatching ID pairs higher (membership info is stored on HS)
 * - Passing through everything else
 */
public interface ProvisioningRemoteWorker<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> {
    public suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>
    )

    public fun getEvents(actorId: ACTOR): Flow<Event<USER, ROOM, MESSAGE>>

    public sealed interface Event<out USER : Any, out ROOM : Any, out MESSAGE : Any> {
        public data object Connected : Event<Nothing, Nothing, Nothing>

        public data class Disconnected(val reason: String = "") : Event<Nothing, Nothing, Nothing>

        public data class FatalFailure(val reason: String) : Event<Nothing, Nothing, Nothing>

        public sealed interface Remote<USER : Any, ROOM : Any, MESSAGE : Any> : Event<USER, ROOM, MESSAGE> {
            public sealed interface Room<USER : Any, ROOM : Any, MESSAGE : Any> : Remote<USER, ROOM, MESSAGE> {
                public val roomId: ROOM
                public val eventId: RemoteEventId

                /**
                 * A room has been created and pair [mxRoomId]-[roomId] should be stored
                 */
                public data class Create<ROOM : Any>(
                    val mxRoomId: RoomId,
                    override val roomId: ROOM,
                    override val eventId: RemoteEventId
                ) : Room<Nothing, ROOM, Nothing>

                // TODO membership

                public data class Message<USER : Any, ROOM : Any, MESSAGE : Any>(
                    val messageData: RemoteMessageEventData<USER, ROOM, MESSAGE>,
                ) : Room<USER, ROOM, MESSAGE>, IRemoteMessageEventData<USER, ROOM, MESSAGE> by messageData
            }

            public sealed interface User<USER : Any> : Remote<USER, Nothing, Nothing> {
                public val userId: USER
                public val eventId: RemoteEventId

                /**
                 * A user has been created and pair [mxUserId]-[userId] should be stored
                 */
                public data class Create<USER : Any>(
                    public val mxUserId: UserId,
                    override val userId: USER,
                    override val eventId: RemoteEventId
                ) : User<USER>
            }
        }

    }
}