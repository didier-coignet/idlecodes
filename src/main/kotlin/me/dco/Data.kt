package me.dco

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

const val settingsFilename = "settings.json"

@Serializable
data class Settings(var userToken: String, val listIdleLogins: LinkedHashSet<IdleLogin>) {
    val combinationsId = "358044869685673985"
    private var isModified = false

    fun removeLogin(login: IdleLogin): Boolean {
        if (listIdleLogins.remove(login)) {
            isModified = true
            return true
        }
        return false
    }

    fun addLogin(login: IdleLogin): Boolean {
        if (listIdleLogins.add(login)) {
            isModified = true
            return true
        }
        return false
    }

    fun replaceToken(userToken: String) {
        this.userToken = userToken
        isModified = true
    }

    fun amendCodesDone(login: IdleLogin, code: String) {
        login.codesDone.add(code)
        isModified = true
    }

    fun writeSettings(doNotCheckModified: Boolean = false) {
        removeOldCodes()
        if (isModified || doNotCheckModified) {
            isModified = false
            File(settingsFilename).writeText(Json.encodeToString(serializer(), this))
        }
    }

    private fun removeOldCodes() {
        listIdleLogins.forEach { l ->
            while (l.codesDone.size > 100) {
                l.codesDone.remove(l.codesDone.first())
                isModified = true
            }
        }
    }

}

fun loadSettingsFromFile(): Settings {
    val f = File(settingsFilename)
    if (f.isFile) {
        val j = Json.decodeFromString<Settings>(f.readText())
        return j
    } else {
        println("creating ${f.absolutePath}")
        val settings = Settings("", LinkedHashSet(1))
        settings.writeSettings(true)
        return loadSettingsFromFile()
    }
}


@Serializable
data class IdleLogin(
    val userId: String,
    val userHash: String,
    val codesDone: LinkedHashSet<String>
) {
    init {
        require(userId.isNotBlank()) { "userId is blank" }
        require(userHash.isNotBlank()) { "userHash is blank" }
    }

    override fun toString(): String {
        return "$userId - $userHash"
    }

    override fun equals(other: Any?): Boolean {
        if (other != null && other is IdleLogin && other.userId == userId && other.userHash == userHash) {
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return userId.hashCode().times(31).plus(userHash.hashCode())
    }
}
