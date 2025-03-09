This is a reference generic repository implementation for double-puppeted (non-personal) bridges, supporting only
PostgreSQL. Currently, it is untested.

This implementation uses `kotlinx.serialization` to convert generic IDs to String and back.

# Usage

Add R2DBC drivers. Only PostgreSQL is supported, other DBMS will likely fail:

```
// libs.versions.toml
r2dbc-postgresql = { module = "org.postgresql:r2dbc-postgresql", version = "1.0.7.RELEASE" }
r2dbc-pool = { module = "io.r2dbc:r2dbc-pool", version = "1.0.1.RELEASE" }

// dependencies
implementation(libs.r2dbc.postgresql)
implementation(libs.r2dbc.pool)
```

Set services in `resources/META-INF/services/io.r2dbc.spi.ConnectionFactoryProvider`:

```
io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
io.r2dbc.pool.PoolingConnectionFactoryProvider
```

Then use like that:

```kotlin
// Remote...Id are your own types. Each type should be @Serializable, or pass StringFormat with SerializersModule to add support
val (repositorySet, actorProvisionRepository) = createR2DBCRepositorySet<RemoteActorId, RemoteUserId, RemoteRoomId, RemoteMessageId, ActorData>(
    connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder().apply {
        option(DRIVER, POOLING_DRIVER)
        option(PROTOCOL, "postgresql")
        option(HOST, host)
        option(PORT, port)
        option(USER, username)
        option(PASSWORD, password)
        option(DATABASE, database)
        // other options if needed..
        option(MAX_IDLE_TIME, connectionIdleTime)
        option(MAX_SIZE, maxConnectionPoolSize)
    }.build())
)
```

Then pass resulting RepositorySet to AppServiceWorker. `actorProvisionRepository` allows to CRUD actors programmatically
and should be passed to RemoteWorker so that it can get actor data for each actor.  
You can also ignore storing actor data in database and use that only to add actor IDs. This may be useful if actor list
is rather constant (e.g. configuration file) and your actors access that directly.

# Why dedicated DBMS?

SQLite support across JVM and Native platforms was attempted, but SQLDelight doesn't support async SQLite. The same with
H2.  
And it was far too late to change everything back to blocking access.

Anyway, if you have installed synapse, you'll have no problem configuring postgres next to it - so it's a minor issue.

