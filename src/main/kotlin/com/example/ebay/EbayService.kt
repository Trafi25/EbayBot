package com.example.ebay

import com.example.models.EbaySearchResponse
import com.example.models.EbayTokenResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import java.util.*

class EbayService(
    private val client: HttpClient,
    private val clientId: String,
    private val clientSecret: String
) {
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0
    
    // Use production URLs since your keys are for Production (PRD)
    private val baseUrl = "https://api.ebay.com"

    private suspend fun getAccessToken(): String {
        val currentTime = System.currentTimeMillis()
        if (accessToken != null && currentTime < tokenExpiry) {
            return accessToken!!
        }

        val auth = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val httpResponse = client.submitForm(
            url = "$baseUrl/identity/v1/oauth2/token",
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("scope", "https://api.ebay.com/oauth/api_scope")
            }
        ) {
            header(HttpHeaders.Authorization, "Basic $auth")
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.body<String>()
            throw Exception("eBay Auth Failed (${httpResponse.status}): $errorBody")
        }

        val response = httpResponse.body<EbayTokenResponse>()
        accessToken = response.access_token
        tokenExpiry = currentTime + (response.expires_in * 1000) - 60000
        return accessToken!!
    }

    suspend fun searchItems(query: String): EbaySearchResponse {
        val token = getAccessToken()
        val httpResponse = client.get("$baseUrl/buy/browse/v1/item_summary/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-EBAY-C-MARKETPLACE-ID", "EBAY_PL")
            parameter("q", query)
            parameter("limit", 20)
            parameter("fieldgroups", "EXTENDED")
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.body<String>()
            println("eBay Search Error: $errorBody")
            return EbaySearchResponse(emptyList())
        }

        return httpResponse.body()
    }
}
