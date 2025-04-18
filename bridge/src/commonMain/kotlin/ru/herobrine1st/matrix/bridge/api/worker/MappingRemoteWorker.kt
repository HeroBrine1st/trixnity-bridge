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

                /**
                 * This event represents state machine fields from [m.room.member](https://spec.matrix.org/latest/client-server-api/#mroommember)
                 *
                 * The [MappingRemoteWorker] MUST NOT emit multiple membership events in one transaction (defined by worker)
                 * except in these cases:
                 * - Invite followed by join
                 *
                 * In other cases [MappingRemoteWorker] MUST NOT emit next [Room.Membership] event without
                 * ensuring it won't emit previous [Room.Membership] event again. **Violating this contract leads to unspecified behavior**.
                 */
                public data class Membership<USER : Any, ROOM : Any>(
                    override val roomId: ROOM,
                    /**
                     * The author of the event if [membership] is not JOIN or KNOCK. If null, it is appservice bot.
                     *
                     * If [membership] is JOIN or KNOCK, setting this field to something other than [stateKey] or null
                     * is undefined behavior.
                     * <!-- That UB is actually used for DRY leading to more safety -->
                     */
                    val sender: USER?,
                    /**
                     * The user that is being affected by the event. It is also a sender for JOIN and KNOCK.
                     */
                    val stateKey: USER,
                    val membership: net.folivo.trixnity.core.model.events.m.room.Membership,
                    /**
                     * If true and [membership] is INVITE, uses [MSC4171: Service members](https://github.com/matrix-org/matrix-spec-proposals/pull/4171)
                     * on [stateKey].
                     *
                     * This is relevant to personal bridges handling bridge bypasses
                     */
                    val asServiceMember: Boolean = false,
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
