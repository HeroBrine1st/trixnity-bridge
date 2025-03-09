package ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.internal

import app.cash.sqldelight.ColumnAdapter
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

internal object EventIdAdapter : ColumnAdapter<EventId, String> {
    override fun decode(databaseValue: String) = EventId(databaseValue)

    override fun encode(value: EventId) = value.full
}

internal object RoomIdAdapter : ColumnAdapter<RoomId, String> {
    override fun decode(databaseValue: String) = RoomId(databaseValue)

    override fun encode(value: RoomId) = value.full
}

internal object UserIdAdapter : ColumnAdapter<UserId, String> {
    override fun decode(databaseValue: String) = UserId(databaseValue)

    override fun encode(value: UserId) = value.full
}