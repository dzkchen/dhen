package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.maxwell.MaxwellState
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOTTOM_RIGHT
import io.github.dzkchen.dhen.gui.slotCornerText
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

object MagicalPower : Module(
	name = "Magical Power",
	category = Category.INVENTORY,
	description = "Writes each accessory's magical power in the corner of its slot in the accessory bag and auction house."
) {
	private val coloredSetting = BooleanSetting(
		"Colored",
		description = "Paints the number in the accessory's rarity colour instead of grey."
	)

	private val menuTitle = Pattern.compile(MENU_TITLE).matcher("")
	private val seen = arrayOfNulls<ItemStack>(MENU_SLOTS)
	private val powers = IntArray(MENU_SLOTS)
	private val inks = IntArray(MENU_SLOTS)

	private var host: AbstractContainerScreen<*>? = null
	private var showing = false
	private var colored = false

	init {
		registerSetting(coloredSetting)

		on<SlotRenderEvent.Post> { drawn(it) }
		on<GuiCloseEvent> { forget() }
	}

	override fun onDisabled() = forget()

	private fun forget() {
		host = null
		showing = false
		seen.fill(null)
	}

	private fun drawn(event: SlotRenderEvent.Post) {
		if (!SkyBlockLocation.inSkyBlock || SkyBlockLocation.island == Island.THE_RIFT) return
		if (event.screen !== host || colored != coloredSetting.on) {
			host = event.screen
			colored = coloredSetting.on
			showing = menuTitle.reset(withoutCodes(event.screen.title.string)).matches()
			seen.fill(null)
		}
		if (!showing) return
		val slot = event.slot
		val index = slot.index
		if (index < 0 || index >= MENU_SLOTS) return
		val stack = slot.item
		if (stack.isEmpty) return
		if (seen[index] !== stack) measure(index, stack)
		if (powers[index] <= 0) return
		slotCornerText(event.graphics, powers[index].toString(), slot.x, slot.y, SLOT_BOTTOM_RIGHT, inks[index])
	}

	private fun measure(index: Int, stack: ItemStack) {
		seen[index] = stack
		val rarity = accessoryRarity(stack)
		powers[index] = if (rarity == null) 0 else powerOf(stack, rarity)
		inks[index] = if (rarity != null && colored) legacyColor(rarity.baseColor) else DhenPalette.TEXT_SECONDARY
	}

	internal fun powerOf(stack: ItemStack, rarity: ItemRarity): Int {
		val id = SkyBlockItems.of(stack).id
		return when {
			id == HEGEMONY -> rarity.magicalPower * 2
			id == RIFT_PRISM -> PRISM_POWER
			id.startsWith(ABICASE) -> rarity.magicalPower + MaxwellState.abiphoneContacts / CONTACTS_PER_POWER
			else -> rarity.magicalPower
		}
	}

	private fun accessoryRarity(stack: ItemStack): ItemRarity? {
		val category = ItemFacts.category(stack)
		if (category != ACCESSORY && category != HATCESSORY) return null
		val rarity = SkyBlockItems.rarity(stack)
		return if (rarity == ItemRarity.NONE) null else rarity
	}

	private const val ACCESSORY = "ACCESSORY"
	private const val HATCESSORY = "HATCESSORY"
	private const val HEGEMONY = "HEGEMONY_ARTIFACT"
	private const val RIFT_PRISM = "RIFT_PRISM"
	private const val ABICASE = "ABICASE_"
	private const val PRISM_POWER = 11
	private const val CONTACTS_PER_POWER = 2
	private const val MENU_SLOTS = 128
	private const val MENU_TITLE = "Accessory Bag(?: \\(\\d+/\\d+\\))?|Auctions Browser|Manage Auctions|Auctions: \".*\"?"
}
