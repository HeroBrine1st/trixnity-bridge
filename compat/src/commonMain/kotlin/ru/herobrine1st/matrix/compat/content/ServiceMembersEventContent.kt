package ru.herobrine1st.matrix.compat.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

// https://github.com/matrix-org/matrix-spec-proposals/pull/4171
// Already being used by element https://github.com/element-hq/element-meta/blob/develop/spec/functional_members.md
@Serializable
public data class ServiceMembersEventContent(
    @SerialName("service_members") val serviceMembers: List<UserId>
) : StateEventContent {
    @SerialName("external_url")
    override val externalUrl: String? = null
}
