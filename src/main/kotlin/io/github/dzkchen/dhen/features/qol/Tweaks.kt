package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object Tweaks : Module(
	name = "Tweaks",
	category = Category.QOL,
	description = "Small quality of life improvements for inventories and the hotbar."
) {
	internal val hideRecipeBookSetting = BooleanSetting(
		"Hide Recipe Book",
		description = "Hides the recipe book button in inventory screens."
	)
	internal var hideRecipeBook by hideRecipeBookSetting

	internal val closeRecipeBookSetting = BooleanSetting(
		"Close Recipe Book",
		description = "Also closes an open recipe book when the screen opens."
	).withDependency { hideRecipeBook }
	internal var closeRecipeBook by closeRecipeBookSetting

	internal val hideItemCooldownsSetting = BooleanSetting(
		"Hide Item Cooldowns",
		description = "Hides item cooldown overlays while in SkyBlock."
	)
	internal var hideItemCooldowns by hideItemCooldownsSetting

	internal val hideHotbarTooltipsSetting = BooleanSetting(
		"Hide Hotbar Tooltips",
		description = "Hides the held-item name above the hotbar."
	)
	internal var hideHotbarTooltips by hideHotbarTooltipsSetting

	internal val cakeNumbersSetting = BooleanSetting(
		"Cake Numbers",
		description = "Shows each New Year Cake's year in container slots."
	)
	internal var cakeNumbers by cakeNumbersSetting

	private val cakeYears = CakeYearCache()

	init {
		on<SlotRenderEvent.Post> { event ->
			if (!cakeNumbers || !SkyBlockLocation.inSkyBlock) return@on
			val stack = event.slot.item
			if (!stack.`is`(Items.CAKE)) return@on
			val year = cakeYears.year(stack) ?: return@on
			val graphics = event.graphics
			val font = Minecraft.getInstance().font
			val pose = graphics.pose()
			pose.pushMatrix()
			try {
				pose.translate((event.slot.x + SLOT_CENTER).toFloat(), (event.slot.y + SLOT_CENTER).toFloat())
				pose.scale(CAKE_SCALE, CAKE_SCALE)
				DhenType.text(
					graphics,
					font,
					year,
					-DhenType.width(font, year) / 2,
					-DhenType.lineHeight(font) / 2,
					DhenPalette.accent,
					true
				)
			} finally {
				pose.popMatrix()
			}
		}
	}

	@JvmStatic
	fun shouldHideRecipeBook(): Boolean = enabled && hideRecipeBook

	@JvmStatic
	fun shouldCloseRecipeBook(): Boolean = shouldHideRecipeBook() && closeRecipeBook

	@JvmStatic
	fun shouldHideItemCooldown(): Boolean = hidesItemCooldown(SkyBlockLocation.inSkyBlock)

	@JvmStatic
	fun shouldHideHotbarTooltip(): Boolean = enabled && hideHotbarTooltips

	internal fun hidesItemCooldown(inSkyBlock: Boolean): Boolean =
		enabled && hideItemCooldowns && inSkyBlock

	private const val SLOT_CENTER = 8
	private const val CAKE_SCALE = 0.8f
}

internal class CakeYearCache {
	private val stacks = arrayOfNulls<ItemStack>(CAPACITY)
	private val names = arrayOfNulls<Component>(CAPACITY)
	private val years = arrayOfNulls<String>(CAPACITY)
	private var evictionHand = 0

	fun year(stack: ItemStack): String? {
		val name = stack.hoverName
		val home = home(stack)
		for (step in 0 until PROBE) {
			val slot = (home + step) and MASK
			if (stacks[slot] === stack) {
				if (names[slot] === name || names[slot] == name) return years[slot]
				return place(slot, stack, name)
			}
			if (stacks[slot] == null) return place(slot, stack, name)
		}
		return place((home + (evictionHand++ and (PROBE - 1))) and MASK, stack, name)
	}

	private fun place(slot: Int, stack: ItemStack, name: Component): String? {
		val year = parse(name.string)
		stacks[slot] = stack
		names[slot] = name
		years[slot] = year
		return year
	}

	private fun home(stack: ItemStack): Int {
		val hash = System.identityHashCode(stack)
		return (hash xor (hash ushr 16)) and MASK
	}

	companion object {
		private const val CAPACITY = 64
		private const val MASK = CAPACITY - 1
		private const val PROBE = 4
		private const val PREFIX = "New Year Cake (Year "

		internal fun parse(name: String): String? {
			val start = name.indexOf(PREFIX)
			if (start < 0) return null
			val yearStart = start + PREFIX.length
			val end = name.indexOf(')', yearStart)
			if (end < 0) return null
			return name.substring(yearStart, end).trim().ifEmpty { null }
		}
	}
}
