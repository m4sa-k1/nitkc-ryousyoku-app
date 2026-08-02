package com.m4sak1.ryousyoku.model

import kotlinx.serialization.Serializable

@Serializable
data class Menu(
    val filename: String,
    val period: String,
    val downloaded_at: String = ""
) {
    val baseName: String
        get() = filename.replace(".pdf", "")

    val imageUrl: String
        get() = "https://ryousyoku.m4sak1.me/images/$baseName.png"

    val pdfUrl: String
        get() = "https://ryousyoku.m4sak1.me/files/$filename"
}
