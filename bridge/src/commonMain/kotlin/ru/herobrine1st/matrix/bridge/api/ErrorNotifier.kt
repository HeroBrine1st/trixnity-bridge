package ru.herobrine1st.matrix.bridge.api


public fun interface ErrorNotifier {
    @Deprecated(level = DeprecationLevel.WARNING, message = "Use other overload")
    public fun notify(message: String, cause: Throwable?): Unit = notify(message, cause, cause != null)

    /**
     * This method is called when [ru.herobrine1st.matrix.bridge.worker.AppServiceWorker] got an internal error
     * that interferes with its normal work.
     *
     * This method should notify monitoring systems instantly, as [ru.herobrine1st.matrix.bridge.worker.AppServiceWorker] attempts to
     * restore its normal operation automatically.
     *
     * @param isCritical if true, the bridge is probably rendered inoperable and is trying to recover. Otherwise, it is a normal warning about unexpected behavior.
     */
    public fun notify(message: String, cause: Throwable?, isCritical: Boolean)
}