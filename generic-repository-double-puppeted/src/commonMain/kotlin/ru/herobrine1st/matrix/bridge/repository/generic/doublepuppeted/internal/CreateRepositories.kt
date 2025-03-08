package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import ru.herobrine1st.matrix.bridge.repository.RepositorySet
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.*


internal inline fun <reified ACTOR : Any, reified USER : Any, reified ROOM : Any, reified MESSAGE : Any> createRepositories(
    databaseFactory: DatabaseFactory
): RepositorySet<ACTOR, USER, ROOM, MESSAGE> {
    val actorIdSerializer = serializer<ACTOR>()
    val puppetIdSerializer = serializer<USER>()
    val roomIdSerializer = serializer<ROOM>()
    val messageIdSerializer = serializer<MESSAGE>()
    val stringFormat = Json.Default

    val actorRepository = ActorRepositoryImpl(databaseFactory, actorIdSerializer, stringFormat)
    val messageRepository = MessageRepositoryImpl(databaseFactory, messageIdSerializer, stringFormat)
    val puppetRepository = PuppetRepositoryImpl(databaseFactory, puppetIdSerializer, stringFormat)
    val roomRepository = RoomRepositoryImpl(databaseFactory, actorIdSerializer, roomIdSerializer, stringFormat)
    val transactionRepository = TransactionRepositoryImpl(databaseFactory)

    return RepositorySet(actorRepository, messageRepository, puppetRepository, roomRepository, transactionRepository)
}