package io.github.dzkchen.dhen.api

sealed interface DhenEvent

data object ClientTickEvent : DhenEvent

data object ServerJoinEvent : DhenEvent

data object ServerLeaveEvent : DhenEvent

data class ChatReceiveEvent(val text: String, val overlay: Boolean = false) : DhenEvent
