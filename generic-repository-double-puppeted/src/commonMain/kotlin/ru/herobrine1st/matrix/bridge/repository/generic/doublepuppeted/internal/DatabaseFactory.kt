package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.database.Database

internal interface DatabaseFactory {
    suspend fun <T> useDatabase(block: suspend (database: Database) -> T): T
}