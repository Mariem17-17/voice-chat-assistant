package com.example.ai_voice_assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [ChatEntity::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                // Generate/Get secure key from Keystore
                // Corrected: SecurityManager requires context
                val securityManager = SecurityManager(context)
                val passphrase = securityManager.getDatabaseKey()
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_history.db"
                ).openHelperFactory(factory)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
