package locked.`in`.domain.model

enum class SupportedApp(
    val displayName: String,
    val packageNames: List<String>
) {
    PHONE("Phone", listOf("com.android.phone", "com.google.android.dialer")),
    SMS("Messages", listOf("com.google.android.apps.messaging", "com.android.mms")),
    GMAIL("Gmail", listOf("com.google.android.gm")),
    LINKEDIN("LinkedIn", listOf("com.linkedin.android")),
    INSTAGRAM("Instagram", listOf("com.instagram.android")),
    DISCORD("Discord", listOf("com.discord"));

    companion object {
        fun fromValue(value: String): SupportedApp? =
            entries.firstOrNull { app ->
                app.name.equals(value, ignoreCase = true) ||
                    app.packageNames.any { it.equals(value, ignoreCase = true) }
            }
    }
}
