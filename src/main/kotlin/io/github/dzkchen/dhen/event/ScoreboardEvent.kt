package io.github.dzkchen.dhen.event

class ScoreboardUpdateEvent internal constructor(val lines: List<String>, val previous: List<String>) : Event

class ScoreboardAreaChangeEvent internal constructor(val area: String?, val previous: String?) : Event
