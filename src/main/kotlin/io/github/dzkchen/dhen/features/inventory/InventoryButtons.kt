package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.features.visual.FIELD_SEPARATOR
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.util.Locale

internal class ButtonDefault(val item: String, val command: String, val title: String, val tooltip: String = "")

internal class InvButton(private val index: Int) {
	var item: String = DEFAULTS[index].item
		set(value) {
			field = value
			stack = null
		}
	var command: String = DEFAULTS[index].command
	var title: String = DEFAULTS[index].title
		set(value) {
			field = value
			pattern = compile(value)
		}
	var tooltip: String = DEFAULTS[index].tooltip
	var disabled: Boolean = false

	private var stack: ItemStack? = null
	private var pattern: Regex? = compile(title)

	val patternValid: Boolean
		get() = pattern != null

	val label: String
		get() = tooltip.ifEmpty { command }

	val hasLabel: Boolean
		get() = tooltip.isNotEmpty() || command.isNotEmpty()

	fun matches(screenTitle: String): Boolean = pattern?.matches(screenTitle) == true

	fun icon(): ItemStack = stack ?: resolve(item).also { stack = it }

	fun forgetIcon() {
		stack = null
	}

	fun reset() {
		item = DEFAULTS[index].item
		command = DEFAULTS[index].command
		title = DEFAULTS[index].title
		tooltip = DEFAULTS[index].tooltip
		disabled = false
	}

	fun row(): String = listOf(item, command, title, tooltip, if (disabled) "1" else "0").joinToString(FIELD_SEPARATOR)

	fun read(line: String) {
		val fields = line.split(FIELD_SEPARATOR)
		if (fields.size != ROW_FIELDS) return
		item = fields[0]
		command = fields[1]
		title = fields[2]
		tooltip = fields[3]
		disabled = fields[4] == "1"
	}

	private fun compile(source: String): Regex? = try {
		Regex(source)
	} catch (e: Exception) {
		LOGGER.warn("Inventory button {} has a screen title that is not a pattern: '{}'", index, source, e)
		null
	}

	private fun resolve(id: String): ItemStack {
		val vanilla = Identifier.tryParse(id.lowercase(Locale.ROOT))?.let { BuiltInRegistries.ITEM.getOptional(it) }
		if (vanilla != null && vanilla.isPresent) return vanilla.get().defaultInstance
		return ItemRepo.stack(id.uppercase(Locale.ROOT)) ?: ItemStack.EMPTY
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(Dhen.MOD_ID)
		const val ROW_FIELDS = 5
	}
}

