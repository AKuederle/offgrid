package com.example.udpservice.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.udpservice.send.DeliveryStatus

/**
 * Type converters for Room database.
 */
class Converters {
    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String = status.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}

/**
 * Room database singleton for packet persistence.
 *
 * Uses application context to survive service recreation after system kill.
 * Thread-safe singleton with double-checked locking.
 */
@Database(
    entities = [PacketEntity::class, OutboundMessageEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PacketDatabase : RoomDatabase() {

    /**
     * Get the PacketDao for database operations.
     */
    abstract fun packetDao(): PacketDao

    /**
     * Get the OutboundMessageDao for outbound message operations.
     */
    abstract fun outboundMessageDao(): OutboundMessageDao

    companion object {
        private const val DATABASE_NAME = "packet_database.db"

        /**
         * Migration from version 1 to 2: Add outbound_messages table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS outbound_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        peerHost TEXT NOT NULL,
                        peerPort INTEGER NOT NULL,
                        payload BLOB NOT NULL,
                        status TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        deliveredAt INTEGER
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_outbound_messages_peerHost_peerPort_status
                    ON outbound_messages(peerHost, peerPort, status)
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_outbound_messages_status_lastAttemptAt
                    ON outbound_messages(status, lastAttemptAt)
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_outbound_messages_createdAt
                    ON outbound_messages(createdAt)
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: PacketDatabase? = null

        /**
         * Get or create the database singleton.
         * Always uses applicationContext internally for lifecycle safety.
         *
         * @param context Any context (will be converted to applicationContext)
         * @return The singleton database instance
         */
        fun getInstance(context: Context): PacketDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PacketDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        /**
         * Clear the singleton instance (for testing only).
         */
        internal fun clearInstance() {
            instance = null
        }
    }
}
