package ru.herobrine1st.matrix.bridge.repository

/**
 * A helper class to reduce number of arguments.
 */
// Is merging all interfaces via inheritance (and probably delegation) really a violation of SRP or simply another approach?
public data class AppServiceWorkerRepositorySet<ACTOR : Any, USER : Any, ROOM : Any, MESSAGE : Any>(
    val actorRepository: ActorRepository<ACTOR>,
    val messageRepository: MessageRepository<MESSAGE>,
    val puppetRepository: PuppetRepository<USER>,
    val roomRepository: RoomRepository<ACTOR, ROOM>,
    val transactionRepository: TransactionRepository,
)
