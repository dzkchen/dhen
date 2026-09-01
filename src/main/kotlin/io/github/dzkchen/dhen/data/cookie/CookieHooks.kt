package io.github.dzkchen.dhen.data.cookie

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.CookieUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.EpochClock
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.spanMillis
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

internal object CookieHooks : GuardedHooks<CookieHooks.Channels> {
	override val feed = "Booster cookie state"
	override val failsafe = Failsafe("Dhen {} failed, its booster cookie state is off until restart")

	private var channels: Channels? = null
	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, epoch: EpochClock = EpochClock.SYSTEM) {
		uninstall()
		channels = Channels(bus, epoch)
		subscriptions = arrayOf(
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it) },
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.stripped) }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		CookieState.reset()
	}

	override fun bound() = channels

	private fun opened(event: ContainerReadyEvent) = guarded("booster cookie menu") { it.opened(event) }

	private fun chatted(message: String) = guarded("booster cookie chat") { it.chatted(message) }

	internal class Channels(
		bus: EventBus,
		private val epoch: EpochClock,
		private val inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock }
	) {
		private val updates = bus.type<CookieUpdateEvent>()
		private val durationLine = matcher("\\s*Duration: (?<time>.*)")
		private val notActiveLine = matcher("\\s*Status: Not active!")
		private val noCookieLine = matcher("You do not currently have a|Booster Cookie active!")
		private val eaten = matcher("You consumed a Booster Cookie!.*")

		fun opened(event: ContainerReadyEvent) {
			if (!inSkyBlock()) return
			val title = withoutCodes(event.title.string)
			val changed = when (title) {
				MENU_TITLE -> skyBlockMenu(event.stacks)
				SHOP_TITLE, COOKIE_TITLE -> cookieMenu(event.stacks)
				else -> false
			}
			if (changed) updates.dispatch(CookieUpdateEvent())
		}

		fun chatted(message: String) {
			if (!inSkyBlock() || !eaten.reset(message).matches()) return
			val from = if (CookieState.expiry > CookieState.EXPIRED) CookieState.expiry else epoch.epochMillis()
			if (CookieState.expires(from + COOKIE_MILLIS)) updates.dispatch(CookieUpdateEvent())
		}

		private fun skyBlockMenu(stacks: List<ItemStack>): Boolean {
			val cookie = stacks.lastOrNull(::isCookie) ?: return CookieState.expires(CookieState.EXPIRED)
			for (line in SkyBlockItems.lore(cookie)) {
				val text = withoutCodes(line.string)
				if (durationLine.reset(text).matches()) {
					return CookieState.expires(epoch.epochMillis() + spanMillis(durationLine.group("time")))
				}
				if (notActiveLine.reset(text).matches() && CookieState.expiry != CookieState.EXPIRED) {
					return CookieState.expires(CookieState.EXPIRED)
				}
			}
			return false
		}

		private fun cookieMenu(stacks: List<ItemStack>): Boolean {
			val cookie = stacks.firstOrNull(::isCookie) ?: return false
			val lore = SkyBlockItems.lore(cookie)
			for (index in lore.indices) {
				val text = withoutCodes(lore[index].string)
				if (durationLine.reset(text).matches()) {
					return CookieState.expires(epoch.epochMillis() + spanMillis(durationLine.group("time")))
				}
				if (!noCookieLine.reset(text).matches()) continue
				val next = lore.getOrNull(index + 1)?.string?.let(::withoutCodes) ?: return false
				return noCookieLine.reset(next).matches() && CookieState.expires(CookieState.EXPIRED)
			}
			return false
		}

		private fun isCookie(stack: ItemStack): Boolean = withoutCodes(stack.hoverName.string).trim() == COOKIE_TITLE

		private fun matcher(pattern: String) = Pattern.compile(pattern).matcher("")
	}

	private const val MENU_TITLE = "SkyBlock Menu"
	private const val SHOP_TITLE = "Community Shop"
	private const val COOKIE_TITLE = "Booster Cookie"
	private const val COOKIE_MILLIS = 4 * 24 * 60 * 60 * 1_000L
}
