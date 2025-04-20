This project is not in any way affiliated with [Trixnity](https://trixnity.gitlab.io/trixnity/) library, except that it
is directly based on it.

# Trixnity Bridge

A work-in-progress next-generation microframework for writing matrix bridges
using [AS API](https://spec.matrix.org/latest/application-service-api/). Its purpose is to outline a general bridge
algorithm.

This framework features one ability many others lack: it does not interfere with your code. You
provide a framework with a stream of events from bridged service and framework provides you with a stream of events from
the matrix. The framework then does the heavy lifting of bridging the two together. It runs on Ktor, another
microframework, allowing you to even create user-facing preferences to bridge particular rooms of user's choice.

What makes this framework next-generation is this set of features:

- Total type-safety via generic identifiers (bring your own types)
- Generic repositories (bring your own storage, even flat-file database)
- Robust failure recovery via full idempotency (it even recovers after programmer-made code errors*)
- Usage of [MSC4171: Service members](https://github.com/matrix-org/matrix-spec-proposals/pull/4171)
- No room aliases - allows for automatic room name generation via heroes**
- Statelessness as it is even possible - the only state saved are ID mappings and handled transaction IDs, as well as
  registered actors.
- A strict mode disabling automatic room and user provision (by implementing MappingRemoteWorker instead of BasicRemoteWorker)
- \[TODO\] Support for webhook-based bridges (this is easy because we only need to pass event consumer earlier than it is currently (currently via Flow, where consumer is FlowCollector))

\* You must fix errors yourself of course, but then you can restart the bridge and it will continue where it crashed.
You must also satisfy the idempotency contract.  
\*\* Currently alias is created (and then removed) on room provision as idempotency measure, but its usage will be
removed entirely via custom state events that will also allow for partial data recovery and migration.  

The only drawback is slightly harder development and more boilerplate.

There is a reference [generic repository implementation](generic-repository-doublepuppeted) that allows for great
boilerplate size decrease.

# Examples

Currently none. Stay tuned!

However, it is possible to show the main feature of this framework. Most of your code will be an implementation of this
interface:

```kotlin
public interface BasicRemoteWorker<ACTOR, USER, ROOM, MESSAGE> {
    // a stream of events from matrix
    public suspend fun EventHandlerScope<MESSAGE>.handleEvent(
        actorId: ACTOR,
        roomId: ROOM,
        event: ClientEvent.RoomEvent<*>
    )
    
    // a stream of events from bridged service
    public fun getEvents(actorId: ACTOR): Flow<BasicRemoteWorker.Event<USER, ROOM, MESSAGE>>

    // a method to get information about particular user on bridged service
    public suspend fun getUser(actorId: ACTOR, id: USER): RemoteUser<USER>

    // a method to get information about particular room on bridged service
    public suspend fun getRoom(actorId: ACTOR, id: ROOM): RemoteRoom<USER, ROOM>

    // a method to get all users in particular room on bridged service
    public fun getRoomMembers(actorId: ACTOR, remoteId: ROOM): Flow<Pair<USER, RemoteUser<USER>?>>
}
```

# Bridge implementations

Currently none, but Telegram bridge is planned. Stay tuned!

# Copyright notice

```
Trixnity-bridge is a Matrix bridge microframework library
Copyright (C) 2025 HeroBrine1st Erquilenne <trixnity-bridge@herobrine1st.ru>

This library is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this library. If not, see <https://www.gnu.org/licenses/>.
```
