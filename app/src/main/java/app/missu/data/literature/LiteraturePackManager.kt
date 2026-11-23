package app.missu.data.literature

import android.content.Context
import androidx.core.content.edit
import app.missu.BuildConfig
import app.missu.data.api.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class LiteratureFullPack(
    val fileName: String,
    val databaseVersion: Int,
    val workCount: Int,
    val bytes: Long,
    val sha256: String,
    val databaseBytes: Long,
    val databaseSha256: String,
)

data class LiteraturePackStatus(
    val fullActive: Boolean,
    val workCount: Int,
    val fullWorkCount: Int,
    val fullBytes: Long,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val error: Throwable? = null,
)

private data class LiteraturePackCatalog(
    val databaseVersion: Int,
    val coreWorkCount: Int,
    val bundledFull: LiteratureFullPack,
)

private class LiteraturePackStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("missu:literature-pack", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "literature-packs").apply { mkdirs() }
    private val catalog = parseCatalog(context.assets.open("literature-pack.json").bufferedReader().use { it.readText() })

    fun openInitialDatabase(): LiteratureDatabase {
        context.deleteDatabase("literature.db")
        val active = activePack()
        return if (active == null) openCore() else openFull(active)
    }

    fun openCore(): LiteratureDatabase = LiteratureDatabase.createCore(context)

    fun openFull(pack: LiteratureFullPack): LiteratureDatabase {
        val name = roomName(pack)
        return if (context.getDatabasePath(name).exists()) {
            LiteratureDatabase.openExisting(context, name)
        } else {
            LiteratureDatabase.createFull(context, databaseFile(pack), name)
        }
    }

    fun activePack(): LiteratureFullPack? {
        val sha = prefs.getString(KEY_SHA, null) ?: return null
        val pack = LiteratureFullPack(
            fileName = prefs.getString(KEY_FILE_NAME, null) ?: return null,
            databaseVersion = prefs.getInt(KEY_DATABASE_VERSION, -1),
            workCount = prefs.getInt(KEY_WORK_COUNT, -1),
            bytes = prefs.getLong(KEY_BYTES, -1),
            sha256 = sha,
            databaseBytes = prefs.getLong(KEY_DATABASE_BYTES, -1),
            databaseSha256 = prefs.getString(KEY_DATABASE_SHA, null) ?: return null,
        )
        if (pack.databaseVersion != catalog.databaseVersion ||
            (!context.getDatabasePath(roomName(pack)).exists() && databaseFile(pack).length() != pack.databaseBytes)
        ) {
            clearActive()
            return null
        }
        return pack
    }

    fun status(error: Throwable? = null): LiteraturePackStatus {
        val active = activePack()
        return LiteraturePackStatus(
            fullActive = active != null,
            workCount = active?.workCount ?: catalog.coreWorkCount,
            fullWorkCount = catalog.bundledFull.workCount,
            fullBytes = catalog.bundledFull.bytes,
            error = error,
        )
    }

    fun bundledFull(): LiteratureFullPack = catalog.bundledFull

    fun archivePart(pack: LiteratureFullPack): File = File(directory, "${pack.sha256.take(16)}.gz.part")

    fun databasePart(pack: LiteratureFullPack): File = File(directory, "${pack.sha256.take(16)}.db.part")

    fun databaseFile(pack: LiteratureFullPack): File = File(directory, "full-${pack.sha256.take(16)}.db")

    fun publishDatabase(pack: LiteratureFullPack) {
        val part = databasePart(pack)
        val target = databaseFile(pack)
        if (target.exists() && !target.delete()) throw IOException("无法替换旧文库")
        if (!part.renameTo(target)) throw IOException("无法启用已下载文库")
    }

    fun markActive(pack: LiteratureFullPack) {
        prefs.edit {
            putString(KEY_FILE_NAME, pack.fileName)
            putInt(KEY_DATABASE_VERSION, pack.databaseVersion)
            putInt(KEY_WORK_COUNT, pack.workCount)
            putLong(KEY_BYTES, pack.bytes)
            putString(KEY_SHA, pack.sha256)
            putLong(KEY_DATABASE_BYTES, pack.databaseBytes)
            putString(KEY_DATABASE_SHA, pack.databaseSha256)
        }
    }

    fun clearActive() {
        prefs.edit { clear() }
    }

    fun discardParts(pack: LiteratureFullPack) {
        archivePart(pack).delete()
        databasePart(pack).delete()
    }

    fun cleanup(previous: LiteratureFullPack?, keep: LiteratureFullPack?) {
        if (previous != null && previous.sha256 != keep?.sha256) {
            context.deleteDatabase(roomName(previous))
            databaseFile(previous).delete()
        }
        keep?.let { databaseFile(it).delete() }
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) file.delete()
        }
    }

    fun parsePack(value: String): LiteratureFullPack {
        val json = Json.parseToJsonElement(value).jsonObject
        return LiteratureFullPack(
            fileName = json.getValue("fileName").jsonPrimitive.content,
            databaseVersion = json.getValue("databaseVersion").jsonPrimitive.int,
            workCount = json.getValue("workCount").jsonPrimitive.int,
            bytes = json.getValue("bytes").jsonPrimitive.long,
            sha256 = json.getValue("sha256").jsonPrimitive.content,
            databaseBytes = json.getValue("databaseBytes").jsonPrimitive.long,
            databaseSha256 = json.getValue("databaseSha256").jsonPrimitive.content,
        )
    }

    private fun parseCatalog(value: String): LiteraturePackCatalog {
        val json = Json.parseToJsonElement(value).jsonObject
        return LiteraturePackCatalog(
            databaseVersion = json.getValue("databaseVersion").jsonPrimitive.int,
            coreWorkCount = json.getValue("core").jsonObject.getValue("workCount").jsonPrimitive.int,
            bundledFull = parsePack(json.getValue("full").jsonObject.toString()),
        )
    }

    private fun roomName(pack: LiteratureFullPack): String = "literature-full-${pack.sha256.take(16)}.db"

    private companion object {
        const val KEY_FILE_NAME = "fileName"
        const val KEY_DATABASE_VERSION = "databaseVersion"
        const val KEY_WORK_COUNT = "workCount"
        const val KEY_BYTES = "bytes"
        const val KEY_SHA = "sha256"
        const val KEY_DATABASE_BYTES = "databaseBytes"
        const val KEY_DATABASE_SHA = "databaseSha256"
    }
}

