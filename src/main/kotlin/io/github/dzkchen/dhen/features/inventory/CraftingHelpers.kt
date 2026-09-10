package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.cookie.CookieState
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.*
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.input.platformModifierHeld
import io.github.dzkchen.dhen.input.platformModifierName
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.item.ItemStack

object CraftingHelpers : Module(
	name = "Crafting Helpers",
	category = Category.INVENTORY,
	description = "Protects quick crafts, warns about costly supercrafts, and offers amount presets and sack withdrawals."
) {
	internal val quick = BooleanSetting("Quick Craft Confirmation")
	internal val superGfs = BooleanSetting("Super Craft GfS", true)
	internal val queued = BooleanSetting("Queued GfS", true)
	internal val bazaar = BooleanSetting("Bazaar GfS")
	internal val defaultAmount = NumberSetting("Default Amount GfS", 1.0, 1.0, 64.0)
	internal val sackKey = MenuKeybinds.menuKey("GfS Keybind", -1, "Fills the inventory with the hovered sack item.")
	internal val presetsEnabled = BooleanSetting("Presets")
	internal val amounts = StringSetting("Preset Amounts", "4, 8, 16, 32, 64, 128, 256, 512", description = "Positive amounts separated by commas, in display order.")
	private val warning = BooleanSetting("Waste Warning", true)
	private val threshold = NumberSetting("Savings Threshold", 10.0, 0.1, 50.0, 0.1).withDependency { warning.on }
	private val bulk = NumberSetting("Bulk Threshold", 5.0, 0.1, 50.0, 0.1).withDependency { warning.on }
	private val withoutCookie = NumberSetting("No Cookie Threshold", 20.0, 0.1, 100.0, 0.1).withDependency { warning.on }
	private val withoutCookieBulk = NumberSetting("No Cookie Bulk Threshold", 10.0, 0.1, 100.0, 0.1).withDependency { warning.on }
	internal val presetHud = hud(SupercraftPresets())
	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private val count = Regex(".*Crafting (?<count>[0-9,]+) item.*")
	private val resource = Regex(" *✔ (?<owned>[0-9,]+)/(?<used>[0-9,]+) (?:\\([0-9,]+x\\) )?(?<resource>.+)")
	private var title = ""
	private var sackHeld = false
	private val blocked = BooleanArray(MAX_SCREEN_SLOTS)
	private val shades = BooleanArray(MAX_SCREEN_SLOTS)
	private val requests = SackRequests()

	init {
		for (setting in listOf(quick, superGfs, queued, bazaar, defaultAmount, sackKey, presetsEnabled, amounts, warning, threshold, bulk, withoutCookie, withoutCookieBulk)) registerSetting(setting)
		on<ContainerReadyEvent> { observe(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { observe(it.title.string, it.stacks) }
		on<ContainerClosedEvent> {
			title = ""
			blocked.fill(false)
			shades.fill(false)
		}
		on<ContainerClickEvent>(AFTER_PRODUCERS) { event ->
			if (!SkyBlockLocation.inSkyBlock) return@on
			val slot = event.hoveredSlot ?: return@on
			if (MenuKeybinds.bound(sackKey, event.click.button(), true)) pullHovered(slot.item)
			if (quick.on && slot.index in blocked.indices && blocked[slot.index] && !platformModifierHeld()) event.cancelled = true
			if (!event.cancelled && event.click.button() == 0 && slot.index == ACTION_SLOT) event.cancelled = waste(event.screen.menu.slots.map { it.item })
		}
		on<KeyInputEvent> { if (it.key == sackKey.code && it.action == InputAction.RELEASE) sackHeld = false }
		on<MouseInputEvent> { if (it.button == sackKey.code && it.action == InputAction.RELEASE) sackHeld = false }
		on<ContainerKeyEvent>(AFTER_PRODUCERS) { event ->
			if (SkyBlockLocation.inSkyBlock && MenuKeybinds.bound(sackKey, event.input.key(), false)) event.hoveredSlot?.item?.let(::pullHovered)
		}
		on<SlotRenderEvent.Post> { event ->
			if (quick.on && SkyBlockLocation.inSkyBlock && event.slot.index in shades.indices && shades[event.slot.index] && !platformModifierHeld()) {
				SharpGui.fill(event.graphics, event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, DhenPalette.withAlpha(DhenPalette.SLOT_SHADE, 180))
			}
		}
		on<TooltipEvent> { event ->
			if (!quick.on || !SkyBlockLocation.inSkyBlock || event.hoveredSlot.index !in blocked.indices || !blocked[event.hoveredSlot.index]) return@on
			val lines = event.edit()
			for (index in lines.indices) if (withoutCodes(lines[index].string) == "Click to craft!") lines[index] = DhenType.component("§c${platformModifierName()} + Click to craft!")
		}
		on<MessageSendEvent>(AFTER_PRODUCERS) { event ->
			if (!event.isCommand) return@on
			if (presetCommand(event.message)) event.cancelled = true else if (SkyBlockLocation.inSkyBlock) requests.command(event)
		}
		on<ChatReceiveEvent> { event ->
			requests.chat(event.stripped)
			if (!superGfs.on || !SkyBlockLocation.inSkyBlock) return@on
			val match = CraftingRules.supercrafted(event.styled) ?: return@on
			val id = sackId(match.groups["item"]!!.value) ?: return@on
			val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
			inTicks(1) { offer(id, amount) }
		}
		on<ClientTickEvent.End> {
			priceHold.ensure()
			repoHold.ensure()
			requests.tick()
			presetHud.refresh()
		}
		on<ScreenRenderEvent.Post> { presetHud.pointer(it.mouseX, it.mouseY) }
		on<MouseInputEvent>(AFTER_PRODUCERS) { event ->
			if (event.action == InputAction.PRESS && event.button == 0 && presetHud.clicked()) event.cancelled = true
		}
		on<WorldChangeEvent> { requests.clear() }
	}

	override fun onDisabled() {
		priceHold.release()
		repoHold.release()
		requests.clear()
		sackHeld = false
		blocked.fill(false)
		shades.fill(false)
	}

	private fun observe(raw: String, stacks: List<ItemStack>) {
		title = withoutCodes(raw)
		blocked.fill(false)
		shades.fill(false)
		for (index in 0 until minOf(stacks.size, blocked.size)) {
			val name = withoutCodes(stacks[index].hoverName.string)
			blocked[index] = CraftingRules.quickSlot(title, index) && !stacks[index].isEmpty && name !in QUICK_CRAFTABLE
			shades[index] = blocked[index] && name != "Quick Crafting Slot"
		}
	}

	private fun waste(stacks: List<ItemStack>): Boolean {
		if (!title.endsWith(" Recipe") || !warning.on || SidebarValues.noTradeProfile() || platformModifierHeld() || stacks.size <= ACTION_SLOT) return false
		val lore = SkyBlockItems.lore(stacks[ACTION_SLOT]).map { withoutCodes(it.string) }
		val amount = lore.firstNotNullOfOrNull { count.matchEntire(it)?.groups?.get("count")?.value?.replace(",", "")?.toLongOrNull() } ?: return false
		val result = stacks[RESULT_SLOT]
		val multiplier = result.count
		if (multiplier.toLong() !in 1..amount) return false
		val resultProduct = Prices.product(SkyBlockItems.of(result).id) ?: return false
		val revenue = CraftingRules.cost(resultProduct.buySummary, amount) ?: return false
		val materials = HashMap<String, Long>()
		for (slot in MATERIAL_SLOTS) {
			val stack = stacks[slot]
			if (stack.isEmpty) continue
			val id = SkyBlockItems.of(stack).id
			if (id.isEmpty()) return false
			materials[id] = (materials[id] ?: 0L) + stack.count
		}
		if (materials.isEmpty()) return false
		var cost = 0.0
		for ((id, units) in materials) cost += CraftingRules.cost(Prices.product(id)?.sellSummary ?: return false, units * (amount / multiplier)) ?: return false
		val maximum = lore.mapNotNull { line ->
			val match = resource.matchEntire(line) ?: return@mapNotNull null
			CraftingRules.maximum(match.groups["owned"]!!.value.replace(",", "").toLongOrNull() ?: return@mapNotNull null,
				match.groups["used"]!!.value.replace(",", "").toLongOrNull() ?: return@mapNotNull null, amount, multiplier)
		}.minOrNull()
		val cookie = CookieState.expiry > System.currentTimeMillis()
		val profit = revenue - cost
		if (!CraftingRules.blocks(profit, amount, maximum, if (cookie) threshold.amount else withoutCookie.amount, if (cookie) bulk.amount else withoutCookieBulk.amount)) return false
		DhenAlert.show("Super Crafting Blocked (Potential Loss)", "Hold ${platformModifierName()} to bypass. Potential loss: ${shortNumber((-profit).toLong())}")
		Dhen.announce("Super Craft Blocked: selling the materials and buying the result saves ${shortNumber((-profit).toLong())}. Hold ${platformModifierName()} to bypass.")
		return true
	}

	internal fun withdraw(id: String, amount: Int): Boolean {
		if (!enabled || !SkyBlockLocation.inSkyBlock || id !in ItemRepo.constants.sackItemIds || amount <= 0) return false
		requests.enqueue(id, amount)
		return true
	}

	private fun pullHovered(stack: ItemStack) {
		if (sackHeld) return
		sackHeld = true
		val id = SkyBlockItems.of(stack).id
		withdraw(id, FILL_AMOUNT)
	}

	internal fun sackId(name: String): String? {
		val id = name.uppercase().replace(':', '-')
		if (id in ItemRepo.constants.sackItemIds) return id
		val clean = withoutCodes(name).replace('_', ' ')
		for (item in ItemRepo.searchNames()) if (item.id in ItemRepo.constants.sackItemIds && item.label.equals(clean, true)) return item.id
		return null
	}

	private fun offer(id: String, amount: Int) {
		val label = ItemRepo.item(id)?.displayName ?: id
		val message = DhenType.component("§lCLICK HERE§r§e to grab §ax$amount §9$label§e from sacks!").copy()
		message.withStyle { it.withClickEvent(ClickEvent.RunCommand("/gfs ${id.replace('-', ':')} $amount")).withHoverEvent(HoverEvent.ShowText(DhenType.component("Click to get from sacks!"))) }
		Minecraft.getInstance().player?.sendSystemMessage(message)
	}

	private fun presetCommand(command: String): Boolean {
		val words = command.split(' ')
		if (words[0] != "shsupercraftpreset") return false
		if (words.size == 1) {
			Dhen.announce("Current presets: ${amounts.value.ifBlank { "none" }}. /shsupercraftpreset <number> adds or removes one.")
			return true
		}
		val amount = words.getOrNull(1)?.toIntOrNull()
		if (amount == null || amount <= 0 || words.size != 2) {
			Dhen.announce("Use /shsupercraftpreset <positive number>.")
			return true
		}
		val values = CraftingRules.presets(amounts.value).toMutableList()
		if (!values.remove(amount)) values += amount
		amounts.value = values.sorted().joinToString(", ")
		persist()
		Dhen.announce("Current presets: ${amounts.value.ifBlank { "none" }}")
		return true
	}

	private const val ACTION_SLOT = 32
	private const val RESULT_SLOT = 25
	private const val FILL_AMOUNT = 9999
	private val MATERIAL_SLOTS = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
}
