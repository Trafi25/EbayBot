package com.example

import com.example.ebay.EbayMonitor
import com.example.ebay.EbayService
import com.example.models.BotState
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun main() = runBlocking {
    val botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN not found")
    val ebayClientId = System.getenv("EBAY_CLIENT_ID") ?: error("EBAY_CLIENT_ID not found")
    val ebayClientSecret = System.getenv("EBAY_CLIENT_SECRET") ?: error("EBAY_CLIENT_SECRET not found")

    val stateFile = File("bot_state.json")
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    val initialState = if (stateFile.exists()) {
        try {
            json.decodeFromString<BotState>(stateFile.readText())
        } catch (e: Exception) {
            println("Error loading state: ${e.message}. Starting fresh.")
            BotState()
        }
    } else {
        BotState()
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
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
                launch {
                    ebayMonitorRef?.stopSearch(message.chat.id)
                    bot.sendMessage(ChatId.fromId(message.chat.id), "Monitoring stopped.")
                }
            }
            command("search") {
                val query = args.joinToString(" ").trim()
                if (query.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "Please provide a search term, e.g., /search SKÅDIS")
                    return@command
                }
                
                launch {
                    ebayMonitorRef?.addOrUpdateSearch(message.chat.id, query)
                    bot.sendMessage(ChatId.fromId(message.chat.id), "🔍 Search for '$query' received! I'm checking eBay.pl for listings in Kraków now...")
                }
            }
        }
    }

    ebayMonitorRef = EbayMonitor(
        ebayService = ebayService,
        telegramBot = telegramBot,
        initialState = initialState,
        onStateChanged = { newState ->
            withContext(Dispatchers.IO) {
                stateFile.writeText(json.encodeToString(newState))
            }
        }
    )

    launch {
        ebayMonitorRef.startMonitoring()
    }

    telegramBot.startPolling()
    
    println("Bot is running and listening for Telegram messages...")
}
