package com.m4sak1.ryousyoku.model

import kotlinx.serialization.Serializable
import com.m4sak1.ryousyoku.BuildConfig

@Serializable
data class Menu(
    val filename: String,
    val period: String,
    val downloaded_at: String = ""
) {
    val baseName: String
        get() = filename.replace(".pdf", "")

    val imageUrl: String
        get() = "${BuildConfig.MENU_BASE_URL}images/$baseName.png"

    val pdfUrl: String
        get() = "${BuildConfig.MENU_BASE_URL}files/$filename"
}
