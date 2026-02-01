package com.example.udpservice.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database singleton for packet persistence.
 *
 * Uses application context to survive service recreation after system kill.
 * Thread-safe singleton with double-checked locking.
 */
@Database(
    entities = [PacketEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PacketDatabase : RoomDatabase() {

    /**
     * Get the PacketDao for database operations.
     */
    abstract fun packetDao(): PacketDao

    companion object {
        private const val DATABASE_NAME = "packet_database.db"

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
                    .fallbackToDestructiveMigration()
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
