package com.jonastm

import com.jonastm.model.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.html.*
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val connections: MutableSet<Connection> = Collections.synchronizedSet<Connection?>(LinkedHashSet())

suspend fun sendAll(action: ServerAction) {
    connections.forEach { conn ->
        conn.send(action)
    }
}

class ClientActionHandlerImpl(val connection: Connection): ClientActionHandler {
    override suspend fun onNewMessage(action: NewMessageAction) {
        sendAll(UserMessageAction(
            Random.nextLong().toString(),
            action.text,
            connection.name
        ))
    }

    override suspend fun onDeleteMessage(action: DeleteMessageAction) {
        sendAll(RemoveAction(action.id))
    }

    override suspend fun onError(e: Exception) {
        e.printStackTrace()
    }
}

fun main() {
    embeddedServer(Netty, port = 8078, host = "127.0.0.1") {
        configuration()
        routes()
    }.start(wait = true)
}

fun Application.configuration() {
    // Disable cors checks (needed when debugging)
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowHeaders { true }
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
    }
}

fun Application.routes() {
    routing {
        get("/") {
            call.respondHtml(HttpStatusCode.OK, HTML::index)
        }
        static("/static") {
            resources()
        }
        webSocket("/ws") {
            handleNewConnection()
        }
    }
}

fun HTML.index() {
    head {
        title("Chat Room")
    }
    body {
        div {
            id = "root"
        }
        script(src = "/static/kotlin-fullstack-web-chat.js") {}
    }
}

suspend fun WebSocketServerSession.handleNewConnection() {
    val thisConnection = Connection(this)
    connections.add(thisConnection)
    println("Added ${thisConnection.name}")

    val handler = ClientActionHandlerImpl(thisConnection)

    try {
        thisConnection.send(UserMessageAction(Random.nextLong().toString(),"Welcome to our server", "Server"))
        while (true) {
            val action = receiveDeserialized<ClientAction>()
            onAction(action, handler)
        }
    } catch (e: Exception) {
        println(e.localizedMessage)
    } finally {
        println("Removing $thisConnection!")
        connections.remove(thisConnection)
    }
}

class Connection(
    private val session: WebSocketServerSession
) {

    companion object {
        val lastId = AtomicInteger(0)
    }

    val name = "user${lastId.getAndIncrement()}"

    suspend fun send(msg: ServerAction) {
        session.sendSerialized(msg)
    }
}
