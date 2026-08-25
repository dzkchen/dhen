package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe
import java.util.regex.Matcher

internal object TabWidgetHooks : GuardedHooks<TabWidgetHooks.Channels> {
	override val feed = "Tab list widgets"

	override val failsafe = Failsafe("Dhen {} failed, its tab list widgets are off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock }) {
		uninstall()
		channels = Channels(bus, inSkyBlock)
		subscriptions = arrayOf(bus.subscribe<TablistUpdateEvent>(BEFORE_FEATURES) { regrouped() })
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		TabWidgetState.reset()
	}

	override fun bound() = channels

	private fun regrouped() = guarded("tab list widget grouping") { it.group(TablistState.lines, TablistState.stripped) }

	internal class Channels(bus: EventBus, private val inSkyBlock: () -> Boolean) {
		private val updates = bus.type<TabWidgetUpdateEvent>()
		private val widgets = TabWidget.entries
		private val matchers: Array<Matcher> = Array(widgets.size) { widgets[it].header.toPattern().matcher("") }
		private val found = arrayOfNulls<TabWidget>(widgets.size)
		private val starts = IntArray(widgets.size + 1)
		private val seen = BooleanArray(widgets.size)
		private var kept = IntArray(0)

		fun group(lines: List<String>, stripped: List<String>) {
			var grouped = 0
			var keptLines = 0
			var running = false
			if (inSkyBlock()) {
				if (kept.size < stripped.size) kept = IntArray(stripped.size)
				for (index in stripped.indices) {
					val line = stripped[index]
					if (line.isEmpty()) continue
					val widget = headerOf(line)
					if (widget != null) {
						if (seen[widget.ordinal]) {
							running = running && found[grouped - 1] === widget
							continue
						}
						seen[widget.ordinal] = true
						found[grouped] = widget
						starts[grouped] = keptLines
						grouped++
						running = true
					} else if (!running) continue
					kept[keptLines++] = index
				}
			}
			starts[grouped] = keptLines
			for (index in 0 until grouped) {
				publish(found[index]!!, lines, stripped, starts[index], starts[index + 1])
			}
			for (index in widgets.indices) {
				if (seen[index]) seen[index] = false else clear(widgets[index])
			}
		}

		private fun headerOf(line: String): TabWidget? {
			for (index in matchers.indices) if (matchers[index].reset(line).matches()) return widgets[index]
			return null
		}

		private fun publish(widget: TabWidget, lines: List<String>, stripped: List<String>, from: Int, to: Int) {
			val previous = TabWidgetState.lines(widget)
			if (previous.size == to - from && sameLines(previous, lines, from, to)) return
			TabWidgetState.read(widget, sliceOf(lines, from, to), sliceOf(stripped, from, to))
			dispatch(widget, previous)
		}

		private fun clear(widget: TabWidget) {
			if (!TabWidgetState.active(widget)) return
			val previous = TabWidgetState.lines(widget)
			TabWidgetState.read(widget, emptyList(), emptyList())
			dispatch(widget, previous)
		}

		private fun dispatch(widget: TabWidget, previous: List<String>) {
			if (updates.hasSubscribers) updates.dispatch(TabWidgetUpdateEvent(widget, TabWidgetState.lines(widget), previous))
		}

		private fun sameLines(previous: List<String>, lines: List<String>, from: Int, to: Int): Boolean {
			for (index in from until to) if (previous[index - from] != lines[kept[index]]) return false
			return true
		}

		private fun sliceOf(source: List<String>, from: Int, to: Int): List<String> {
			val slice = ArrayList<String>(to - from)
			for (index in from until to) slice += source[kept[index]]
			return slice
		}
	}
}
