package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import ru.herobrine1st.matrix.bridge.repository.ActorRepository
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

// TODO: there's currently no way to add actors
public class ActorRepositoryImpl<ACTOR : Any> internal constructor(
    internal val databaseFactory: DatabaseFactory,
    internal val serializer: KSerializer<ACTOR>,
    internal val stringFormat: StringFormat
) : ActorRepository<ACTOR> {
    override suspend fun getLocalUserIdForActor(remoteActorId: ACTOR): UserId? = null

    override suspend fun getActorIdByEvent(event: ClientEvent.RoomEvent<*>): ACTOR? =
        databaseFactory.useDatabase { database ->
            database.roomQueries.getActorByRoomId(event.roomId).awaitAsOneOrNull()
                ?.let { stringFormat.decodeFromString(serializer, it) }
        }

    override suspend fun getMxUserOfActorPuppet(actorId: ACTOR): UserId? = null

    override fun getActorsFlow(): Flow<List<ACTOR>> = flow {
        databaseFactory.useDatabase { database ->
            database.actorQueries.getAllIds()
                .asFlow()
                .map {
                    it.awaitAsList().map { stringFormat.decodeFromString(serializer, it) }
                }
                .let { emitAll(it) }
        }
    }
}