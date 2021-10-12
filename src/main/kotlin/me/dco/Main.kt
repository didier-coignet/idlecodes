package me.dco

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.cli.default

fun main(args: Array<String>) {

    println("Starting idlecodes")

    val settings = loadSettingsFromFile()
    val ic = IdleChampions()
    val httpClient = HttpClient(CIO) {
        expectSuccess = false
        engine {
            // this: CIOEngineConfig
            maxConnectionsCount = 1000
            endpoint {
                // this: EndpointConfig
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 50000
                connectTimeout = 50000
                connectAttempts = 5
            }

        }
    }

    //Command line arguments
    val parser = ArgParser("idlecodes")
    //option for normal use
    val discordLimit by parser.option(ArgType.Int, "limitdiscord", "ld", "limit date get message from discord")
        .default(50)
    val redditLimit by parser.option(ArgType.Int, "limitreddit", "lr", "max messages to get from reddit").default(50)

    // option to add discord information
    class AddDiscordToken : Subcommand("discord", "add / replace the discord token") {
        val userToken by argument(ArgType.String, "token", "your discord token to read codes from discord")
        override fun execute() {}
    }

    //option to add an idlechampions login
    class AddIdleLogin : Subcommand("idle", "add idlechampions login ") {
        val deleteLogin by option(ArgType.Boolean, "delete", "d", "remove the login from the list")
            .default(false)
        val userId by argument(ArgType.String, "user_id", "your idle champions user Id")
        val userHash by argument(ArgType.String, "hash", "your idle champions hash")
        override fun execute() {
        }
    }

    val addDiscordToken = AddDiscordToken()
    val addIdleLogin = AddIdleLogin()
    parser.subcommands(addDiscordToken, addIdleLogin)
    val parserResult = parser.parse(args)
    //validation
    if (discordLimit < 1 || discordLimit > 100) {
        throw Exception("discord limit out of bound (1..100) : $discordLimit")
    }
    if (redditLimit < 1 || redditLimit > 100) {
        throw Exception("reddit limit out of bound (1..100) : $redditLimit")
    }

    //main logic
    if (parserResult.commandName == addIdleLogin.name) {
        val login = IdleLogin(addIdleLogin.userId, addIdleLogin.userHash, LinkedHashSet(1))
        if (addIdleLogin.deleteLogin) {
            if (settings.removeLogin(login)) {
                println("the login $login was not present")
            }
        } else {
            val instanceId = ic.getIdleInstanceId(httpClient, login)
            if (instanceId != null && instanceId.isNotBlank()) {
                if (settings.addLogin(login)) {
                    settings.writeSettings()
                    println("login $login added to the list")
                } else {
                    println("login $login already present in settings")
                }
            } else {
                println("login $login is not validated by the server")
            }
        }
    } else if (parserResult.commandName == addDiscordToken.name) {
        if (settings.userToken == addDiscordToken.userToken) {
            println("discord token ${addDiscordToken.userToken} already present")
            return
        }

        settings.replaceToken(addDiscordToken.userToken)
        if (getDiscordCodes(httpClient, settings, 1).isNotEmpty()) {
            println("discord token ${addDiscordToken.userToken} added")
            settings.writeSettings()
        } else {
            println("token ${addDiscordToken.userToken} refused by server : check if you belong to the discord channel")
        }
    } else {
        if (settings.listIdleLogins.isNotEmpty()) {
            val codes = getRedditCodes(httpClient, redditLimit)
            if (settings.userToken.isNotBlank()) {
                val discordCodes = getDiscordCodes(httpClient, settings, discordLimit)
                codes.addAll(discordCodes)
                println("${discordCodes.size} codes for DISCORD")
            }
            settings.listIdleLogins.forEach {
                val login = it
                val codesUser = LinkedHashSet<String>(codes.size)
                codesUser.addAll(codes)
                codesUser.removeAll(login.codesDone)
                if (codesUser.isEmpty()) {
                    println("no code to send for $login")
                } else {
                    println("${codesUser.size} codes to send for user : $login")
                    val instanceId = ic.getIdleInstanceId(httpClient, login)
                    if (instanceId != null && instanceId.isNotBlank()) {
                        codesUser.forEach { it2 ->
                            println("$instanceId - $it2")
                            ic.sendIdleCode(httpClient, login, instanceId, it2)
                            settings.amendCodesDone(login, it2)
                        }
                        settings.writeSettings()
                    } else {
                        println("ABORT : impossible to get instance_id for $login")
                    }
                }
            }
        }
    }

    httpClient.close()
    println("***END***")
}

