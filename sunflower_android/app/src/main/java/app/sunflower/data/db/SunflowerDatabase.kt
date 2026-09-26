package app.sunflower.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.sunflower.security.KeyVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, SystemPromptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SunflowerDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    abstract fun messages(): MessageDao

    abstract fun systemPrompts(): SystemPromptDao

    companion object {
        private const val FILE_NAME = "sunflower.db"

        /**
         * Opens the SQLCipher database: the whole file (pages, indices, schema)
         * is AES-256 encrypted with per-page HMAC-SHA512 authentication.
         */
        fun open(
            context: Context,
            vault: KeyVault,
        ): SunflowerDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(rawKeyLiteral(vault.databaseKey()))
            return Room
                .databaseBuilder(context.applicationContext, SunflowerDatabase::class.java, FILE_NAME)
                .openHelperFactory(factory)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            // Overwrite deleted rows with zeros instead of leaving them in free pages.
                            db.query("PRAGMA secure_delete = ON").close()
                        }
                    },
                ).build()
        }

        /**
         * SQLCipher treats a key of the form x'<64 hex chars>' as the raw 256-bit
         * encryption key, skipping PBKDF2 (which only adds value for human passwords).
         */
        private fun rawKeyLiteral(key: ByteArray): ByteArray {
            val hex = key.joinToString("") { "%02x".format(it) }
            key.fill(0)
            return "x'$hex'".toByteArray(Charsets.US_ASCII)
        }
    }
}
