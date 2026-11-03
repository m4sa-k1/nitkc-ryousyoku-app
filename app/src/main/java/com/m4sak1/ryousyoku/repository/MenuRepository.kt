package com.m4sak1.ryousyoku.repository

import com.m4sak1.ryousyoku.model.Menu
import com.m4sak1.ryousyoku.model.StructuredMenu
import com.m4sak1.ryousyoku.model.StructuredIndex
import com.m4sak1.ryousyoku.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class StructuredNotReady : Exception("この週の文字データはまだ準備されていません。")
data class LoadedStructuredMenu(val menu: StructuredMenu, val fromCache: Boolean)

class MenuRepository(private val cacheDir: File? = null) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMenus(): List<Menu> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.MENU_BASE_URL}data/menus.json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseData = response.body?.string() ?: throw IOException("Empty body")
            json.decodeFromString(responseData)
        }
    }
    suspend fun fetchRawMenus(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.MENU_BASE_URL}data/menus.json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }

    suspend fun fetchRawSkipPeriods(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.MENU_BASE_URL}data/skip_periods.json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }

    suspend fun fetchStructured(menu: Menu): LoadedStructuredMenu = withContext(Dispatchers.IO) {
        val cache = cacheDir?.let { File(it, "structured-${menu.baseName}.json") }
        try {
            val index = json.decodeFromString<StructuredIndex>(getText("data/parsed/index.json"))
            if (index.schemaVersion != 1) throw StructuredNotReady()
            val entry = index.menus.singleOrNull { it.filename == menu.filename }
            if (entry?.status != "ready" || entry.path != "${menu.baseName}.json") {
                cache?.delete()
                throw StructuredNotReady()
            }
            // Remove old revisions before fetching; they must not be reused if a new body fails.
            cache?.takeIf { it.exists() }?.let {
                val old = runCatching { json.decodeFromString<StructuredMenu>(it.readText()) }.getOrNull()
                if (old?.source?.sha256 != entry.sourceSha256) it.delete()
            }
            val body = getText("data/parsed/${entry.path}")
            val parsed = json.decodeFromString<StructuredMenu>(body).validate(menu.filename, entry.sourceSha256)
            cache?.let {
                it.parentFile?.mkdirs()
                val temp = File(it.path + ".tmp")
                temp.writeText(body)
                if (!temp.renameTo(it)) { it.writeText(body); temp.delete() }
            }
            LoadedStructuredMenu(parsed, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (network: IOException) {
            val saved = cache?.takeIf { it.isFile }?.let {
                runCatching {
                    val parsed = json.decodeFromString<StructuredMenu>(it.readText())
                    parsed.validate(menu.filename, parsed.source.sha256)
                }.getOrNull()
            }
            if (saved != null) LoadedStructuredMenu(saved, true) else throw network
        }
    }

    suspend fun fetchRawStructuredIndex(): String = withContext(Dispatchers.IO) {
        getText("data/parsed/index.json")
    }

    suspend fun fetchRawStructuredMenu(menu: Menu): String = withContext(Dispatchers.IO) {
        require(menu.filename.matches(Regex("\\d{8}-\\d{8}\\.pdf")))
        getText("data/parsed/${menu.baseName}.json")
    }

    private fun getText(path: String): String {
        val request = Request.Builder().url(BuildConfig.MENU_BASE_URL + path).build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) throw StructuredNotReady()
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }
}
