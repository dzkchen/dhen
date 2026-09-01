package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.data.quiver.QuiverArrow

class QuiverUpdateEvent internal constructor(
	val arrow: QuiverArrow?,
	val amount: Int
) : Event
