package ru.herobrine1st.matrix.bridge.exception

/**
 * Exception for cases when event is not handled (e.g. due to error) and user should be notified about that.
 *
 * @param message to be shown to user
 */
public class UnhandledEventException(message: String, cause: Throwable? = null): EventHandlingException(message, cause)