class LiteraturePackManager(
    private val storeContext: Context,
    private val httpClient: OkHttpClient,
    private val repository: LiteratureRepository,
) {
    private val store = LiteraturePackStore(storeContext.applicationContext)
    private val operation = Mutex()
    private val _state = MutableStateFlow(store.status())
    val state: StateFlow<LiteraturePackStatus> = _state.asStateFlow()

    suspend fun warmUp() = operation.withLock {
        try {
            repository.warmUp()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            if (store.activePack() == null) throw error
            useCoreLocked()
        }
    }

    suspend fun useCore() = operation.withLock {
        try {
            useCoreLocked()
        } catch (error: kotlinx.coroutines.CancellationException) {
            _state.value = store.status()
            throw error
        } catch (error: Exception) {
            _state.value = store.status(error)
        }
    }

    suspend fun downloadFull() = operation.withLock {
        var pack = store.bundledFull()
        _state.value = store.status().copy(downloading = true, downloadedBytes = 0)
        try {
            pack = fetchManifest()
            validate(pack)
            _state.value = store.status().copy(
                downloading = true,
                fullWorkCount = pack.workCount,
                fullBytes = pack.bytes,
            )
            if (store.databaseFile(pack).length() != pack.databaseBytes) {
                store.discardParts(pack)
                downloadArchive(pack)
                verifyFile(store.archivePart(pack), pack.bytes, pack.sha256)
                inflateArchive(pack)
                verifyFile(store.databasePart(pack), pack.databaseBytes, pack.databaseSha256)
                store.publishDatabase(pack)
            }
            activateLocked(pack)
        } catch (error: kotlinx.coroutines.CancellationException) {
            store.discardParts(pack)
            _state.value = store.status()
            throw error
        } catch (error: Exception) {
            store.discardParts(pack)
            _state.value = store.status(error)
        }
    }

    private suspend fun useCoreLocked() {
        val previous = store.activePack() ?: run {
            _state.value = store.status()
            return
        }
        val database = store.openCore()
        try {
            database.dao().warmUp()
            repository.replaceDatabase(database)
            store.clearActive()
            store.cleanup(previous, keep = null)
            _state.value = store.status()
        } catch (error: Exception) {
            database.close()
            throw error
        }
    }

    private suspend fun activateLocked(pack: LiteratureFullPack) {
        val previous = store.activePack()
        if (previous?.sha256 == pack.sha256) {
            store.cleanup(previous, pack)
            _state.value = store.status()
            return
        }
        val database = store.openFull(pack)
        try {
            database.dao().warmUp()
            repository.replaceDatabase(database)
            store.markActive(pack)
            store.cleanup(previous, pack)
            _state.value = store.status()
        } catch (error: Exception) {
            database.close()
            storeContext.deleteDatabase("literature-full-${pack.sha256.take(16)}.db")
            throw error
        }
    }

    private suspend fun fetchManifest(): LiteratureFullPack {
        val request = Request.Builder()
            .url("${BuildConfig.LITERATURE_PACK_BASE_URL}/literature-manifest.json")
            .build()
        val body = execute(request) { response ->
            response.body.string()
        }
        return store.parsePack(body)
    }

    private fun validate(pack: LiteratureFullPack) {
        if (pack.databaseVersion != store.bundledFull().databaseVersion) {
            throw IOException("文库版本与应用不兼容")
        }
        if (pack.fileName != "literature-full.db.gz" || pack.bytes <= 0 || pack.databaseBytes <= 0 ||
            !SHA256.matches(pack.sha256) || !SHA256.matches(pack.databaseSha256)
        ) {
            throw IOException("文库清单无效")
        }
    }

    private suspend fun downloadArchive(pack: LiteratureFullPack) {
        val request = Request.Builder()
            .url("${BuildConfig.LITERATURE_PACK_BASE_URL}/${pack.fileName}")
            .build()
        execute(request) { response ->
            val body = response.body
            var downloaded = 0L
            var published = 0L
            body.byteStream().use { input ->
                store.archivePart(pack).outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded - published >= PROGRESS_STEP) {
                            published = downloaded
                            _state.value = store.status().copy(
                                downloading = true,
                                fullWorkCount = pack.workCount,
                                fullBytes = pack.bytes,
                                downloadedBytes = downloaded,
                            )
                        }
                    }
                }
            }
            if (downloaded != pack.bytes) throw IOException("文库下载不完整")
        }
    }

    private fun inflateArchive(pack: LiteratureFullPack) {
        GZIPInputStream(store.archivePart(pack).inputStream().buffered()).use { input ->
            store.databasePart(pack).outputStream().buffered().use { output ->
                input.copyTo(output, bufferSize = 128 * 1024)
            }
        }
    }

    private suspend fun <T> execute(request: Request, block: (okhttp3.Response) -> T): T =
        withContext(Dispatchers.IO) {
            val call = httpClient.newCall(request)
            currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            call.execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                block(response)
            }
        }

    private fun verifyFile(file: File, expectedBytes: Long, expectedSha256: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                bytes += count
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (bytes != expectedBytes || !actual.equals(expectedSha256, ignoreCase = true)) {
            throw IOException("文库校验失败")
        }
    }

    companion object {
        private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
        private const val PROGRESS_STEP = 4 * 1024 * 1024

        fun openInitialDatabase(context: Context): LiteratureDatabase =
            LiteraturePackStore(context.applicationContext).openInitialDatabase()
    }
}
