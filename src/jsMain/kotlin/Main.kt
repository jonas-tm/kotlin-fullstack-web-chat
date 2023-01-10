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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    var wsSession: DefaultClientWebSocketSession? = null
    val messages = mutableStateListOf<UserMessage>()

    var inputText = ""

    val client = HttpClient(Js) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
        }
    }

    GlobalScope.launch {
        client.webSocket(method = HttpMethod.Get, host = "127.0.0.1", port =8079, path = "/ws") {
            wsSession = this
            receive { msg: UserMessage ->
                messages.add(msg)
            }
        }
    }

    renderComposable(rootElementId = "root") {
        Div({ style { padding(25.px) } }) {

            H1 { Text("Chat Room") }

            Hr()

            Div(attrs = {
                style {
                    height(500.px)
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

            Input(type = InputType.Url) {
                onInput {
                    inputText = it.value.trim()
                }
            }

            Button(attrs = {
                onClick {
                    CoroutineScope(Dispatchers.Default).launch {
                        if (inputText.isNotBlank()) {
                            wsSession?.sendMessage(NewMessage(inputText)) {
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

suspend fun DefaultClientWebSocketSession.receive(onMessage: suspend (UserMessage) -> Unit) {
    try {
        while (true) {
            val msg = receiveDeserialized<UserMessage>()
            onMessage(msg)
        }
    } catch (e: Exception) {
        println("Error while receiving: " + e.message)
    }
}

suspend fun DefaultClientWebSocketSession.sendMessage(msg: NewMessage, onError: suspend (Exception) -> Unit) {
    try {
        sendSerialized(msg)
    } catch (e: Exception) {
        println("Error while sending: " + e.message)
        onError(e)
        return
    }

}
