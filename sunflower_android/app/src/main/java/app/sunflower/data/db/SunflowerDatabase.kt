package app.sunflower.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.sunflower.security.KeyVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, SystemPromptEntity::class, ModelEntity::class],
    version = 9,
    exportSchema = true,
)
abstract class SunflowerDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    abstract fun messages(): MessageDao

    abstract fun systemPrompts(): SystemPromptDao

    abstract fun models(): ModelDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            // Overwrite deleted rows with zeros instead of leaving them in free pages.
                            db.query("PRAGMA secure_delete = ON").close()
                        }
                    },
                ).build()
        }

        /** v2 adds the model library. Written by hand: v1 shipped before schemas were exported. */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `models` (" +
                            "`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `uri` TEXT NOT NULL, " +
                            "`localPath` TEXT, `sizeBytes` INTEGER NOT NULL, `name` TEXT, " +
                            "`architecture` TEXT, `sizeLabel` TEXT, `quantization` TEXT, " +
                            "`contextLength` INTEGER, `layerCount` INTEGER, " +
                            "`hasChatTemplate` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                            "`lastBackend` TEXT, `failedBackends` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }

        /** v3 stores per-model settings. */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `models` ADD COLUMN `settings` TEXT")
                }
            }

        /** v4: built-in MTP detection and speculative-decoding stats per reply. */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `models` ADD COLUMN `nextnLayers` INTEGER")
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `draftTokens` INTEGER")
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `draftAccepted` INTEGER")
                }
            }

        /** v5: a saved system prompt can be the default for new chats. */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `system_prompts` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v6: speculative replies record their speed relative to plain generation. */
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `speedupVsPlain` REAL")
                }
            }

        /** v7: time spent reasoning, for "Thought for 12 s". */
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `thinkingMs` INTEGER")
                }
            }

        /** v8: conversations remember their model, to offer it again. */
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `modelId` TEXT")
                }
            }

        /** v9: several versions of the last exchange (regenerate, edit), one shown at a time. */
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `turnId` TEXT")
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `variant` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `messages` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
                }
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
