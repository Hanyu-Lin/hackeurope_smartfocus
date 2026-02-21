package locked.`in`.service

import android.util.LruCache
import locked.`in`.domain.model.ParsedNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParsedNotificationCache @Inject constructor() {
    private val cache = LruCache<String, ParsedNotification>(100)

    fun put(key: String, parsed: ParsedNotification) {
        cache.put(key, parsed)
    }

    fun get(key: String): ParsedNotification? = cache.get(key)
}