object InventoryButtons : Module(
	name = "Inventory Buttons",
	category = Category.INVENTORY,
	description = "Puts command buttons around every SkyBlock menu, like the tabs on the creative inventory."
) {
	private var storedButtons by StringSetting("Stored Buttons").hide()

	private val buttons = Array(COUNT) { InvButton(it) }
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private var source: String? = null
	private var matchedTitle: Component? = null
	private var matchedSource: String? = null
	private var matched = NONE
	private var repoReady = false

	init {
		registerSetting(
			ActionSetting("Edit Buttons", ::openEditor, "Opens a screen where each button's icon, command and tooltip is set.")
		)
		on<ClientTickEvent.End> { repoHold.ensure() }
		on<ScreenRenderEvent.Pre> { hint(it) }
		on<ScreenRenderEvent.Post> { draw(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onDisabled() = repoHold.release()

	override fun onReset() {
		source = null
	}

	internal fun all(): Array<InvButton> {
		if (repoReady != ItemRepo.ready) {
			repoReady = ItemRepo.ready
			for (button in buttons) button.forgetIcon()
		}
		val text = storedButtons
		if (text == source) return buttons
		source = text
		val rows = text.split(ROW_SEPARATOR)
		for (index in buttons.indices) {
			buttons[index].reset()
			rows.getOrNull(index)?.let { if (it.isNotEmpty()) buttons[index].read(it) }
		}
		return buttons
	}

	internal fun save() {
		storedButtons = buttons.joinToString(ROW_SEPARATOR) { it.row() }
		source = storedButtons
		persist()
	}

	internal fun left(origin: ContainerOrigin, column: Int): Int =
		column * (origin.dhenContainerWidth() / COLUMNS) + origin.dhenContainerLeft()

	internal fun top(origin: ContainerOrigin, bottom: Boolean): Int =
		origin.dhenContainerTop() + if (bottom) origin.dhenContainerHeight() - LIP - 1 else LIP - HEIGHT

	internal fun hoveredIndex(origin: ContainerOrigin, mouseX: Int, mouseY: Int, lifted: Int): Int {
		for (index in 0 until COUNT) {
			val bottom = index >= COLUMNS
			val left = left(origin, index % COLUMNS)
			if (mouseX !in left until left + WIDTH) continue
			val top = top(origin, bottom) + step(bottom, index == lifted)
			val height = if (index == lifted) HEIGHT else HEIGHT - STEP
			if (mouseY in top until top + height) return index
		}
		return NONE
	}

	private fun step(bottom: Boolean, lifted: Boolean): Int = when {
		!lifted -> if (bottom) STEP else 0
		bottom -> STEP
		else -> -STEP
	}

	internal fun matchedIndex(screen: Screen): Int {
		val buttons = all()
		if (screen.title === matchedTitle && source == matchedSource) return matched
		matchedTitle = screen.title
		matchedSource = source
		val title = withoutCodes(screen.title.string).trim()
		matched = buttons.indexOfFirst { it.matches(title) }
		return matched
	}

	internal fun paint(
		graphics: GuiGraphicsExtractor,
		origin: ContainerOrigin,
		matched: Int,
		hovered: Int,
		selected: Int,
		editing: Boolean
	) {
		val font = Minecraft.getInstance().font
		val buttons = all()
		for (index in buttons.indices) {
			val button = buttons[index]
			if (button.disabled && !editing) continue
			val bottom = index >= COLUMNS
			val off = button.disabled
			val lifted = !off && (index == hovered || index == selected || index == matched)
			val left = left(origin, index % COLUMNS)
			val top = top(origin, bottom) + if (lifted && !bottom) -STEP else if (lifted) STEP else 0
			val border = when {
				index == selected -> DhenPalette.accent
				off -> DhenPalette.TEXT_DISABLED
				else -> DhenPalette.BORDER
			}
			val fill = if (off) GlassGui.surface() else GlassGui.raised(lifted)
			RoundedGui.frame(graphics, left, top, left + WIDTH, top + HEIGHT, RADIUS, fill, border)
			val iconTop = if (bottom) top + HEIGHT - ICON_INSET - ICON_BOX else top + ICON_INSET
			ItemGui.stack(graphics, button.icon(), left + (WIDTH - ICON_BOX) / 2, iconTop)
			if (!off) continue
			DhenType.text(graphics, font, DISABLED_MARK, left + MARK_INSET, top + MARK_INSET, DhenPalette.TEXT_DISABLED)
		}
	}

	private fun hint(event: ScreenRenderEvent.Pre) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!SkyBlockLocation.inSkyBlock) return
		val origin = screen as ContainerOrigin
		if (origin.dhenHoveredSlot() != null) return
		val index = hoveredIndex(origin, event.mouseX, event.mouseY, matchedIndex(screen))
		if (index == NONE) return
		val button = all()[index]
		if (button.disabled || !button.hasLabel) return
		event.graphics.setTooltipForNextFrame(DhenType.styled(button.label), event.mouseX, event.mouseY)
	}

	private fun draw(event: ScreenRenderEvent.Post) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!SkyBlockLocation.inSkyBlock) return
		val origin = screen as ContainerOrigin
		val matched = matchedIndex(screen)
		paint(
			event.graphics,
			origin,
			matched,
			hoveredIndex(origin, event.mouseX, event.mouseY, matched),
			NONE,
			editing = false
		)
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!SkyBlockLocation.inSkyBlock || event.hoveredSlot != null) return
		if (event.click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
		val origin = event.screen as ContainerOrigin
		val index = hoveredIndex(origin, event.click.x().toInt(), event.click.y().toInt(), matchedIndex(event.screen))
		if (index == NONE) return
		val button = all()[index]
		if (button.disabled) return
		event.cancelled = true
		if (button.command.isEmpty()) return
		Minecraft.getInstance().connection?.sendCommand(button.command)
	}

	private fun openEditor() {
		val client = Minecraft.getInstance()
		client.execute { client.gui.setScreen(InventoryButtonsScreen(client.gui.screen())) }
	}

	internal const val COUNT = 14
	internal const val COLUMNS = 7
	internal const val WIDTH = 26
	internal const val HEIGHT = 32
	internal const val NONE = -1

	private const val LIP = 8
	private const val STEP = 4
	private const val RADIUS = 4f
	private const val ICON_BOX = 16
	private const val ICON_INSET = 5
	private const val MARK_INSET = 2
	private const val DISABLED_MARK = "✖"
}

private val DEFAULTS = arrayOf(
	ButtonDefault("minecraft:diamond_sword", "Skills", "Your Skills"),
	ButtonDefault("minecraft:painting", "Collections", "Collections"),
	ButtonDefault("minecraft:bone", "Pets", "(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?"),
	ButtonDefault("ARMOR_OF_YOG_CHESTPLATE", "Wardrobe", "(?:Wardrobe )?\\((?<currentPage>\\d+)/\\d+\\)(?: Armor Sets)?"),
	ButtonDefault("minecraft:bundle", "Sacks", "Sack of Sacks"),
	ButtonDefault("RUNEBOOK", "Accessories", "Accessory Bag(?: \\(\\d+/\\d+\\))?"),
	ButtonDefault("minecraft:ender_chest", "Storage", "Storage"),
	ButtonDefault("minecraft:grass_block", "warp island", "a^", "Island"),
	ButtonDefault("HUB_PORTAL", "Hub", "a^"),
	ButtonDefault("minecraft:skeleton_skull", "warp dh", "a^", "Dungeon Hub"),
	ButtonDefault("SMOOTH_CHOCOLATE_BAR", "ChocolateFactory", "Chocolate Factory", "Chocolate Factory"),
	ButtonDefault("ESSENCE_GOLD", "Bazaar", "(?:Special )?Bazaar"),
	ButtonDefault("ESSENCE_DIAMOND", "Auction", "(?:Co-op )?Auction House"),
	ButtonDefault("minecraft:crafting_table", "CraftingTable", "Craft Item", "Crafting Table")
)
