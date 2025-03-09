package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.repository.MessageRepository
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

public class MessageRepositoryImpl<MESSAGE : Any> @PublishedApi internal constructor(
    internal val databaseFactory: DatabaseFactory,
    internal val serializer: KSerializer<MESSAGE>,
    internal val stringFormat: StringFormat
) : MessageRepository<MESSAGE> {
    override suspend fun createRelation(
        remoteMessageId: MESSAGE,
        mxEventId: EventId
    ): Unit = databaseFactory.useDatabase { database ->
        val encoded = stringFormat.encodeToString(serializer, remoteMessageId)
        val inserted = database.messageQueries.createRelation(mxEventId, encoded)
            .awaitAsOne()
        check(inserted >= 0L)
        if (inserted != 0L) return@useDatabase
        // as only insertion is used, idempotency does not require explicit transaction
        val conflicted = database.messageQueries.getByConflict(mxEventId, encoded).awaitAsOne()
        if (conflicted.eventId == mxEventId && conflicted.remoteId == encoded) return@useDatabase
        error("Constraint violation! Tried to insert Message(eventId=$mxEventId, remoteId=$encoded), but $conflicted is already in database")
    }

    override suspend fun getMessageEventId(id: MESSAGE): EventId? = databaseFactory.useDatabase { database ->
        database.messageQueries.getEventId(stringFormat.encodeToString(serializer, id)).awaitAsOneOrNull()
    }

    override suspend fun getMessageEventId(id: EventId): MESSAGE? = databaseFactory.useDatabase { database ->
        database.messageQueries.getRemoteId(id).awaitAsOneOrNull()
            ?.let { stringFormat.decodeFromString(serializer, it) }
    }
}