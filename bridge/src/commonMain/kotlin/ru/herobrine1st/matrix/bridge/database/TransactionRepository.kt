package ru.herobrine1st.matrix.bridge.database

import net.folivo.trixnity.core.model.EventId

/**
 * This repository is an abstraction for those kinds of questions:
 *
 * - Did this bridge handle incoming transaction?
 * - If so, which events are not handled yet?
 *
 * It is used as short-circuit and not main way to ensure idempotency,
 * therefore it can be mocked to always return false/empty collection, which is not recommended but possible.
 */
public interface TransactionRepository {
    /**
     * This method returns true if transaction is already fully processed.
     *
     * Once it is true, it stays true permanently.
     *
     * @param txnId transaction id
     * @return boolean value indicating if this transaction was already fully processed
     */
    public suspend fun isTransactionProcessed(txnId: String): Boolean

    /**
     * This method is executed at the end of transaction, meaning transaction is fully processed
     * and [isTransactionProcessed] MUST return true for this [txnId] from now on.
     *
     * It is recommended to clear (forget) all handled events in this transaction.
     *
     * @param txnId transaction id
     */
    public suspend fun onTransactionProcessed(txnId: String)

    /**
     * This method returns all handled events in transaction. It returns empty list if transaction is not processed
     * as meant by [onTransactionProcessed] AND no events are handled as meant by [onEventHandled] (i.e. if transaction [txnId] is
     * unknown to this repository).
     *
     * Once [onTransactionProcessed] with particular [txnId] is called, the behavior of this method
     * for this particular [txnId] is undefined.
     *
     * @param txnId transaction id
     * @return collection of events already processed in this transaction. It is recommended to return collections with
     * O(1) search time (i.e. HashSet).
     */
    public suspend fun getHandledEventsInTransaction(txnId: String): Collection<EventId>


    /**
     * This method is executed after event is handled completely, meaning [getHandledEventsInTransaction] MUST
     * include [eventId] if executed with the same [txnId].
     *
     * @param txnId transaction id
     * @param eventId event id
     */
    public suspend fun onEventHandled(txnId: String, eventId: EventId)


}