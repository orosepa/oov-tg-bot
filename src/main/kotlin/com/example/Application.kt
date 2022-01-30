package com.example

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import redis.clients.jedis.Jedis

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT").toInt()) {
        startOovBot()
    }.start(wait = true)
}

fun Application.startOovBot() {
    val jedis = Jedis(System.getenv("REDIS_URL"))

    val bot = bot {
        this.token = System.getenv("TOKEN")
        logLevel = LogLevel.Network.Body

        dispatch {

            command("start") {

                val keyboardMarkup = KeyboardReplyMarkup(keyboard = generateUsersButtons(), resizeKeyboard = true)

                val chatId = message.chat.id

                println(chatId)

                if (!jedis.exists(chatId.toString())) {
                    jedis.set(chatId.toString(), "20")
                }

                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "Привет, ${message.from?.firstName ?: "снюсоедик!"}!" +
                            "\n\nСейчас у тебя ${jedis.get(chatId.toString())} пакетов снюса. " +
                            "Количество можно изменить в меню",
                    replyMarkup = keyboardMarkup
                )

                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "ИНСТРУКЦИЯ" +
                            "\n/all -- сообщение всем" +
                            "\n/reset -- изменить количество паков напишите типа /reset 228" +
                            "\n\nкнопки внизу чтобы менять количество паков больше нет там теперь стата"
                )
            }

            command("all") {
                val argsStr = args.joinToString(" ")
                if (argsStr.isNotBlank()) {
                    jedis.keys("*").forEach {
                        bot.sendMessage(
                            chatId = ChatId.fromId(it.toLong()),
                            text = "Сообщение от ${message.from?.firstName}:" +
                                    "\n\n$argsStr"
                        )
                    }
                }
            }

            command("reset") {
                val snusQuantity = args.joinToString().toInt()

                if (snusQuantity in 0..30) {
                    jedis.set(message.chat.id.toString(), snusQuantity.toString())
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "Урааа! У тебя ${jedis.get(message.chat.id.toString())} пакетов"
                    )
                } else {
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "ээээээм"
                    )
                }
            }

            text("Статистика") {
                var result = "Статистика по всем снюсоедикам: \n"
                jedis.keys("*").forEach {
                    val user = bot.getChat(ChatId.fromId(it.toLong())).get()
                    println(user.firstName)
                    result += "\n\uD83E\uDD1F ${user.firstName}: ${jedis.get(it)} паков"
                }
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = result
                )
            }

            text("Вкинуть свежак") {
                val chatId = message.chat.id.toString()

                jedis.decr(chatId)

                if (jedis.get(chatId).toInt() >= 0) {
                    jedis.keys("*").forEach {
                        bot.sendMessage(
                            chatId = ChatId.fromId(it.toLong()),
                            text = "${message.from!!.firstName} оформляет свежак! " +
                                    "\n\nОстается ${jedis.get(chatId)} пакетов снюса"
                        )
                    }
                } else {
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId.toLong()),
                        text = "Sorry, у тебя не осталось снюса\n\uD83D\uDE14\uD83D\uDE14\uD83D\uDE14"
                    )
                }
            }

            text("Новая шайба!") {
                val chatId = message.chat.id.toString()
                jedis.set(chatId, "20")

                jedis.keys("*").forEach {
                    bot.sendMessage(
                        chatId = ChatId.fromId(it.toLong()),
                        text = "${message.from!!.firstName} открывает новую шайбу!"
                    )
                }
            }
        }
    }
    bot.startPolling()
}


private fun generateUsersButtons(): List<List<KeyboardButton>> {
    return listOf(
        listOf(
            KeyboardButton("Вкинуть свежак"),
        ),
        listOf(
            KeyboardButton("Новая шайба!"),
        ),
        listOf(
            KeyboardButton("Статистика"),
        ),
    )
}


