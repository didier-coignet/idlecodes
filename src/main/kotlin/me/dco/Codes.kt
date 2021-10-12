package me.dco

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

fun getDiscordCodes(httpClient: HttpClient, settings: Settings, limit: Int = 50): List<String> {
    val resultDiscord = runBlocking {
        httpClient.request<String>() {
            url("https://discord.com/api/v8/channels/${settings.combinationsId}/messages?limit=$limit")
            method = HttpMethod.Get

            headers {
                append("Accept", "application/json")
                append("Authorization", settings.userToken)
            }
        }
    }

    val decoded = Json.decodeFromString<JsonArray>(resultDiscord)
    return decoded.jsonArray
        .mapNotNull { jsonElement -> jsonElement.jsonObject.getOrDefault("content", JsonNull).jsonPrimitive.content }
        .mapNotNull { content -> regexPattern.find(content)?.groupValues?.first() }
}

fun getRedditCodes(httpClient: HttpClient, limit: Int): LinkedHashSet<String> {
    val set = LinkedHashSet<String>(limit)
    val result = Json.decodeFromString<JsonObject>(runBlocking {
        httpClient.request<String> {
            url("https://www.reddit.com/r/idlechampions/search.json?q=flair_name%3A%22loot%22&restrict_sr=1&sort=new&raw_json=1&limit=$limit")
            method = HttpMethod.Get
            headers {
                append("Accept", "application/json")
            }
        }
    }).get("data")?.jsonObject?.get("children")?.jsonArray
        ?.mapNotNull<JsonElement, String> { jsonElement ->
            jsonElement.jsonObject.get("data")?.jsonObject?.get("selftext")
                ?.jsonPrimitive?.content
        }?.mapNotNull { content -> regexPattern.find(content)?.groupValues?.first() }
    if (result != null && result.isNotEmpty()) {
        set.addAll(result)
    }
    println("${set.size} codes from REDDIT")
    return set
}

val regexPattern =
    """(?:^|(?<![0-9A-Za-z!*&^%#@]))([0-9A-Za-z!*&^%#@]{4}-[0-9A-Za-z!*&^%#@]{4}-[0-9A-Za-z!*&^%#@]{4}-[0-9A-Za-z!*&^%#@]{4})(?:${'$'}|(?![0-9A-Za-z!*&^%#@]))|(?:^|(?<![0-9A-Za-z!*&^%#@]))([0-9A-Za-z!*&^%#@]{4}-[0-9A-Za-z!*&^%#@]{4}-[0-9A-Za-z!*&^%#@]{4})(?:${'$'}|(?![0-9A-Za-z!*&^%#@]))|(?:^|(?<![0-9A-Za-z!*&^%#@]))([0-9A-Za-z!*&^%#@]{16})(?:${'$'}|(?![0-9A-Za-z!*&^%#@]))|(?:^|(?<![0-9A-Za-z!*&^%#@]))([0-9A-Za-z!*&^%#@]{12})(?:${'$'}|(?![0-9A-Za-z!*&^%#@]))""".toRegex()