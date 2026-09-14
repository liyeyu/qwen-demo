package com.qianwen.demo.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class NewsItem(
    val title: String,
    // 服务端字段名示例： "image" 或 "imageUrl" 都可以，通过 ignoreUnknownKeys 和下面的尝试解析控制兼容性
    val image: String? = null,
    val imageUrl: String? = null
) {
    fun resolvedImageUrl(): String? = imageUrl ?: image
}

@Serializable
data class NewsListEnvelope(
    val type: String,
    val items: List<NewsItem>
)

/**
 * 尝试把 message.content 解析为新闻列表。
 * - 首先尝试解析为 List<NewsItem>
 * - 然后尝试解析为 NewsListEnvelope（例如 { "type":"news_list", "items":[...] }）
 * - 若都失败，返回 null
 */
fun ChatMessage.parseAsNewsList(json: Json = Json { ignoreUnknownKeys = true }): List<NewsItem>? {
    val payload = this.content.trim()
    if (payload.isEmpty()) return null

    return runCatching {
        // 1) 直接解析为数组
        json.decodeFromString<List<NewsItem>>(payload)
    }.getOrElse {
        // 2) 解析为 envelope（兼容服务端封装）
        runCatching {
            val envelope = json.decodeFromString<NewsListEnvelope>(payload)
            // 你可以要求 envelope.type == "news_list" 或者直接信任 items
            envelope.items
        }.getOrNull()
    }
}
