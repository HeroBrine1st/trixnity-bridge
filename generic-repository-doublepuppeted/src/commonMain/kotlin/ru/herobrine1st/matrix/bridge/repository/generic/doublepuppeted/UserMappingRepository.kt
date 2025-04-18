package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.api.worker.BasicRemoteWorker
import ru.herobrine1st.matrix.bridge.api.worker.MappingRemoteWorker
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

/**
 * This is intended to be used for [BasicRemoteWorker.getRoom], so that it can return at least one administrator of a room
 * and if there's none known, prevent room bridging until it is known. It is recommended though to ensure the bridge requester is
 * known to this repository.
 *
 * Using this with [BasicRemoteWorker.Event.Remote.Room.Create] (or its [MappingRemoteWorker] copy) allows to invite specifically the user requested the briding, allowing it not to be admin.
 *
 * All [UserId]s in this interface MUST NOT be controlled by the bridge, and instead MUST belong to real users. **Violating
 * this is** at best malfunctional bridge inviting puppets here and there, and generally **undefined behavior**.
 */
public interface UserMappingRepository<USER : Any> {
    /**
     * @return [UserId]s of users with known [UserId], and ignores users with unknown [UserId]
     */
    public suspend fun getMxIdOfSomeone(users: Collection<USER>): Map<USER, UserId>

    /**
     * @return [UserId] of user or null if not known
     */
    public suspend fun getMxId(id: USER): UserId?

    /**
     * This registers pair [remoteId]-[mxId] as registered from remote. This means that we can trust only [remoteId] part,
     * and so this method overrides [mxId] if it is changed.
     */
    public suspend fun registerPairFromRemote(remoteId: USER, mxId: UserId)

    /**
     * This registers pair [mxId]-[remoteId] as registered from matrix. This means that we can trust only [mxId] paart,
     * and so this method overrides [remoteId] if it is changed.
     */
    public suspend fun registerPairFromMatrix(mxId: UserId, remoteId: USER)
}

public class UserMappingRepositoryImpl<USER : Any>(
    private val databaseFactory: DatabaseFactory,
    private val idSerializer: KSerializer<USER>,
    private val stringFormat: StringFormat,
) : UserMappingRepository<USER> {
    override suspend fun getMxIdOfSomeone(users: Collection<USER>): Map<USER, UserId> =
        databaseFactory.useDatabase { database ->
            database.userMappingQueries.getMxIdOfSomeone(users.map { stringFormat.encodeToString(idSerializer, it) })
                .awaitAsList()
                .associate { (userId, remoteId) ->
                    stringFormat.decodeFromString(idSerializer, remoteId) to userId
                }
        }

    override suspend fun getMxId(id: USER): UserId? = databaseFactory.useDatabase { database ->
        database.userMappingQueries.getMxId(stringFormat.encodeToString(idSerializer, id)).awaitAsOneOrNull()
    }

    override suspend fun registerPairFromRemote(remoteId: USER, mxId: UserId) =
        databaseFactory.useDatabase { database ->
            database.userMappingQueries.registerPairFromRemote(
                stringFormat.encodeToString(idSerializer, remoteId),
                mxId
            )
        }

    override suspend fun registerPairFromMatrix(mxId: UserId, remoteId: USER) =
        databaseFactory.useDatabase { database ->
            database.userMappingQueries.registerPairFromMatrix(
                mxId,
                stringFormat.encodeToString(idSerializer, remoteId)
            )
        }
}