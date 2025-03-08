package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.database.Database

public interface DatabaseFactory {
    public suspend fun <T> useDatabase(block: suspend (database: Database) -> T): T
}