package ru.herobrine1st.matrix.bridge.exception

/**
 * Generic exception for application-level errors in handling matrix events by remote worker.
 *
 * Application-level errors do not block bridge from working, e.g.:
 * - Too big message - this error is specific to one event, and it does make sense to skip it
 * (preferably notifying user of error via [UnhandledEventException])
 * - Permission denied - this error is, again, specific to event or room, again making sense to skip it due to error (or even undo if possible)
 * - Exceptions specific to some message types, e.g. remote worker used deprecated API for sending images and it is removed, but text messages are working.
 *
 * Examples of errors that block bridge:
 * - Network timeout: bridge couldn't send message, and it does not make sense to skip it
 * - Database errors: probably this problem spans multiple unrelated events
 * - Remote side API is deprecated and then removed, which renders remote worker inoperable
 */
public sealed class EventHandlingException(message: String?, cause: Throwable?): Exception(message, cause)
