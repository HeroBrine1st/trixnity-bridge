package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import ru.herobrine1st.matrix.bridge.repository.RepositorySet
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.*


public fun <ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> createRepositories(
    databaseFactory: DatabaseFactory,
    actorIdSerializer: KSerializer<ACTOR>,
    puppetIdSerializer: KSerializer<USER>,
    roomIdSerializer: KSerializer<ROOM>,
    messageIdSerializer: KSerializer<MESSAGE>,
    stringFormat: StringFormat = Json.Default
): RepositorySet<ACTOR, USER, ROOM, MESSAGE> = RepositorySet(
    ActorRepositoryImpl(databaseFactory, actorIdSerializer, stringFormat),
    MessageRepositoryImpl(databaseFactory, messageIdSerializer, stringFormat),
    PuppetRepositoryImpl(databaseFactory, puppetIdSerializer, stringFormat),
    RoomRepositoryImpl(databaseFactory, actorIdSerializer, roomIdSerializer, stringFormat),
    TransactionRepositoryImpl(databaseFactory)
)