package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.Hooks
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.network.chat.Component

internal class FirstRunExperience(
	private val persist: () -> Unit,
	private val announce: (Component) -> Unit
) : Hooks {
	override val feed: String = "First-run welcome"

	private var subscription: Handle? = null
	private var installed = false

	var shown: Boolean = false
		private set

	fun install(bus: EventBus, alreadyShown: Boolean) {
		uninstall()
		installed = true
		shown = alreadyShown
		if (!shown) subscription = bus.subscribe<IslandChangeEvent>(handler = ::islandChanged)
	}

	override fun uninstall() {
		subscription?.unsubscribe()
		subscription = null
		installed = false
	}

	override fun active(): Boolean = installed

	private fun islandChanged(event: IslandChangeEvent) {
		if (event.previous != Island.NONE || event.island == Island.NONE) return
		shown = true
		subscription?.unsubscribe()
		subscription = null
		persist()
		announce(welcome())
	}

	private fun welcome(): Component = DhenType.clickableCommandOverWorld(
		prefix = "Welcome to Dhen! Click ",
		command = "/dhen",
		suffix = " to list its commands, or use /dhen addon list to browse addons."
	)
}
