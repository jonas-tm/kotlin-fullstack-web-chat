import androidx.compose.runtime.mutableStateListOf
import com.jonastm.model.NewMessage
import com.jonastm.model.UserMessage
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    val messages = mutableStateListOf<UserMessage>()
    val chat = ChatStream {
        messages.add(it)
    }

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
                Ul {
                    messages.forEach {
                        P {
                            Text("[${it.username}]: ${it.text}")
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
                            chat.send(NewMessage(inputText)) {
                                messages.add(UserMessage("${it.message}", "ERROR"))
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

class ChatStream(
    val onMessage: suspend (UserMessage) -> Unit,
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
                receive<UserMessage> {
                    onMessage(it)
                }
            }
        }
    }

    suspend fun send(msg: NewMessage, onError: (Exception) -> Unit) {
        wsSession?.sendMessage(msg, onError) ?: apply {
            onError(Exception("No connected to chat"))
        }
    }
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
