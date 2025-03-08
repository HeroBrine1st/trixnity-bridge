package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.awaitQuery
import app.cash.sqldelight.driver.r2dbc.R2dbcPreparedStatement
import io.github.oshai.kotlinlogging.KotlinLogging
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions.Builder
import io.r2dbc.spi.ConnectionFactoryOptions.builder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.reactivestreams.Publisher
import ru.herobrine1st.matrix.bridge.repository.RepositorySet
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.database.Database
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.R2DBCDatabaseFactory
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.awaitCompletion
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.completeAnyway
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.createRepositories

private val logger = KotlinLogging.logger {}

public suspend fun <ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any> createR2DBCRepositorySet(
    actorIdSerializer: KSerializer<ACTOR>,
    puppetIdSerializer: KSerializer<USER>,
    roomIdSerializer: KSerializer<ROOM>,
    messageIdSerializer: KSerializer<MESSAGE>,
    stringFormat: StringFormat = Json.Default,
    options: Builder.() -> Unit = {},
): RepositorySet<ACTOR, USER, ROOM, MESSAGE> {
    val pool: Publisher<out Connection> = ConnectionFactories.get(builder().apply {
        options()
//        option(DRIVER, POOLING_DRIVER)
//        option(PROTOCOL, "postgresql")
//        option(HOST, host)
//        option(PORT, port)
//        option(USER, username)
//        option(PASSWORD, password)
//        option(DATABASE, database)
//        option(MAX_IDLE_TIME, connectionIdleTime)
//        option(MAX_SIZE, maxConnectionPoolSize)
    }.build()).create()

    val databaseFactory = R2DBCDatabaseFactory(pool)
    databaseFactory.getDriver().use {
        it.await(null, "CREATE TABLE IF NOT EXISTS metadata(version INTEGER NOT NULL)", 0)
        it.await(null, "INSERT INTO metadata(version) SELECT 0 WHERE NOT EXISTS (SELECT * FROM metadata)", 0)
        val version =
            it.awaitQuery(null, "SELECT version FROM metadata", mapper = { it.next().await();it.getLong(0)!! }, 0)
        logger.info { "Database version is $version, schema version is ${Database.Schema.version}" }
        if (version == Database.Schema.version) return@use
        it.connection.beginTransaction().awaitCompletion()
        try {
            if (version == 0L) {
                Database.Schema.awaitCreate(it)
            } else {
                Database.Schema.awaitMigrate(it, version, Database.Schema.version)
            }
            it.await(null, "UPDATE metadata SET version=$1", 1) {
                check(this is R2dbcPreparedStatement)
                bindLong(0, version)
            }
            it.connection.commitTransaction().awaitCompletion()
        } catch (_: Throwable) {
            it.connection.rollbackTransaction().completeAnyway()
        }
        logger.info { "Schema creation/migration complete" }
    }

    return createRepositories(
        databaseFactory, actorIdSerializer, puppetIdSerializer, roomIdSerializer, messageIdSerializer, stringFormat
    )
}

public suspend inline fun <reified ACTOR : Any, reified USER : Any, reified ROOM : Any, reified MESSAGE : Any> createR2DBCRepositorySet(
    stringFormat: StringFormat = Json.Default,
    noinline options: Builder.() -> Unit = {},
): RepositorySet<ACTOR, USER, ROOM, MESSAGE> = createR2DBCRepositorySet<ACTOR, USER, ROOM, MESSAGE>(
    serializer(), serializer(), serializer(), serializer(),
    stringFormat, options
)
