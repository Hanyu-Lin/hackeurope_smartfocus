package locked.`in`.service

import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.domain.model.NotificationOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestGenerator @Inject constructor() {

    fun generate(records: List<NotificationRecordEntity>): String {
        if (records.isEmpty()) return "No notifications during this session"

        val suppressed = records.filter { it.outcome == NotificationOutcome.SUPPRESSED.name }
        val bundled = records.filter { it.outcome == NotificationOutcome.BUNDLED.name }

        val sb = StringBuilder()
        sb.appendLine("Focus Session Summary")
        sb.appendLine("---------------------")
        sb.appendLine("${records.size} notifications total")
        sb.appendLine("${suppressed.size} suppressed, ${bundled.size} bundled")

        // Group bundled by bundleId
        val bundleGroups = bundled.groupBy { it.bundleId ?: "unknown" }
        if (bundleGroups.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Bundled:")
            for ((bundleId, members) in bundleGroups) {
                val apps = members.map { it.appLabel }.distinct()
                val label = if (apps.size > 1) {
                    apps.joinToString(" \u00b7 ") + " \u00b7 ${members.size} notifications"
                } else {
                    "${apps.first()} \u00b7 ${members.size} notifications"
                }
                sb.appendLine("  $label")
                val preview = members.maxByOrNull { it.timestamp }
                if (preview != null) {
                    sb.appendLine("    Latest: ${preview.title} - ${preview.text.take(80)}")
                }
            }
        }

        // Suppressed individually
        val highPriority = suppressed.filter { (it.priorityScore ?: 0f) >= 5f }
        val lowPriority = suppressed.filter { (it.priorityScore ?: 0f) < 5f }

        if (highPriority.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Might be important:")
            for (r in highPriority.sortedByDescending { it.priorityScore }.take(5)) {
                sb.appendLine("  ${r.appLabel}: ${r.title}")
            }
        }

        if (lowPriority.isNotEmpty()) {
            sb.appendLine()
            if (lowPriority.size <= 3) {
                sb.appendLine("Low priority:")
                for (r in lowPriority) {
                    sb.appendLine("  ${r.appLabel}: ${r.title}")
                }
            } else {
                sb.appendLine("and ${lowPriority.size} other low-priority notifications")
            }
        }

        return sb.toString().trimEnd()
    }
}
