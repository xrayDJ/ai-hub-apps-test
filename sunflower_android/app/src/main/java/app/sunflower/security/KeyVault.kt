package app.sunflower.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the key that encrypts Sunflower's database.
 *
 * Two layers:
 *  - The database key: 256 random bits from [SecureRandom], handed to SQLCipher
 *    as a raw key (no passphrase stretching needed; it is already full-entropy).
 *  - The master key: AES-256-GCM, generated inside the Android Keystore and
 *    never exportable. It lives in StrongBox (a dedicated secure element) when
 *    the device has one, otherwise in the TEE. It only ever wraps the database
 *    key, which is stored wrapped in no-backup storage.
 *
 * Copying the app's files off the device is therefore useless without the
 * hardware that holds the master key.
 */
class KeyVault(context: Context) {
    private val appContext = context.applicationContext
    private val wrappedKeyFile = File(appContext.noBackupFilesDir, "vault/database.key")

    /** Returns the database key, creating and sealing a new one on first launch. */
    @Synchronized
    fun databaseKey(): ByteArray {
        if (wrappedKeyFile.exists()) {
            return unwrap(wrappedKeyFile.readBytes())
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        writeAtomically(wrap(key))
        return key
    }

    /** True when the device can hold keys in a StrongBox secure element. */
    val hasStrongBox: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        // Layout: [format version][iv length][iv][ciphertext + GCM tag]
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == FORMAT_VERSION) { "Unrecognised key vault format" }
        val ivLength = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLength)
        val sealed = blob.copyOfRange(2 + ivLength, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(sealed)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        return try {
            generateMasterKey(strongBox = hasStrongBox)
        } catch (e: StrongBoxUnavailableException) {
            Log.w(TAG, "StrongBox unavailable, falling back to TEE-backed key", e)
            generateMasterKey(strongBox = false)
        }
    }

    private fun generateMasterKey(strongBox: Boolean): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setIsStrongBoxBacked(strongBox)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun writeAtomically(bytes: ByteArray) {
        wrappedKeyFile.parentFile?.mkdirs()
        val tmp = File(wrappedKeyFile.parentFile, wrappedKeyFile.name + ".tmp")
        tmp.outputStream().use {
            it.write(bytes)
            it.fd.sync()
        }
        check(tmp.renameTo(wrappedKeyFile)) { "Could not persist key vault" }
    }

    private companion object {
        const val TAG = "KeyVault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "sunflower.master.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_BYTES = 32
        const val FORMAT_VERSION: Byte = 1
    }
}
