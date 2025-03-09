package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import net.folivo.trixnity.core.model.RoomId
import ru.herobrine1st.matrix.bridge.repository.RoomRepository
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

public class RoomRepositoryImpl<ACTOR : Any, ROOM : Any> @PublishedApi internal constructor(
    internal val databaseFactory: DatabaseFactory,
    internal val actorIdSerializer: KSerializer<ACTOR>,
    internal val roomIdSerializer: KSerializer<ROOM>,
    internal val stringFormat: StringFormat
) : RoomRepository<ACTOR, ROOM> {
    override suspend fun getRemoteRoom(id: RoomId): ROOM? = databaseFactory.useDatabase { database ->
        database.roomQueries.getRemoteIdByRoomId(id).awaitAsOneOrNull()
            ?.let { stringFormat.decodeFromString(roomIdSerializer, it) }
    }

    override suspend fun getMxRoom(id: ROOM): RoomId? = databaseFactory.useDatabase { database ->
        database.roomQueries.getRoomIdByRemoteId(stringFormat.encodeToString(roomIdSerializer, id)).awaitAsOneOrNull()
    }


    override suspend fun create(
        actorId: ACTOR,
        mxId: RoomId,
        remoteId: ROOM,
        isDirect: Boolean
    ): Unit = databaseFactory.useDatabase { database ->
        database.roomQueries.create(
            mxId,
            stringFormat.encodeToString(roomIdSerializer, remoteId),
            isDirect,
            stringFormat.encodeToString(actorIdSerializer, actorId)
        )
    }

    override suspend fun isRoomBridged(id: ROOM): Boolean = databaseFactory.useDatabase { database ->
        database.roomQueries.isRoomBridged(stringFormat.encodeToString(roomIdSerializer, id)).awaitAsOne()
    }
}