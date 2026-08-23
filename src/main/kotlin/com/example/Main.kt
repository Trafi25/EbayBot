package com.example

import com.example.ebay.EbayService
import com.example.telegram.TelegramBot
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main() = runBlocking {
    val botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN not found")
    val ebayClientId = System.getenv("EBAY_CLIENT_ID") ?: error("EBAY_CLIENT_ID not found")
    val ebayClientSecret = System.getenv("EBAY_CLIENT_SECRET") ?: error("EBAY_CLIENT_SECRET not found")

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    val ebayService = EbayService(client, ebayClientId, ebayClientSecret)
    val bot = TelegramBot(client, botToken, ebayService)

    bot.start()
}
