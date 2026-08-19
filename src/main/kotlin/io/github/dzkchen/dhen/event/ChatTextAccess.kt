package io.github.dzkchen.dhen.event

interface ChatTextAccess {
	fun chatText(): String

	fun chatText(text: String)
}
