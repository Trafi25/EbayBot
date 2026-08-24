package com.example.ebay

import com.example.models.BotState
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class EbayMonitor(
    private val ebayService: EbayService,
    private val telegramBot: Bot,
    initialState: BotState = BotState(),
    private val onStateChanged: suspend (BotState) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(EbayMonitor::class.java)
    
    // Channel to trigger immediate monitoring check
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    
    // chatId -> query
    private val userSearches = ConcurrentHashMap<Long, String>(initialState.userSearches)
    
    // ... rest of fields ...
    
    // chatId -> Set of seen Item IDs
    private val seenItemsPerUser = ConcurrentHashMap<Long, MutableSet<String>>().apply {
        initialState.seenItemsPerUser.forEach { (id, items) ->
            put(id, ConcurrentHashMap.newKeySet<String>().apply { addAll(items) })
        }
    }
    
    // chatId -> Is it the first search?
    private val firstSearch = ConcurrentHashMap<Long, Boolean>()

    private suspend fun saveState() {
        val state = BotState(
            userSearches = userSearches.toMap(),
            seenItemsPerUser = seenItemsPerUser.mapValues { it.value.toSet() }
        )
        onStateChanged(state)
    }

    suspend fun addOrUpdateSearch(chatId: Long, query: String) {
        userSearches[chatId] = query
        seenItemsPerUser[chatId] = ConcurrentHashMap.newKeySet()
        firstSearch[chatId] = true
        saveState()
        logger.info("Updated search for $chatId to '$query'.")
        triggerChannel.trySend(Unit)
    }

    suspend fun stopSearch(chatId: Long) {
        userSearches.remove(chatId)
        seenItemsPerUser.remove(chatId)
        firstSearch.remove(chatId)
        saveState()
        logger.info("Stopped search for $chatId")
        triggerChannel.trySend(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun startMonitoring() {
        logger.info("Starting eBay monitor loop...")
        while (true) {
            try {
                if (userSearches.isNotEmpty()) {
                    for ((chatId, query) in userSearches) {
                        // ... existing monitoring logic ...
                        logger.info("Periodic search: User $chatId, Query '$query'")
                        val response = ebayService.searchItems(query)
                        val items = response.itemSummaries ?: emptyList()

                        val seenSet = seenItemsPerUser.getOrPut(chatId) { ConcurrentHashMap.newKeySet() }
                        
                        val krakowItems = items.filter { item ->
                            val city = item.itemLocation?.city?.lowercase() ?: ""
                            city.contains("kraków") || city.contains("krakow")
                        }

                        if (firstSearch[chatId] == true) {
                            logger.info("Initial population for $chatId: sending first results.")
                            
                            val limit = 5
                            val initialItems = krakowItems.take(limit)
                            
                            telegramBot.sendMessage(
                                chatId = ChatId.fromId(chatId),
                                text = "✅ Моніторинг активований для '$query' у Кракові!\nОсь перші результати:"
                            )

                            for (item in initialItems) {
                                val message = "📍 Знайдено: ${item.title}\nЦіна: ${item.price?.value} ${item.price?.currency}\nПосилання: ${item.itemWebUrl}"
                                telegramBot.sendMessage(chatId = ChatId.fromId(chatId), text = message)
                            }

                            krakowItems.forEach { seenSet.add(it.itemId) }
                            firstSearch[chatId] = false
                            saveState()
                            continue
                        }

                        val newItems = krakowItems.filter { it.itemId !in seenSet }
                        
                        if (newItems.isNotEmpty()) {
                            logger.info("Found ${newItems.size} NEW items in Kraków for $chatId")
                            for (item in newItems) {
                                val location = item.itemLocation?.city ?: "Unknown"
                                val message = "🔔 NEW Listing in $location!\n\n${item.title}\nPrice: ${item.price?.value} ${item.price?.currency}\nLink: ${item.itemWebUrl}"
                                telegramBot.sendMessage(
                                    chatId = ChatId.fromId(chatId),
                                    text = message
                               )
                                seenSet.add(item.itemId)
                            }
                            saveState()
                        } else {
                            logger.info("No new items for $chatId")
                        }
                    }
                } else {
                    logger.info("No active searches. Waiting for trigger...")
                }
            } catch (e: Exception) {
                logger.error("Critical error in eBay monitor loop", e)
            }

            // Wait for 8 hours (3 times a day) OR until a new search is added
            logger.info("Waiting 8 hours (or until triggered)...")
            select<Unit> {
                triggerChannel.onReceive { 
                    logger.info("Monitor loop triggered early by search update.")
                }
                onTimeout(8 * 60 * 60 * 1000L) {
                    logger.info("Monitor loop timeout reached (8h interval).")
                }
            }
        }
    }
}
