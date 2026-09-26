package app.sunflower.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.provider.OpenableColumns
import android.system.Os
import app.sunflower.data.db.ModelEntity
import app.sunflower.data.db.SunflowerDatabase
import app.sunflower.engine.GgufInfo
import app.sunflower.engine.GgufReader
import app.sunflower.engine.ModelSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A path the native runtime can open, valid until [close]. */
class ModelHandle(
    val path: String,
    private val onClose: () -> Unit,
) : Closeable {
    override fun close() = onClose()
}

class ModelLibrary(
    context: Context,
    private val database: suspend () -> SunflowerDatabase,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val modelsDir = File(appContext.filesDir, "models")

    fun observe(): Flow<List<ModelEntity>> = flow { emitAll(database().models().observeAll()) }

    suspend fun get(id: String): ModelEntity? = database().models().get(id)

    /** Adds a picked file to the library, reading its GGUF header. Re-importing the same file is a no-op. */
    suspend fun import(uri: Uri): ModelEntity =
        withContext(Dispatchers.IO) {
            val dao = database().models()
            dao.findByUri(uri.toString())?.let { return@withContext it }

            // Keep read access across restarts; the file stays where the user keeps it.
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val (fileName, size) = describe(uri)
            val info =
                try {
                    resolver.openInputStream(uri)?.use { GgufReader.read(it) } ?: error("Couldn't open the file")
                } catch (e: Exception) {
                    releasePermission(uri)
                    throw e
                }
            val model =
                ModelEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    uri = uri.toString(),
                    localPath = null,
                    sizeBytes = size,
                    name = info.name,
                    architecture = info.architecture,
                    sizeLabel = info.sizeLabel,
                    quantization = info.quantization,
                    contextLength = info.contextLength,
                    layerCount = info.layerCount,
                    hasChatTemplate = info.hasChatTemplate,
                    addedAt = System.currentTimeMillis(),
                    lastBackend = null,
                    failedBackends = "",
                )
            dao.upsert(model)
            model
        }

    /**
     * Opens the model for the native loader, in order of preference:
     *  1. the private copy, if the user made one;
     *  2. the file's real path, readable when "All files access" is granted;
     *  3. the picker's descriptor via /proc/self/fd, which some devices allow.
     * The descriptor stays open while the model is loaded.
     */
    suspend fun open(model: ModelEntity): ModelHandle =
        withContext(Dispatchers.IO) {
            model.localPath?.let { path ->
                if (File(path).canRead()) return@withContext ModelHandle(path) {}
            }
            val pfd: ParcelFileDescriptor =
                resolver.openFileDescriptor(Uri.parse(model.uri), "r")
                    ?: error("The file is no longer available")
            val fdPath = "/proc/self/fd/${pfd.fd}"
            val realPath = if (hasAllFilesAccess()) realPathOf(fdPath) else null
            ModelHandle(realPath ?: fdPath) { pfd.close() }
        }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /**
     * Asks the kernel where an open descriptor points. This works for every
     * document provider (Downloads, SD cards, media), unlike parsing their
     * document ids. FUSE mounts can report the internal /mnt/user/<id>/ view,
     * which apps can't open, so map it back to /storage/.
     */
    private fun realPathOf(fdPath: String): String? {
        val target = runCatching { Os.readlink(fdPath) }.getOrNull() ?: return null
        val candidates =
            listOf(
                target,
                target.replaceFirst(Regex("^/mnt/user/\\d+/emulated/"), "/storage/emulated/"),
                target.replaceFirst(Regex("^/mnt/user/\\d+/"), "/storage/"),
                target.replaceFirst(Regex("^/mnt/pass_through/\\d+/"), "/storage/"),
            ).distinct()
        return candidates.firstOrNull { it.startsWith("/") && File(it).canRead() }
    }

    /** Copies the file into app storage, for devices where direct access fails. */
    suspend fun copyIntoApp(
        id: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val dao = database().models()
        val model = dao.get(id) ?: return@withContext
        modelsDir.mkdirs()
        val target = File(modelsDir, "$id.gguf")
        val partial = File(modelsDir, "$id.gguf.part")
        try {
            resolver.openInputStream(Uri.parse(model.uri))!!.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        if (model.sizeBytes > 0) onProgress(copied.toFloat() / model.sizeBytes)
                    }
                    output.fd.sync()
                }
            }
            check(partial.renameTo(target)) { "Couldn't finish the copy" }
            dao.upsert(model.copy(localPath = target.absolutePath, failedBackends = ""))
        } finally {
            partial.delete()
        }
    }

    suspend fun remove(id: String) =
        withContext(Dispatchers.IO) {
            val dao = database().models()
            val model = dao.get(id) ?: return@withContext
            model.localPath?.let { File(it).delete() }
            releasePermission(Uri.parse(model.uri))
            dao.delete(id)
        }

    fun settingsOf(model: ModelEntity): ModelSettings = ModelSettings.fromJson(model.settings)

    suspend fun updateSettings(
        id: String,
        settings: ModelSettings,
    ) {
        val dao = database().models()
        val model = dao.get(id) ?: return
        dao.upsert(model.copy(settings = settings.toJson()))
    }

    private val infoCache = java.util.concurrent.ConcurrentHashMap<String, GgufInfo>()

    /** Re-reads the header for details not kept in the database (attention shape, recommended sampling). */
    suspend fun info(model: ModelEntity): GgufInfo? =
        infoCache[model.id] ?: withContext(Dispatchers.IO) {
            runCatching {
                val stream =
                    model.localPath?.takeIf { File(it).canRead() }?.let { File(it).inputStream() }
                        ?: resolver.openInputStream(Uri.parse(model.uri))
                stream?.use { GgufReader.read(it) }
            }.getOrNull()?.also { infoCache[model.id] = it }
        }

    suspend fun recordLoaded(
        id: String,
        backend: String,
    ) {
        val dao = database().models()
        val model = dao.get(id) ?: return
        dao.upsert(model.copy(lastBackend = backend, failedBackends = model.failedSet().minus(backend).joinToString(",")))
    }

    /** Remembers a backend that crashed the process, so Auto skips it next time. */
    suspend fun recordCrash(
        id: String,
        backend: String,
    ) {
        val dao = database().models()
        val model = dao.get(id) ?: return
        dao.upsert(model.copy(failedBackends = (model.failedSet() + backend).joinToString(",")))
    }

    private fun describe(uri: Uri): Pair<String, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0) ?: uri.lastPathSegment ?: "model.gguf"
                val size = if (c.isNull(1)) -1L else c.getLong(1)
                return name to size
            }
        }
        return (uri.lastPathSegment ?: "model.gguf") to -1L
    }

    private fun releasePermission(uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

fun ModelEntity.failedSet(): Set<String> = failedBackends.split(',').filter { it.isNotBlank() }.toSet()

val ModelEntity.displayName: String
    get() = name ?: fileName.removeSuffix(".gguf")
