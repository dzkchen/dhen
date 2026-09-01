package io.github.dzkchen.dhen.features.visual

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.ArcGui
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class PetWheelSession {
	val cache = PetWheelCache()
	private val debounce = PetWheelDebounce()

	var actionSlot = PetWheelCache.NO_SLOT
		private set
	var actionInput = ContainerInput.PICKUP
		private set
	var closesAfterAction = false
		private set

	val visibleCount: Int
		get() = (cache.size - cache.page * PetWheelCache.PETS_PER_PAGE).coerceIn(0, PetWheelCache.PETS_PER_PAGE)

	fun refresh(title: net.minecraft.network.chat.Component, windowId: Int, stacks: List<ItemStack>): Boolean {
		val previous = cache.windowId
		if (!cache.refresh(title, windowId, stacks)) return false
		if (previous != windowId) debounce.reset()
		return true
	}

	fun target(visibleIndex: Int): Int = cache.slotAt(visibleIndex)

	fun accept(visibleIndex: Int, quickMove: Boolean, now: Long): Boolean {
		val slot = target(visibleIndex)
		if (slot == PetWheelCache.NO_SLOT || !debounce.accept(now)) return false
		actionSlot = slot
		actionInput = if (quickMove) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
		closesAfterAction = !quickMove
		return true
	}

	fun close(windowId: Int): Boolean {
		if (!cache.close(windowId)) return false
		resetAction()
		debounce.reset()
		return true
	}

	fun reset() {
		cache.reset()
		resetAction()
		debounce.reset()
	}

	private fun resetAction() {
		actionSlot = PetWheelCache.NO_SLOT
		actionInput = ContainerInput.PICKUP
		closesAfterAction = false
	}
}

internal fun samePetStack(displayed: ItemStack, live: ItemStack): Boolean =
	!live.isEmpty && live.`is`(Items.PLAYER_HEAD) && ItemStack.matches(displayed, live)

internal class PetWheelScreen {
	private val session = PetWheelSession()
	private val layout = PetWheelLayout()
	private val stacks = Array(SNAPSHOT_SIZE) { ItemStack.EMPTY }
	private val visibleStacks = Array(PetWheelCache.PETS_PER_PAGE) { ItemStack.EMPTY }
	private val visibleNames = Array(PetWheelCache.PETS_PER_PAGE) { "" }
	private val visibleHeldItems = Array(PetWheelCache.PETS_PER_PAGE) { "" }
	private val keyLabels = Array(PetWheelCache.PETS_PER_PAGE) { "" }
	private val nameMemos = Array(PetWheelCache.PETS_PER_PAGE) { DhenType.memo() }
	private val heldItemMemos = Array(PetWheelCache.PETS_PER_PAGE) { DhenType.memo() }
	private val keyMemos = Array(PetWheelCache.PETS_PER_PAGE) { DhenType.memo() }
	private val headerMemo = DhenType.memo()
	private val buttonMemo = DhenType.memo()
	private val activeMemo = DhenType.memo()

	private var header = ""
	private var activeIndex = PetWheelLayout.NO_INDEX
	private var buttonLeft = 0
	private var buttonTop = 0
	private var buttonRight = 0
	private var buttonBottom = 0

	fun ready(event: ContainerReadyEvent) = refreshed(event.title, event.windowId, event.stacks)

	fun updated(event: ContainerUpdatedEvent) = refreshed(event.title, event.windowId, event.stacks)

	fun closed(event: ContainerClosedEvent) {
		if (!session.close(event.windowId)) return
		clearSnapshot()
	}

