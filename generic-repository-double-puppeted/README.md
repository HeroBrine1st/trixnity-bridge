This is a generic repository implementation for double-puppeted (non-personal) bridges.
It uses `kotlinx.serialization` to convert generic IDs to String and back.

Currently, this implementation doesn't support adding actors programmatically. Also, untested.

# Usage

Add R2DBC drivers. Only PostgreSQL is supported, other DBMS will likely fail:

```
// libs.versions.toml
r2dbc-postgresql = {module="org.postgresql:r2dbc-postgresql", version="1.0.7.RELEASE"}
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
val repositorySet = createR2DBCRepositorySet<RemoteActorId, RemoteUserId, RemoteRoomId, RemoteMessageId> {
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
}
```

(Remote...Id are your own types)

Support for adding actors programmatically is coming soon™.  
SQLite support across JVM and Native was attempted, but SQLDelight doesn't support async SQLite.

