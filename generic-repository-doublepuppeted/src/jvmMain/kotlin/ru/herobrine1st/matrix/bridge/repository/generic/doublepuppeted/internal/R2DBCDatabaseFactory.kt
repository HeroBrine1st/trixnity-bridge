package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import io.r2dbc.spi.Connection
import org.reactivestreams.Publisher
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.database.*

internal class R2DBCDatabaseFactory(
    val connectionFactory: Publisher<out Connection>
) : DatabaseFactory {
    override suspend fun <T> useDatabase(block: suspend (Database) -> T): T {
        getDriver().use {
            val database = Database(
                driver = it,
                handledEventInTransactionAdapter = HandledEventInTransaction.Adapter(EventIdAdapter),
                messageAdapter = Message.Adapter(EventIdAdapter),
                puppetAdapter = Puppet.Adapter(UserIdAdapter),
                roomAdapter = Room.Adapter(RoomIdAdapter),
            )
            return block(database)
        }
    }

    internal suspend fun getDriver() = connectionFactory.awaitFirst().toDriver()

}