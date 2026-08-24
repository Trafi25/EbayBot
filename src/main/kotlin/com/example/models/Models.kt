package com.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T
)

@Serializable
data class TelegramUpdate(
    val update_id: Long,
    val message: TelegramMessage? = null
)

@Serializable
data class TelegramMessage(
    val message_id: Long,
    val chat: TelegramChat,
    val text: String? = null
)

@Serializable
data class TelegramChat(
    val id: Long
)

@Serializable
data class EbaySearchResponse(
    val itemSummaries: List<EbayItemSummary>? = null
)

@Serializable
data class EbayItemSummary(
    val itemId: String,
    val title: String,
    val itemWebUrl: String,
    val price: EbayPrice? = null,
    val thumbnailImages: List<EbayImage>? = null,
    val itemLocation: EbayLocation? = null
)

@Serializable
data class EbayLocation(
    val city: String? = null,
    val country: String? = null
)

@Serializable
data class EbayPrice(
    val value: String,
    val currency: String
)

@Serializable
data class EbayImage(
    val imageUrl: String
)

@Serializable
data class EbayTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val token_type: String
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    val text: String
)
