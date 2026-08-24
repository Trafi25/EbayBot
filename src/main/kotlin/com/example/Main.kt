package com.example

import com.example.ebay.EbayMonitor
import com.example.ebay.EbayService
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
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
            filter { request ->
                // Sanitize logs: don't log Telegram API calls which contain the bot token
                !request.url.host.contains("telegram.org")
            }
        }
    }

    val ebayService = EbayService(client, ebayClientId, ebayClientSecret)
    
    // Initializing monitor variable for use in dispatch
    var ebayMonitorRef: EbayMonitor? = null

    val telegramBot = bot {
        token = botToken
        dispatch {
            command("start") {
                bot.sendMessage(ChatId.fromId(message.chat.id), "Welcome! Send me /search <item> to monitor eBay.pl for new listings in Kraków.")
            }
            command("stop") {
                ebayMonitorRef?.stopSearch(message.chat.id)
                bot.sendMessage(ChatId.fromId(message.chat.id), "Monitoring stopped.")
            }
            command("search") {
                val query = args.joinToString(" ").trim()
                if (query.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "Please provide a search term, e.g., /search SKÅDIS")
                    return@command
                }
                
                ebayMonitorRef?.addOrUpdateSearch(message.chat.id, query)
                bot.sendMessage(ChatId.fromId(message.chat.id), "Now monitoring for '$query' in Kraków. I'll alert you about new listings every 10 minutes.")
            }
        }
    }

    ebayMonitorRef = EbayMonitor(ebayService, telegramBot)

    launch {
        ebayMonitorRef.startMonitoring()
    }

    telegramBot.startPolling()
    
    println("Bot is running and listening for Telegram messages...")
}
