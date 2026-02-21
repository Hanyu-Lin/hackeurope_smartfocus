package locked.`in`.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import locked.`in`.data.local.dao.FocusSessionDao
import locked.`in`.data.local.dao.NotificationDao
import locked.`in`.data.local.entity.FocusSessionEntity
import locked.`in`.data.local.entity.NotificationEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS index_notifications_sbn_key")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_sbn_key ON notifications(sbn_key)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS focus_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                allowed_count INTEGER NOT NULL,
                suppressed_count INTEGER NOT NULL,
                digest_text TEXT NOT NULL
            )"""
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notifications ADD COLUMN sender_name TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN conversation_name TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN style_type TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN has_reply_action INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notifications ADD COLUMN has_attachment INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notifications ADD COLUMN is_important INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notifications ADD COLUMN is_group_summary INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notifications ADD COLUMN channel_id TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN group_key TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN model_input TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notifications ADD COLUMN embedding BLOB DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Removed columns from NotificationEntity: is_contact, style_type, has_reply_action,
        // has_attachment, is_important, is_group_summary, channel_id, group_key, embedding.
        // SQLite pre-3.35 (API <34) doesn't support DROP COLUMN, so recreate the table.
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS notifications_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sbn_key TEXT NOT NULL,
                app_package TEXT NOT NULL,
                app_name TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                label TEXT NOT NULL,
                confidence_score REAL NOT NULL,
                reason TEXT NOT NULL,
                bundle_id TEXT DEFAULT NULL,
                is_allowed INTEGER NOT NULL,
                is_restored INTEGER NOT NULL DEFAULT 0,
                is_group_chat INTEGER NOT NULL DEFAULT 0,
                has_mention INTEGER NOT NULL DEFAULT 0,
                notification_category TEXT DEFAULT NULL,
                focus_session_id TEXT DEFAULT NULL,
                sender_name TEXT DEFAULT NULL,
                conversation_name TEXT DEFAULT NULL,
                model_input TEXT DEFAULT NULL
            )"""
        )
        db.execSQL(
            """INSERT INTO notifications_new (
                id, sbn_key, app_package, app_name, title, body, timestamp,
                label, confidence_score, reason, bundle_id, is_allowed, is_restored,
                is_group_chat, has_mention, notification_category, focus_session_id,
                sender_name, conversation_name, model_input
            ) SELECT
                id, sbn_key, app_package, app_name, title, body, timestamp,
                label, confidence_score, reason, bundle_id, is_allowed, is_restored,
                is_group_chat, has_mention, notification_category, focus_session_id,
                sender_name, conversation_name, model_input
            FROM notifications"""
        )
        db.execSQL("DROP TABLE notifications")
        db.execSQL("ALTER TABLE notifications_new RENAME TO notifications")

        // Recreate indices
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_sbn_key ON notifications(sbn_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_app_package ON notifications(app_package)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_timestamp ON notifications(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_label ON notifications(label)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_bundle_id ON notifications(bundle_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_is_allowed ON notifications(is_allowed)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_focus_session_id ON notifications(focus_session_id)")
    }
}

@Database(
    entities = [NotificationEntity::class, FocusSessionEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun focusSessionDao(): FocusSessionDao
}
