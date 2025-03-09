package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import ru.herobrine1st.matrix.bridge.repository.RepositorySet
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.*


public fun <ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any, ACTOR_DATA> createRepositories(
    databaseFactory: DatabaseFactory,
    actorIdSerializer: KSerializer<ACTOR>,
    puppetIdSerializer: KSerializer<USER>,
    roomIdSerializer: KSerializer<ROOM>,
    messageIdSerializer: KSerializer<MESSAGE>,
    actorDataSerializer: KSerializer<ACTOR_DATA>,
    stringFormat: StringFormat = Json.Default
): Pair<RepositorySet<ACTOR, USER, ROOM, MESSAGE>, ActorProvisionRepository<ACTOR, ACTOR_DATA>> = RepositorySet(
    ActorRepositoryImpl(databaseFactory, actorIdSerializer, stringFormat),
    MessageRepositoryImpl(databaseFactory, messageIdSerializer, stringFormat),
    PuppetRepositoryImpl(databaseFactory, puppetIdSerializer, stringFormat),
    RoomRepositoryImpl(databaseFactory, actorIdSerializer, roomIdSerializer, stringFormat),
    TransactionRepositoryImpl(databaseFactory)
) to ActorProvisionRepositoryImpl(databaseFactory, actorIdSerializer, actorDataSerializer, stringFormat)