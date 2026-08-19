package io.github.dzkchen.dhen.event

sealed interface ClientTickEvent : DeepProfiledEvent {
	object Start : ClientTickEvent
	object End : ClientTickEvent
}

object ServerTickEvent : DeepProfiledEvent
