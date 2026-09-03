package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.mixin.AbstractSignEditScreenAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

object AuctionPriceInput : Module(
	name = "Auction Price Input",
	category = Category.ECONOMY,
	description = "Replaces the auction house's starting-bid sign with a Dhen box that understands 10m and undercuts the lowest BIN."
) {
	internal val defaultModeSetting = SelectorSetting(
		"Default Mode",
		NORMAL,
		listOf(NORMAL, UNDERCUT),
		description = "Which pricing mode the box opens in. Undercut asks the lowest BIN minus what you type."
	)

	internal val rememberTextSetting = BooleanSetting(
		"Remember Text",
		description = "Keeps the last price you typed ready in the box next time."
	)

	internal val rememberModeSetting = BooleanSetting(
		"Remember Mode",
		description = "Keeps the last pricing mode you chose instead of going back to the default."
	)

	private var captured: ItemStack? = null
	private var lastText = ""
	private var lastUndercut: Boolean? = null
	private val priceHold = RequirementHold(Prices::active, Prices::require)

	init {
		for (setting in listOf(defaultModeSetting, rememberTextSetting, rememberModeSetting)) registerSetting(setting)

		on<ContainerClickEvent> { event -> capture(event) }
		on<ContainerKeyEvent> { event -> confirm(event) }
		on<GuiOpenEvent> { event -> swap(event) }
		on<ClientTickEvent.End> { priceHold.ensure() }
	}

	override fun onDisabled() {
		captured = null
		priceHold.release()
	}

	internal fun rememberedText(): String = if (rememberTextSetting.on) lastText else ""

	internal fun rememberedUndercut(): Boolean =
		if (rememberModeSetting.on) lastUndercut ?: defaultUndercut() else defaultUndercut()

	internal fun remember(text: String, undercut: Boolean) {
		lastText = text
		lastUndercut = undercut
	}

	internal fun lowestBin(stack: ItemStack): Long? {
		val marketId = SkyBlockItems.of(stack).marketId
		if (marketId.isEmpty()) return null
		return Prices.price(marketId, PriceSource.LOWEST_BIN)?.toLong()
	}

	private fun defaultUndercut(): Boolean = defaultModeSetting.value == UNDERCUT

	private fun capture(event: ContainerClickEvent) {
		if (event.hoveredSlot?.index != PRICE_SLOT) return
		if (title(event.screen) !in CREATE_TITLES) return
		val stack = event.screen.menu.slots.getOrNull(ITEM_SLOT)?.item
		captured = stack?.takeIf { SkyBlockItems.of(it).id.isNotEmpty() }
	}

	private fun confirm(event: ContainerKeyEvent) {
		val key = event.input.key()
		if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return
		val title = title(event.screen)
		val expected = when (title) {
			in CREATE_TITLES -> CREATE_TITLES
			in CONFIRM_TITLES -> CONFIRM_TITLES
			else -> return
		}
		val slotIndex = if (expected === CREATE_TITLES) CREATE_BUTTON_SLOT else CONFIRM_BUTTON_SLOT
		val stack = event.screen.menu.slots.getOrNull(slotIndex)?.item ?: return
		if (stack.item !== greenTerracotta()) return
		if (withoutCodes(stack.hoverName.string) !in expected) return
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val gameMode = client.gameMode ?: return
		event.cancelled = true
		gameMode.handleContainerInput(
			event.screen.menu.containerId,
			slotIndex,
			GLFW.GLFW_MOUSE_BUTTON_LEFT,
			ContainerInput.PICKUP,
			player
		)
	}

	private fun swap(event: GuiOpenEvent) {
		val screen = event.screen as? AbstractSignEditScreen ?: return
		val stack = captured ?: return
		val accessor = screen as AbstractSignEditScreenAccessor
		val sign = accessor.dhenSign() ?: return
		val frontText = accessor.dhenFrontText()
		val text = sign.getText(frontText)
		val lines = Array(SIGN_LINES) { withoutCodes(text.getMessage(it, false).string) }
		if (lines[1] != CARETS || lines[2] != AUCTION_LINE || lines[3] != BID_LINE) return
		captured = null
		event.screen = AuctionInputScreen(sign.blockPos, frontText, lines, stack)
	}

	private fun title(screen: AbstractContainerScreen<*>): String = withoutCodes(screen.title.string)

	private fun greenTerracotta(): Item = Items.DYED_TERRACOTTA.pick(DyeColor.GREEN)

	private const val NORMAL = "Normal"
	private const val UNDERCUT = "Undercut"
	private const val PRICE_SLOT = 31
	private const val ITEM_SLOT = 13
	private const val CREATE_BUTTON_SLOT = 29
	private const val CONFIRM_BUTTON_SLOT = 11
	private const val SIGN_LINES = 4
	private const val CARETS = "^^^^^^^^^^^^^^^"
	private const val AUCTION_LINE = "Your auction"
	private const val BID_LINE = "starting bid"

	private val CREATE_TITLES = setOf("Create BIN Auction", "Create Auction")
	private val CONFIRM_TITLES = setOf("Confirm BIN Auction", "Confirm Auction")
}
