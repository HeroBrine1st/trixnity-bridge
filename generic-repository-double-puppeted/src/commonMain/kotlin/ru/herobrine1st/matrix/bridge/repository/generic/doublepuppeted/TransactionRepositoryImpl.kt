package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.repository.TransactionRepository
import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal.DatabaseFactory

public class TransactionRepositoryImpl internal constructor(
    internal val databaseFactory: DatabaseFactory
) : TransactionRepository {
    @OptIn(DelicateCoroutinesApi::class)
    private val workaroundForSqldelightFailingToDisableThreadCheckInAsyncEnvironment =
        newFixedThreadPoolContext(1, "Transaction confinement thread")

    override suspend fun isTransactionProcessed(txnId: String): Boolean = databaseFactory.useDatabase { database ->
        database.transactionQueries.isTransactionProcessed(txnId).awaitAsOne()
    }

    override suspend fun onTransactionProcessed(txnId: String): Unit = databaseFactory.useDatabase { database ->
        withContext(workaroundForSqldelightFailingToDisableThreadCheckInAsyncEnvironment) {
            /*
            That's the reason:
                internal fun checkThreadConfinement() = check(ownerThreadId == currentThreadId()) {
                  """
                    Transaction objects (`TransactionWithReturn` and `TransactionWithoutReturn`) must be used
                    only within the transaction lambda scope.
                  """.trimIndent()
                }
            They simply don't disable that with R2DBC.
             */
            database.transactionQueries.onTransactionProcessed(txnId)
        }
    }

    override suspend fun getHandledEventsInTransaction(
        txnId: String
    ): Collection<EventId> = databaseFactory.useDatabase { database ->
        database.transactionQueries.getHandledEventsInTransaction(txnId).awaitAsList()
    }

    override suspend fun onEventHandled(txnId: String, eventId: EventId): Unit =
        databaseFactory.useDatabase { database ->
        database.transactionQueries.onEventHandled(txnId, eventId)
    }
}