package ru.herobrine1st.matrix.bridge.api

public fun interface ErrorNotifier {
    /**
     * This method is called when [ru.herobrine1st.matrix.bridge.worker.AppServiceWorker] got an internal error
     * that interferes with its normal work.
     *
     * This method should notify monitoring systems instantly, as [ru.herobrine1st.matrix.bridge.worker.AppServiceWorker] attempts to
     * restore its normal operation automatically.
     */
    public fun notify(message: String, cause: Throwable?)
}