package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

public interface ActorProvisionRepository<ACTOR : Any, DATA> {
    /**
     * @return All available actors
     */
    public suspend fun getAllActors(): List<ActorWithData<ACTOR, DATA>>

    /**
     * @return Data associated with actor
     * @throws NullPointerException if there's no actor with such [id]
     */
    public suspend fun getActorData(id: ACTOR): DATA

    /**
     * Adds actor with associated data to repository
     */
    public suspend fun addActor(id: ACTOR, data: DATA)

    /**
     * Updates data associated to actor
     *
     * @throws IllegalStateException if there's no actor with such [id]
     */
    public suspend fun updateActor(id: ACTOR, data: DATA)

    /**
     * Removes actor given its id
     *
     * @throws IllegalStateException if there's no actor with such [id]
     */
    public suspend fun remoteActor(id: ACTOR)
}

public data class ActorWithData<ACTOR : Any, DATA>(
    public val id: ACTOR,
    public val data: DATA
)

internal class ActorProvisionRepositoryImpl<ACTOR : Any, DATA>(
    private val databaseFactory: DatabaseFactory,
    private val idSerializer: KSerializer<ACTOR>,
    private val dataSerializer: KSerializer<DATA>,
    private val stringFormat: StringFormat
) : ActorProvisionRepository<ACTOR, DATA> {
    override suspend fun getAllActors(): List<ActorWithData<ACTOR, DATA>> = databaseFactory.useDatabase { database ->
        database.actorQueries.getAll(mapper = { id, data ->
            ActorWithData(
                stringFormat.decodeFromString(idSerializer, id),
                stringFormat.decodeFromString(dataSerializer, data)
            )
        }).awaitAsList()
    }

    override suspend fun getActorData(id: ACTOR): DATA = databaseFactory.useDatabase { database ->
        database.actorQueries
            .getActorData(stringFormat.encodeToString(idSerializer, id))
            .awaitAsOne()
            .let {
                stringFormat.decodeFromString(dataSerializer, it)
            }
    }

    override suspend fun addActor(id: ACTOR, data: DATA) = databaseFactory.useDatabase { database ->
        database.actorQueries.add(
            stringFormat.encodeToString(idSerializer, id),
            stringFormat.encodeToString(dataSerializer, data)
        )
    }

    override suspend fun updateActor(id: ACTOR, data: DATA) = databaseFactory.useDatabase { database ->
        database.actorQueries.update(
            stringFormat.encodeToString(dataSerializer, data),
            stringFormat.encodeToString(idSerializer, id)
        )
            .awaitAsList().size.let { check(it > 0) { "No rows updated, is $id a real actor id?" } }
    }

    override suspend fun remoteActor(id: ACTOR) = databaseFactory.useDatabase { database ->
        database.actorQueries.remove(stringFormat.encodeToString(idSerializer, id))
            .awaitAsList().size.let { check(it > 0) { "No rows deleted, is $id a real actor id?" } }
    }
}