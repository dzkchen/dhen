package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.MessageSendEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.util.Calculated
import io.github.dzkchen.dhen.util.Calculator
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import java.util.Locale

internal class SackRequests {
	private class Request(val id: String, val amount: Int)
	private val queue = ArrayDeque<Request>()
	private var last: Request? = null
	private var lastAt = 0L
	private var profile: String? = null
	private var sending = false
	private val moved = Regex("Moved ([\\d,]+) (.+) from your Sacks to your inventory\\.")
	private val missing = Regex("You have no (.+) in your Sacks!")

	fun enqueue(id: String, amount: Int) {
		if (amount > 0) queue.addLast(Request(id, amount))
	}

	fun command(event: MessageSendEvent) {
		if (sending) return
		val words = event.message.trim().split(Regex("\\s+"))
		if (words.firstOrNull() == "dhenbazaarfromsacks" && words.size == 3) {
			val amount = words[1].toIntOrNull() ?: return
			val id = CraftingHelpers.sackId(words[2]) ?: return
			if (amount <= 0 || SidebarValues.noTradeProfile()) return
			event.cancelled = true
			val client = Minecraft.getInstance()
			client.keyboardHandler.clipboard = amount.toString()
			client.connection?.sendCommand("bz ${withoutCodes(ItemRepo.item(id)?.displayName ?: id)}")
			return
		}
		if (words.firstOrNull()?.lowercase(Locale.ROOT) !in COMMANDS) return
		if (words.size < 2) return
		val calculated = Calculator.evaluate(words.last()) as? Calculated.Value
		val explicit = calculated?.amount?.let { runCatching { it.intValueExact() }.getOrNull() }
		if (calculated != null && explicit == null) {
			event.cancelled = true
			Dhen.announce("The sack amount must be a whole number within the supported range.")
			return
		}
		val amount = explicit ?: CraftingHelpers.defaultAmount.amount.toInt()
		if (amount <= 0) {
			event.cancelled = true
			Dhen.announce("The sack amount must be positive.")
			return
		}
		val query = words.subList(1, if (explicit == null) words.size else words.size - 1).joinToString(" ")
		val id = CraftingHelpers.sackId(query) ?: return
		if (CraftingHelpers.queued.on) {
			event.cancelled = true
			enqueue(id, amount)
		} else {
			event.message = "gfs ${id.replace('-', ':')} $amount"
			remember(Request(id, amount))
		}
	}

	fun tick() {
		if (!SkyBlockLocation.inSkyBlock || profile != null && ProfileHooks.profile != profile) {
			clear()
			return
		}
		profile = ProfileHooks.profile
		val now = System.currentTimeMillis()
		if (last != null && now - lastAt > REPLY_TIMEOUT) last = null
		if (last != null || queue.isEmpty() || now - lastAt < SEND_INTERVAL) return
		val connection = Minecraft.getInstance().connection ?: return
		val request = queue.removeFirst()
		remember(request)
		sending = true
		try {
			connection.sendCommand("gfs ${request.id.replace('-', ':')} ${request.amount}")
		} finally {
			sending = false
		}
	}

	private fun remember(request: Request) {
		lastAt = System.currentTimeMillis()
		last = if (CraftingHelpers.bazaar.on && !SidebarValues.noTradeProfile()) request else null
	}

	fun chat(line: String) {
		val request = last ?: return
		val partial = moved.matchEntire(line)
		val absent = missing.matchEntire(line)
		val name = partial?.groupValues?.get(2) ?: absent?.groupValues?.get(1) ?: return
		if (CraftingHelpers.sackId(name) != request.id) return
		last = null
		if (!CraftingHelpers.bazaar.on || SidebarValues.noTradeProfile()) return
		val received = partial?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
		val remaining = request.amount - received
		if (remaining <= 0) return
		val label = ItemRepo.item(request.id)?.displayName ?: name
		val prompt = DhenType.component("CLICK to find the remaining x$remaining $label in the Bazaar.").copy()
		prompt.withStyle { it.withClickEvent(ClickEvent.RunCommand("/dhenbazaarfromsacks $remaining ${request.id}")).withHoverEvent(HoverEvent.ShowText(DhenType.component("Buy $remaining items."))) }
		Minecraft.getInstance().player?.sendSystemMessage(prompt)
	}

	fun clear() {
		queue.clear()
		last = null
		lastAt = 0
		profile = null
		sending = false
	}

	private companion object {
		val COMMANDS = setOf("gfs", "getfromsacks")
		const val SEND_INTERVAL = 1650L
		const val REPLY_TIMEOUT = 10_000L
	}
}
