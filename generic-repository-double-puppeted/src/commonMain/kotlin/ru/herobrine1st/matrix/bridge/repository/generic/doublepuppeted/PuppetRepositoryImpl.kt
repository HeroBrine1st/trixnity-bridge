package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.repository.PuppetRepository
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

public class PuppetRepositoryImpl<USER : Any> internal constructor(
    internal val databaseFactory: DatabaseFactory,
    internal val serializer: KSerializer<USER>,
    internal val stringFormat: StringFormat
) : PuppetRepository<USER> {
    override suspend fun getPuppetId(id: USER): UserId? = databaseFactory.useDatabase { database ->
        database.puppetQueries.getUserIdByRemoteId(stringFormat.encodeToString(serializer, id)).awaitAsOneOrNull()
    }

    override suspend fun getPuppetId(id: UserId): USER? = databaseFactory.useDatabase { database ->
        database.puppetQueries.getRemoteIdByUserId(id).awaitAsOneOrNull()
            ?.let { stringFormat.decodeFromString(serializer, it) }
    }

    override suspend fun createPuppet(mxId: UserId, remoteId: USER): Unit = databaseFactory.useDatabase { database ->
        database.puppetQueries.create(mxId, stringFormat.encodeToString(serializer, remoteId))
    }
}