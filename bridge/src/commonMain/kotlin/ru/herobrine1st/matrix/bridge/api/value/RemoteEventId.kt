package ru.herobrine1st.matrix.bridge.api.value

import kotlin.jvm.JvmInline

/**
 * This value is used as txnId when sending room events. Treated as opaque value.
 *
 * @param value opaque id of an event. This value MUST be unique for each event and SHOULD be the same for the same event.
 */
@JvmInline
public value class RemoteEventId(public val value: String)