	fun render(event: ScreenRenderEvent.Pre) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!custom(screen)) return
		event.cancelled = true
		updateLayout(screen)
		val hovered = layout.hoveredIndex(event.mouseX.toDouble(), event.mouseY.toDouble())
		val selected = if (hovered != PetWheelLayout.NO_INDEX) hovered else activeIndex
		val buttonHovered = insideButton(event.mouseX.toDouble(), event.mouseY.toDouble())
		draw(event.graphics, hovered, selected, buttonHovered)
	}

	fun clicked(event: ContainerClickEvent) {
		if (!custom(event.screen)) return
		updateLayout(event.screen)
		event.cancelled = true
		val click = event.click
		if (insideButton(click.x(), click.y())) {
			session.cache.showVanilla()
			return
		}
		val hovered = layout.hoveredIndex(click.x(), click.y())
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && click.hasShiftDown() && hovered != PetWheelLayout.NO_INDEX) {
			action(event.screen, hovered, quickMove = true)
			return
		}
		val bound = PetWheelInput.resolve(
			click.button(),
			mouse = true,
			session.visibleCount,
			PetDisplay.useHotbarBindsSetting.on,
			PetDisplay.petSlotSettings,
			Minecraft.getInstance().options.keyHotbarSlots
		)
		if (bound != PetWheelLayout.NO_INDEX) {
			action(event.screen, bound, quickMove = false)
			return
		}
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered != PetWheelLayout.NO_INDEX) {
			action(event.screen, hovered, quickMove = false)
		}
	}

	fun keyed(event: ContainerKeyEvent) {
		if (!custom(event.screen)) return
		val index = PetWheelInput.resolve(
			event.input.key(),
			mouse = false,
			session.visibleCount,
			PetDisplay.useHotbarBindsSetting.on,
			PetDisplay.petSlotSettings,
			Minecraft.getInstance().options.keyHotbarSlots
		)
		if (index == PetWheelLayout.NO_INDEX) return
		event.cancelled = true
		action(event.screen, index, quickMove = false)
	}

	fun scrolled(event: ContainerScrollEvent) {
		if (!custom(event.screen) || event.scrollY == 0.0) return
		event.cancelled = true
		session.cache.movePage(if (event.scrollY < 0.0) 1 else -1)
		rebuild()
	}

	fun reset() {
		session.reset()
		clearSnapshot()
	}

	private fun refreshed(title: net.minecraft.network.chat.Component, windowId: Int, incoming: List<ItemStack>) {
		if (!session.refresh(title, windowId, incoming)) return
		var index = 0
		while (index < stacks.size) {
			stacks[index] = incoming.getOrElse(index) { ItemStack.EMPTY }
			index++
		}
		session.cache.showFavouritesOnly(PetDisplay.favouritePetsOnlySetting.on)
		rebuild()
	}

	private fun rebuild() {
		activeIndex = PetWheelLayout.NO_INDEX
		var index = 0
		while (index < PetWheelCache.PETS_PER_PAGE) {
			val slot = session.target(index)
			val stack = if (slot in stacks.indices) stacks[slot] else ItemStack.EMPTY
			visibleStacks[index] = stack
			if (stack.isEmpty) {
				visibleNames[index] = ""
				visibleHeldItems[index] = ""
				keyLabels[index] = ""
			} else {
				visibleNames[index] = withoutCodes(stack.hoverName.string)
				visibleHeldItems[index] = heldItem(stack)
				keyLabels[index] = keyLabel(index)
				if (slot == CurrentPet.menuSlot) activeIndex = index
			}
			index++
		}
		header = "Pets ${session.cache.page + 1}/${session.cache.pages}"
	}

	private fun action(screen: AbstractContainerScreen<*>, visibleIndex: Int, quickMove: Boolean) {
		val slotIndex = session.target(visibleIndex)
		if (slotIndex == PetWheelCache.NO_SLOT) return
		val minecraft = Minecraft.getInstance()
		val player = minecraft.player ?: return
		val gameMode = minecraft.gameMode ?: return
		if (player.containerMenu !== screen.menu || slotIndex !in screen.menu.slots.indices) return
		val slot = screen.menu.slots[slotIndex]
		if (slot.index != slotIndex || !samePetStack(visibleStacks[visibleIndex], slot.item)) return
		if (!session.accept(visibleIndex, quickMove, System.currentTimeMillis())) return
		gameMode.handleContainerInput(
			screen.menu.containerId,
			session.actionSlot,
			LEFT_BUTTON,
			session.actionInput,
			player
		)
		if (session.closesAfterAction) player.closeContainer()
	}

	private fun custom(screen: AbstractContainerScreen<*>): Boolean =
		session.cache.custom && screen.menu.containerId == session.cache.windowId && session.cache.belongsTo(screen.title)

	private fun updateLayout(screen: AbstractContainerScreen<*>) {
		layout.update(screen.width, screen.height, PetDisplay.wheelScaleSetting.value, session.visibleCount)
		val font = Minecraft.getInstance().font
		val buttonWidth = buttonMemo.width(font, VANILLA_MENU) + BUTTON_HORIZONTAL_PADDING
		buttonRight = layout.referenceWidth.roundToInt() - BUTTON_MARGIN
		buttonLeft = buttonRight - buttonWidth
		buttonBottom = layout.referenceHeight.roundToInt() - BUTTON_MARGIN
		buttonTop = buttonBottom - BUTTON_HEIGHT
	}

	private fun insideButton(mouseX: Double, mouseY: Double): Boolean {
		val x = mouseX / layout.referenceScale
		val y = mouseY / layout.referenceScale
		return x >= buttonLeft && x <= buttonRight && y >= buttonTop && y <= buttonBottom
	}

	private fun draw(graphics: GuiGraphicsExtractor, hovered: Int, selected: Int, buttonHovered: Boolean) {
		SharpGui.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), DhenPalette.GLASS_SCRIM)
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.scale(layout.referenceScale, layout.referenceScale)
			drawSegments(graphics, hovered)
			drawItems(graphics, hovered)
			if (selected in 0 until session.visibleCount) drawCenter(graphics, selected)
			drawHeader(graphics)
			drawButton(graphics, buttonHovered)
		} finally {
			pose.popMatrix()
		}
	}

	private fun drawSegments(graphics: GuiGraphicsExtractor, hovered: Int) {
		var index = 0
		while (index < session.visibleCount) {
			val start = (-PI / 2.0 + index * layout.segmentAngle - layout.segmentAngle / 2.0).toFloat()
			val sweep = layout.segmentAngle.toFloat()
			ArcGui.annularSegment(
				graphics,
				layout.centerX,
				layout.centerY,
				layout.innerRadius,
				layout.outerRadius,
				start,
				sweep,
				PetDisplay.segmentColorSetting.value.argb
			)
			if (index == activeIndex) ArcGui.annularSegment(
				graphics,
				layout.centerX,
				layout.centerY,
				layout.innerRadius,
				layout.outerRadius,
				start,
				sweep,
				DhenPalette.accent
			)
			if (index == hovered) ArcGui.annularSegment(
				graphics,
				layout.centerX,
				layout.centerY,
				layout.innerRadius,
				layout.outerRadius,
				start,
				sweep,
				PetDisplay.hoverColorSetting.value.argb
			)
			index++
		}
		drawSeparators(graphics)
	}

	private fun drawSeparators(graphics: GuiGraphicsExtractor) {
		if (session.visibleCount <= 1) return
		val length = (layout.outerRadius - layout.innerRadius + SEPARATOR_OVERDRAW * 2f).roundToInt()
		var index = 0
		while (index < session.visibleCount) {
			val angle = (-PI / 2.0 - layout.segmentAngle / 2.0 + index * layout.segmentAngle).toFloat()
			val inner = layout.innerRadius - SEPARATOR_OVERDRAW
			val pose = graphics.pose()
			pose.pushMatrix()
			try {
				pose.translate(layout.centerX + cos(angle) * inner, layout.centerY + sin(angle) * inner)
				pose.rotate(angle)
				SharpGui.fill(graphics, 0, -SEPARATOR_HALF_WIDTH, length, SEPARATOR_HALF_WIDTH, PetDisplay.separatorColorSetting.value.argb)
			} finally {
				pose.popMatrix()
			}
			index++
		}
	}

	private fun drawItems(graphics: GuiGraphicsExtractor, hovered: Int) {
		var index = 0
		while (index < session.visibleCount) {
			val angle = (-PI / 2.0 + index * layout.segmentAngle).toFloat()
			val radius = (layout.innerRadius + layout.outerRadius) / 2f
			val scale = SEGMENT_ITEM_SCALE * if (index == hovered) HOVER_ITEM_SCALE else 1f
			drawItem(
				graphics,
				visibleStacks[index],
				layout.centerX + cos(angle) * radius,
				layout.centerY + sin(angle) * radius,
				scale
			)
			if (PetDisplay.showKeyLabelsSetting.on) drawKeyBadge(graphics, index, angle, index == hovered)
			index++
		}
	}

	private fun drawKeyBadge(graphics: GuiGraphicsExtractor, index: Int, angle: Float, hovered: Boolean) {
		val label = keyLabels[index]
		if (label.isEmpty()) return
		val font = Minecraft.getInstance().font
		val memo = keyMemos[index]
		val width = memo.width(font, label)
		val textScale = min(KEY_MAX_SCALE, KEY_MAX_TEXT_WIDTH / width.coerceAtLeast(1))
		val badgeWidth = (width * textScale + KEY_HORIZONTAL_PADDING).coerceAtLeast(KEY_MIN_WIDTH).roundToInt()
		val radius = layout.outerRadius - KEY_RADIUS_INSET
		val centerX = (layout.centerX + cos(angle) * radius).roundToInt()
		val centerY = (layout.centerY + sin(angle) * radius).roundToInt()
		val left = centerX - badgeWidth / 2
		val top = centerY - KEY_HEIGHT / 2
		RoundedGui.pill(graphics, left, top, left + badgeWidth, top + KEY_HEIGHT, PetDisplay.segmentColorSetting.value.argb)
		if (hovered) RoundedGui.pill(graphics, left, top, left + badgeWidth, top + KEY_HEIGHT, PetDisplay.hoverColorSetting.value.argb)
		drawCenteredText(graphics, font, memo, label, centerX.toFloat(), centerY - KEY_TEXT_OFFSET, textScale, DhenPalette.TEXT_PRIMARY)
	}

	private fun drawCenter(graphics: GuiGraphicsExtractor, selected: Int) {
		val font = Minecraft.getInstance().font
		val contentScale = (layout.innerRadius / CENTER_SCALE_DIVISOR).coerceIn(CENTER_MIN_SCALE, CENTER_MAX_SCALE)
		val availableWidth = (layout.innerRadius * 2f - CENTER_TEXT_INSET).coerceAtLeast(CENTER_MIN_TEXT_WIDTH)
		val iconScale = min(CENTER_ITEM_SCALE, layout.innerRadius * CENTER_ITEM_FRACTION / ITEM_SIZE)
		drawItem(graphics, visibleStacks[selected], layout.centerX, layout.centerY - CENTER_ITEM_Y * contentScale, iconScale)
		val nameScale = CENTER_NAME_SCALE * contentScale
		val name = nameMemos[selected].fit(font, visibleNames[selected], (availableWidth / nameScale).toInt())
		val nameY = layout.centerY + CENTER_NAME_Y * contentScale
		drawCenteredText(graphics, font, nameMemos[selected], name, layout.centerX, nameY, nameScale, DhenPalette.TEXT_PRIMARY)
		val heldScale = CENTER_HELD_SCALE * contentScale
		val held = heldItemMemos[selected].fit(font, visibleHeldItems[selected], (availableWidth / heldScale).toInt())
		val heldY = nameY + CENTER_LINE_GAP * contentScale
		drawCenteredText(graphics, font, heldItemMemos[selected], held, layout.centerX, heldY, heldScale, DhenPalette.TEXT_SECONDARY)
		if (selected == activeIndex) {
			drawCenteredText(
				graphics,
				font,
				activeMemo,
				ACTIVE,
				layout.centerX,
				heldY + CENTER_ACTIVE_GAP * contentScale,
				CENTER_ACTIVE_SCALE * contentScale,
				DhenPalette.accent
			)
		}
	}

	private fun drawHeader(graphics: GuiGraphicsExtractor) {
		drawCenteredText(
			graphics,
			Minecraft.getInstance().font,
			headerMemo,
			header,
			layout.centerX,
			layout.centerY - layout.outerRadius - HEADER_GAP,
			HEADER_SCALE,
			DhenPalette.TEXT_PRIMARY
		)
	}

	private fun drawButton(graphics: GuiGraphicsExtractor, hovered: Boolean) {
		RoundedGui.pill(
			graphics,
			buttonLeft,
			buttonTop,
			buttonRight,
			buttonBottom,
			if (hovered) PetDisplay.hoverColorSetting.value.argb else PetDisplay.segmentColorSetting.value.argb
		)
		drawCenteredText(
			graphics,
			Minecraft.getInstance().font,
			buttonMemo,
			VANILLA_MENU,
			(buttonLeft + buttonRight) / 2f,
			buttonTop + BUTTON_TEXT_TOP,
			1f,
			DhenPalette.TEXT_PRIMARY
		)
	}

	private fun drawItem(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Float, y: Float, scale: Float) {
		if (stack.isEmpty) return
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(x, y)
			pose.scale(scale, scale)
			graphics.item(stack, -ITEM_HALF, -ITEM_HALF)
		} finally {
			pose.popMatrix()
		}
	}

	private fun drawCenteredText(
		graphics: GuiGraphicsExtractor,
		font: Font,
		memo: TextMemo,
		text: String,
		centerX: Float,
		top: Float,
		scale: Float,
		color: Int
	) {
		val width = memo.width(font, text)
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(centerX - width * scale / 2f, top)
			pose.scale(scale, scale)
			memo.text(graphics, font, text, 0, 0, color)
		} finally {
			pose.popMatrix()
		}
	}

	private fun heldItem(stack: ItemStack): String {
		val lore = SkyBlockItems.lore(stack)
		var index = 0
		while (index < lore.size) {
			val line = withoutCodes(lore[index].string)
			val marker = line.indexOf(HELD_ITEM_MARKER)
			if (marker >= 0) {
				val held = line.substring(marker + HELD_ITEM_MARKER.length).trim()
				if (held.isNotEmpty()) return PET_ITEM_PREFIX + held
			}
			index++
		}
		return PET_ITEM_NONE
	}

	private fun keyLabel(index: Int): String {
		val minecraft = Minecraft.getInstance()
		val shown = if (PetDisplay.useHotbarBindsSetting.on) {
			minecraft.options.keyHotbarSlots[index].translatedKeyMessage.string
		} else {
			val code = PetDisplay.petSlotSettings[index].code
			val type = if (code in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST) {
				InputConstants.Type.MOUSE
			} else {
				InputConstants.Type.KEYSYM
			}
			type.getOrCreate(code).displayName.string
		}
		return shown.uppercase(Locale.ROOT).replace(BUTTON_WORD, MOUSE_ABBREVIATION)
	}

	private fun clearSnapshot() {
		var index = 0
		while (index < stacks.size) {
			stacks[index] = ItemStack.EMPTY
			index++
		}
		rebuild()
	}

	private companion object {
		const val SNAPSHOT_SIZE = 44
		const val LEFT_BUTTON = 0
		const val ITEM_SIZE = 16f
		const val ITEM_HALF = 8
		const val SEGMENT_ITEM_SCALE = 2.25f
		const val HOVER_ITEM_SCALE = 1.12f
		const val SEPARATOR_OVERDRAW = 1f
		const val SEPARATOR_HALF_WIDTH = 1
		const val KEY_RADIUS_INSET = 12f
		const val KEY_HEIGHT = 12
		const val KEY_TEXT_OFFSET = 3.5f
		const val KEY_MAX_SCALE = 0.68f
		const val KEY_MAX_TEXT_WIDTH = 24f
		const val KEY_HORIZONTAL_PADDING = 8f
		const val KEY_MIN_WIDTH = 12f
		const val CENTER_SCALE_DIVISOR = 54f
		const val CENTER_MIN_SCALE = 0.78f
		const val CENTER_MAX_SCALE = 1.12f
		const val CENTER_TEXT_INSET = 14f
		const val CENTER_MIN_TEXT_WIDTH = 36f
		const val CENTER_ITEM_SCALE = 2.475f
		const val CENTER_ITEM_FRACTION = 0.62f
		const val CENTER_ITEM_Y = 14f
		const val CENTER_NAME_Y = 3f
		const val CENTER_NAME_SCALE = 0.78f
		const val CENTER_HELD_SCALE = 0.62f
		const val CENTER_LINE_GAP = 9.5f
		const val CENTER_ACTIVE_GAP = 10f
		const val CENTER_ACTIVE_SCALE = 0.6f
		const val HEADER_GAP = 19f
		const val HEADER_SCALE = 0.85f
		const val BUTTON_MARGIN = 5
		const val BUTTON_HEIGHT = 18
		const val BUTTON_HORIZONTAL_PADDING = 16
		const val BUTTON_TEXT_TOP = 4.5f
		const val VANILLA_MENU = "Vanilla Menu"
		const val HELD_ITEM_MARKER = "Held Item:"
		const val PET_ITEM_PREFIX = "Pet Item: "
		const val PET_ITEM_NONE = "Pet Item: None"
		const val ACTIVE = "ACTIVE"
		const val BUTTON_WORD = "BUTTON "
		const val MOUSE_ABBREVIATION = "M"
	}
}
