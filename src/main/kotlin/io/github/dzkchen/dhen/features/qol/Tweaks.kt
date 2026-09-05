package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.slotCenteredText
import io.github.dzkchen.dhen.mixin.ServerReconfigScreenAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.toasts.AdvancementToast
import net.minecraft.client.gui.components.toasts.RecipeToast
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen
import net.minecraft.network.Connection
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object Tweaks : Module(
	name = "Tweaks",
	category = Category.QOL,
	description = "Small quality of life fixes for inventories, screens, toasts and the world."
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

	private var hideAdvancementToasts by BooleanSetting(
		"Hide Advancement Toasts",
		description = "Stops advancement pop-ups appearing in the corner."
	)

	private var hideRecipeToasts by BooleanSetting(
		"Hide Recipe Toasts",
		default = true,
		description = "Stops new recipe pop-ups appearing in the corner."
	)

	private var hideSystemToasts by BooleanSetting(
		"Hide System Toasts",
		description = "Stops client warning and status pop-ups appearing in the corner."
	)

	private var skipReconfigure by BooleanSetting(
		"Skip Reconfigure Screen",
		default = true,
		description = "Hides the reconfiguring screen shown while a server moves you."
	)

	private var skipMultiplayerWarning by BooleanSetting(
		"Skip Multiplayer Warning",
		default = true,
		description = "Opens the server list straight from the title screen."
	)

	private var duplicateKeybinds by BooleanSetting(
		"Duplicate Keybinds",
		description = "Lets two controls share one key without the Controls screen calling it a conflict."
	)

	private var fitTitles by BooleanSetting(
		"Fit Title Text",
		default = true,
		description = "Shrinks oversized titles and subtitles so they stay on screen."
	)

	private var steadyNightVision by BooleanSetting(
		"Steady Night Vision",
		default = true,
		description = "Stops the screen flickering as night vision runs out."
	)

	private var hideItemFrames by BooleanSetting(
		"Hide Item Frames",
		description = "Hides the frame around an item frame that is displaying something."
	)

	private val cakeYears = CakeYearCache()

	private val titleMemo = DhenType.memo()

	private val subtitleMemo = DhenType.memo()

	init {
		on<GuiOpenEvent> { opened(it) }
		on<SlotRenderEvent.Post> { event ->
			if (!cakeNumbers || !SkyBlockLocation.inSkyBlock) return@on
			val stack = event.slot.item
			if (!stack.`is`(Items.CAKE)) return@on
			val year = cakeYears.year(stack) ?: return@on
			slotCenteredText(event.graphics, year, event.slot.x + SLOT_CENTER, event.slot.y + SLOT_CENTER, CAKE_SCALE, DhenPalette.accent)
		}
	}

	@JvmStatic
	fun hidesToast(toast: Toast): Boolean = enabled && when (toast) {
		is AdvancementToast -> hideAdvancementToasts
		is RecipeToast -> hideRecipeToasts
		is SystemToast -> hideSystemToasts
		else -> false
	}

	@JvmStatic
	fun steadiesNightVision(): Boolean = enabled && steadyNightVision

	@JvmStatic
	fun hidesItemFrame(frame: ItemFrame): Boolean = enabled && hideItemFrames && !frame.item.isEmpty

	@JvmStatic
	fun titleScale(font: Font, title: Component?, magnification: Float): Float =
		fitted(font, titleMemo, title, magnification)

	@JvmStatic
	fun subtitleScale(font: Font, subtitle: Component?, magnification: Float): Float =
		fitted(font, subtitleMemo, subtitle, magnification)

	private fun fitted(font: Font, memo: TextMemo, text: Component?, magnification: Float): Float {
		if (!enabled || !fitTitles || text == null) return magnification
		val width = memo.width(font, text)
		val room = Minecraft.getInstance().window.guiScaledWidth - TITLE_MARGIN
		if (width <= 0 || width * magnification <= room) return magnification
		return room.toFloat() / width
	}

	private fun opened(event: GuiOpenEvent) {
		val screen = event.screen
		if (skipMultiplayerWarning && screen is SafetyScreen) {
			event.screen = JoinMultiplayerScreen(Minecraft.getInstance().gui.screen() ?: TitleScreen())
			return
		}
		if (skipReconfigure && screen is ServerReconfigScreen) {
			event.screen = QuietReconfigure((screen as ServerReconfigScreenAccessor).reconfigureConnection())
		}
	}

	@JvmStatic
	fun allowsDuplicateKeybinds(): Boolean = enabled && duplicateKeybinds

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
	private const val TITLE_MARGIN = 16
}

private class QuietReconfigure(private val connection: Connection) : Screen(CommonComponents.EMPTY) {
	private var ticksWaited = 0

	override fun tick() {
		ticksWaited++
		if (connection.isConnected) connection.tick() else connection.handleDisconnection()
	}

	override fun shouldCloseOnEsc(): Boolean = ticksWaited >= STALLED_RECONFIGURE_TICKS

	override fun onClose() {
		connection.disconnect(ConnectScreen.ABORT_CONNECTION)
	}
}

private const val STALLED_RECONFIGURE_TICKS = 600

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
