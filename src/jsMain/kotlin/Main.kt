import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.jonastm.model.*
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    val messages = mutableStateListOf<UserMessageAction>()
    val handler = ServerActionHandlerImpl(messages)
    val chat = ChatStream(handler)

    var inputText = ""

    renderComposable(rootElementId = "root") {
        Div({ style { padding(25.px) } }) {

            H1 { Text("Chat Room") }

            Hr()

            Div(attrs = {
                style {
                    height(300.px)
                    overflow("auto")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.ColumnReverse)
                }
            }) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                    }
                }) {
                    messages.forEach {
                        val message = it
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Row)
                                alignItems(AlignItems.Baseline)
                                gap(10.px)
                            }
                        }) {
                            P {
                                Text(convertTime(message.time))
                            }
                            P(attrs = {
                                style {
                                    fontWeight(500)
                                }
                            }){
                                Text("[${it.username}]: ${it.text}")
                            }
                            Button(attrs = {
                                onClick {
                                    CoroutineScope(Dispatchers.Default).launch {
                                        chat.send(DeleteMessageAction(message.id)) {
                                            messages.add(UserMessageAction(0L.toString(),"${it.message}", "ERROR"))
                                        }
                                    }
                                }
                            }) {
                                Text("X")
                            }
                        }
                    }
                }
            }

            Hr()

            TextArea(attrs = {
                style {
                    width(300.px)
                    height(50.px)
                }
                onInput {
                    inputText = it.value.trim()
                }
            })

            Button(attrs = {
                onClick {
                    CoroutineScope(Dispatchers.Default).launch {
                        if (inputText.isNotBlank()) {
                            chat.send(NewMessageAction(inputText)) {
                                messages.add(UserMessageAction(1L.toString(),"${it.message}", "ERROR"))
                            }
                        }
                    }
                }
            }) {
                Text("Send Message")
            }
        }
    }
}

class ServerActionHandlerImpl(
    private val messages: SnapshotStateList<UserMessageAction>
) : ServerActionHandler {
    override suspend fun onError(e: Exception) {
        messages.add(
            UserMessageAction(0L.toString(),"${e.message}", "ERROR")
        )
    }

    override suspend fun onRemoveMessage(action: RemoveAction) {
        var itemToRemove: UserMessageAction? = null
        for (message in messages) {
            if (message.id == action.id) {
                itemToRemove = message
                break
            }
        }
        messages.remove(itemToRemove)
    }

    override suspend fun onUserMessage(action: UserMessageAction) {
        messages.add(action)
    }
}

class ChatStream(
    private val serverActionHandler: ServerActionHandler
) {

    private val client = HttpClient(Js) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
        }
    }

    private var wsSession: DefaultClientWebSocketSession? = null

    init {
        CoroutineScope(Dispatchers.Default).launch {
            client.webSocket(method = HttpMethod.Get, path = "/ws") {
                wsSession = this
                receive<ServerAction> {
                    onAction(it, serverActionHandler)
                }
            }
        }
    }

    suspend fun send(msg: ClientAction, onError: (Exception) -> Unit) {
        wsSession?.sendMessage(msg, onError) ?: apply {
            onError(Exception("No connected to chat"))
        }
    }
}

fun convertTime(instant: Instant): String {
    val time = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = when {
        (time.hour > 9) -> "${time.hour}"
        else -> " ${time.hour}"
    }
    val minutes = when {
        (time.minute > 9) -> "${time.minute}"
        else -> " ${time.minute}"
    }
    return "$hour:$minutes"
}

suspend inline fun <reified T> DefaultClientWebSocketSession.receive(onMessage: (T) -> Unit) {
    try {
        while (true) {
            val msg = receiveDeserialized<T>()
            onMessage(msg)
        }
    } catch (e: Exception) {
        println("Error while receiving: " + e.message)
    }
}

suspend inline fun <reified T> DefaultClientWebSocketSession.sendMessage(msg: T, onError: (Exception) -> Unit) {
    try {
        sendSerialized(msg)
    } catch (e: Exception) {
        println("Error while sending: " + e.message)
        onError(e)
        return
    }
}
