package com.jonastm.model

import kotlinx.serialization.Serializable

@Serializable
data class NewMessage(
    val text: String,
)

@Serializable
data class UserMessage(
    val text: String,
    val username: String,
)
