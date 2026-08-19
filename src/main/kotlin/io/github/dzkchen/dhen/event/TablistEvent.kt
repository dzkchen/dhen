package io.github.dzkchen.dhen.event

class TablistUpdateEvent internal constructor(val lines: List<String>, val previous: List<String>) : Event
