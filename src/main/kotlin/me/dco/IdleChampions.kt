package me.dco

import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class IdleChampions {
    var nextId = 0
    private fun getNextServerId(): String {
       // nextId = nextId.mod(3) +7
        return "ps7"
    }

    fun getIdleInstanceId(httpClient: HttpClient, login: IdleLogin, retry: Boolean = true): String? {
        val regex = """"instance_id":"(\d+)"""".toRegex()
        val resultPost = runBlocking {
            httpClient.submitForm<String>(
                url = "http://${getNextServerId()}.idlechampions.com/~idledragons/post.php",
                formParameters = Parameters.build {
                    append("call", "getuserdetails")
                    append("include_free_play_objectives", "true")
                    append("instance_key", "1")
                    append("user_id", login.userId)
                    append("hash", login.userHash)
                }
            )
        }

        var instanceId = regex.find(resultPost)?.groups?.get(1)?.value
        if (instanceId == null && retry) {
            // retry because sometimes the call does not load user_details
            print("retry : ")
            instanceId = getIdleInstanceId(httpClient, login, false)
        }
        println("we got $instanceId for user ")
        return instanceId
    }

    fun sendIdleCode(httpClient: HttpClient, login: IdleLogin, instanceId: String, code: String) {
        println("sending code $code for ${login.userId}")
        val resultPost = Json.decodeFromString<JsonObject>(runBlocking {
            httpClient.submitForm<String>("http://${getNextServerId()}.idlechampions.com/~idledragons/post.php",
                Parameters.build {
                    append("call", "redeemcoupon")
                    append("language_id", "1")
                    append("timestamp", "0")
                    append("request_id", "0")
                    append("network_id", "11")
                    append("mobile_client_version", "999")
                    append("user_id", login.userId)
                    append("hash", login.userHash)
                    append("instance_id", instanceId)
                    append("code", code)
                })
        })
        if (resultPost.containsKey("loot_details")) {
            println("chest $code added for ${login.userId}")
        } else if (resultPost.containsKey("failure_reason")) {
            println("error sending code $code : ${resultPost.get("failure_reason")}")
        }
    }
}