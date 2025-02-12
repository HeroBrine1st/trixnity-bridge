package ru.herobrine1st.matrix.compat

import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.messageOf
import net.folivo.trixnity.core.serialization.events.stateOf
import ru.herobrine1st.matrix.compat.content.ServiceMembersEventContent
import ru.herobrine1st.matrix.compat.content.StickerEventContent


public val StickerEventContentSerializerMappings: EventContentSerializerMappings
    get() = createEventContentSerializerMappings {
        messageOf<StickerEventContent>("m.sticker")
    }

public val ServiceMembersContentSerializerMappings: EventContentSerializerMappings
    get() = createEventContentSerializerMappings {
        stateOf<ServiceMembersEventContent>("io.element.functional_members")
    }