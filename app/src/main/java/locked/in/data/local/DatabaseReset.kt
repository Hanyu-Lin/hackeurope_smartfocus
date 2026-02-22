package locked.`in`.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears all Room tables. Use for debug/reset or when the user explicitly requests a full reset.
 *
 * **Manual alternatives (no code):**
 * - **Clear app data:** Settings → Apps → Smart Focus → Storage → Clear data
 * - **Uninstall and reinstall** the app
 */
@Singleton
class DatabaseReset @Inject constructor(
    private val database: AppDatabase
) {

    /**
     * Deletes all rows in all Room-managed tables. Runs on the IO dispatcher.
     * Does not delete the database file; tables remain and can be written to again.
     */
    suspend fun clearAllTables() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
