package ru.herobrine1st.matrix.compat.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.ImageInfo

@Serializable
public data class StickerEventContent(
    @SerialName("body")
    val body: String,
    @SerialName("info")
    val info: ImageInfo,
    @SerialName("url")
    val url: String,
    @SerialName("external_url")
    override val externalUrl: String?
): MessageEventContent {
    override val mentions: Mentions? = null
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo? = null
}
