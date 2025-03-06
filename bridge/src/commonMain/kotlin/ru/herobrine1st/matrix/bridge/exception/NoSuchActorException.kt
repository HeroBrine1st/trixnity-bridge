package ru.herobrine1st.matrix.bridge.exception

public class NoSuchActorException(message: String?, cause: Throwable?) : Exception(message, cause) {
    public constructor() : this(null, null)
    public constructor(message: String?) : this(message, null)
    public constructor(cause: Throwable?) : this(null, cause)
}