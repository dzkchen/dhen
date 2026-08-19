package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.data.Island

class IslandChangeEvent internal constructor(val island: Island, val previous: Island) : Event

class AreaChangeEvent internal constructor(val area: String?, val previous: String?) : Event
