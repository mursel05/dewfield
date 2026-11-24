package dev.mursal.dewfield.app

/** A notification for the platform to show at [atMillis] (wall-clock time), already translated. */
data class Notice(val id: Int, val atMillis: Long, val title: String, val text: String)

/**
 * Local notifications. The Android launcher implements it with AlarmManager; the desktop build
 * has none. The game calls it from the render thread, and callbacks come back on that thread.
 */
interface Notifier {
    /** False where the platform has no notifications: the game then never offers reminders. */
    val available: Boolean

    /** True if the system lets the game post notifications right now. */
    fun allowed(): Boolean

    /**
     * Asks the system for permission (Android 13 and newer show a dialog). If the player turned
     * notifications off for good, this opens the system settings instead. [done] gets the answer.
     */
    fun requestPermission(done: (Boolean) -> Unit)

    /** Replaces every pending reminder with [notices]. */
    fun schedule(notices: List<Notice>)

    /** Cancels pending reminders and clears the ones already shown. */
    fun cancelAll()

    /** The desktop build and tests. */
    object None : Notifier {
        override val available = false
        override fun allowed() = false
        override fun requestPermission(done: (Boolean) -> Unit) = done(false)
        override fun schedule(notices: List<Notice>) = Unit
        override fun cancelAll() = Unit
    }
}
