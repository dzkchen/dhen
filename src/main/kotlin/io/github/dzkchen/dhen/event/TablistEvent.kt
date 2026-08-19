package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.data.TabWidget

class TablistUpdateEvent internal constructor(val lines: List<String>, val previous: List<String>) : Event

class TabWidgetUpdateEvent internal constructor(
	val widget: TabWidget,
	val lines: List<String>,
	val previous: List<String>
) : Event
