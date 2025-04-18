package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted

public data class RemoteWorkerRepositorySet<ACTOR : Any, ACTOR_DATA, USER : Any>(
    val actorProvisionRepository: ActorProvisionRepository<ACTOR, ACTOR_DATA>,
    val userMappingRepository: UserMappingRepository<USER>,
)