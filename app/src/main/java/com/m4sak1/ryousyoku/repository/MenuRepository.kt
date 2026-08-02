package com.m4sak1.ryousyoku.repository

import com.m4sak1.ryousyoku.model.Menu
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class MenuRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMenus(): List<Menu> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://ryousyoku.m4sak1.me/data/menus.json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseData = response.body?.string() ?: throw IOException("Empty body")
            json.decodeFromString(responseData)
        }
    }
    suspend fun fetchRawMenus(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://ryousyoku.m4sak1.me/data/menus.json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }

    suspend fun fetchRawSkipPeriods(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://ryousyoku.m4sak1.me/data/skip_periods.json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }
}
