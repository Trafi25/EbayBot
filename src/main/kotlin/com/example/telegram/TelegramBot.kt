package com.example.telegram

import com.example.ebay.EbayService
import com.example.models.TelegramResponse
import com.example.models.TelegramUpdate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class TelegramBot(
    private val client: HttpClient,
    private val botToken: String,
    private val ebayService: EbayService
) {
    private val logger = LoggerFactory.getLogger(TelegramBot::class.java)
    private var lastUpdateId = 0L

    suspend fun start() {
        logger.info("Starting Telegram Bot...")
        while (true) {
            try {
                val response: TelegramResponse<List<TelegramUpdate>> = client.get("https://api.telegram.org/bot$botToken/getUpdates") {
                    parameter("offset", lastUpdateId + 1)
                    parameter("timeout", 30)
                }.body()

                if (response.ok) {
                    for (update in response.result) {
                        lastUpdateId = update.update_id
                        handleUpdate(update)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in Telegram loop", e)
                delay(5000)
            }
        }
    }

    private suspend fun handleUpdate(update: TelegramUpdate) {
        val message = update.message ?: return
        val text = message.text ?: return
        val chatId = message.chat.id

        if (text.startsWith("/search")) {
            val query = text.removePrefix("/search").trim()
            if (query.isEmpty()) {
                sendMessage(chatId, "Please provide a search term. Example: /search laptop")
                return
            }
            
            sendMessage(chatId, "Searching for '$query' on eBay.pl...")
            try {
                val results = ebayService.searchItems(query)
                val summaries = results.itemSummaries
                if (summaries.isNullOrEmpty()) {
                    sendMessage(chatId, "No products found for '$query'.")
                } else {
                    val responseText = summaries.joinToString("\n\n") { item ->
                        "${item.title}\nPrice: ${item.price?.value} ${item.price?.currency}\nLink: ${item.itemWebUrl}"
                    }
                    sendMessage(chatId, responseText)
                }
            } catch (e: Exception) {
                logger.error("eBay search failed", e)
                sendMessage(chatId, "Failed to search eBay. Please try again later.")
            }
        } else if (text == "/start") {
            sendMessage(chatId, "Welcome! Use /search <product> to find goods on eBay Poland.")
        }
    }

    private suspend fun sendMessage(chatId: Long, text: String) {
        client.post("https://api.telegram.org/bot$botToken/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("chat_id" to chatId, "text" to text))
        }
    }
}
