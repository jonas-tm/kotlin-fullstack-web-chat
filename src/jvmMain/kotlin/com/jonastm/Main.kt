package com.jonastm

import com.jonastm.model.NewMessage
import com.jonastm.model.UserMessage
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class Connection(val session: WebSocketServerSession) {
    companion object {
        val lastId = AtomicInteger(0)
    }
    val name = "user${lastId.getAndIncrement()}"

    suspend fun send(msg: UserMessage) {
        session.sendSerialized(msg)
    }
}

fun main() {

    val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())

    embeddedServer(Netty, port = 8079, host = "127.0.0.1") {
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowHeaders { true }
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
        }

        routing {
            webSocket("/ws") {
                println("Adding user!")
                val thisConnection = Connection(this)
                connections.add(thisConnection)

                try {
                    sendSerialized(UserMessage("Welcome to our server", "Server Bot"))
                    while (true) {
                        val msg = receiveDeserialized<NewMessage>()
                        println("Received: ${msg.text}")
                        val userMsg = UserMessage(msg.text, thisConnection.name)
                        connections.forEach {
                            it.send(userMsg)
                        }
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    println("Removing $thisConnection!")
                    connections.remove(thisConnection)
                }
            }

            get("/") {
                call.application.log.info("get called")
                call.respondText { "Hello World" }
            }
            static("/static") {
                resources()
            }
        }
    }.start(wait = true)
}
