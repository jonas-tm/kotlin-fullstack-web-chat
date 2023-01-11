package com.jonastm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ClientAction()

@Serializable
@SerialName("new_msg")
class NewMessageAction(
    val text: String,
) : ClientAction()

@Serializable
@SerialName("remove")
class DeleteMessageAction(
    val id: String,
) : ClientAction()

interface ClientActionHandler {
    suspend fun onNewMessage(action: NewMessageAction)
    suspend fun onDeleteMessage(action: DeleteMessageAction)
    suspend fun onError(e: Exception)
}

suspend fun onAction(action: ClientAction, handler: ClientActionHandler) {
    when (action) {
        is NewMessageAction -> handler.onNewMessage(action)
        is DeleteMessageAction -> handler.onDeleteMessage(action)
        else -> handler.onError(UnsupportedActionException())
    }
}
