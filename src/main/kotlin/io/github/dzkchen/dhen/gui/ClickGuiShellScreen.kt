package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.input.TextInputTarget
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

internal class ClickGuiShellScreen(
	private val categories: List<Category>,
	private val manager: ModuleManager,
	view: ClickGuiState,
	private val persistCore: () -> Unit,
	private val persistModules: () -> Unit,
	private val fontChanged: () -> Unit,
	private val parent: Screen? = null
) : LiveWorldScreen(Component.literal("Dhen")), TextInputTarget {
	private val field = ClickGuiColumnField(view, { width }, { height })
	private val panel = ClickGuiPrefsPanel({ width }, { height })
	private val chrome = ClickGuiChrome({ width }, { height }, ::drawTabBody)
	private val hit = ControlHit()
	private var dragged: SettingControl? = null
	private var dragLeft = 0
	private var dragTop = 0
	private var dragWidth = 0
	private var focused: SettingControl? = null
	private var overlay: SettingControl? = null
	private var seenOptionCounts = SelectorSetting.optionCountRevision
	private var swallowCharKey = GLFW.GLFW_KEY_UNKNOWN

	override val textInputFocused: Boolean
		get() = liveFocus?.acceptsTextInput ?: chrome.acceptsTextInput

	override fun init() {
		blurFocus()
		collapseOverlay()
		cancelDrag()
		field.build(categories, manager.categories)
		chrome.measure(font)
		field.measure(font)
		panel.reclamp()
		applySearch()
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val outsideWorld = minecraft.level == null
		if (outsideWorld) extractPanorama(graphics, a)
		if (Effects.reduced) {
			if (outsideWorld) extractMenuBackground(graphics)
			return
		}
		extractBlurredBackground(graphics)
		GlassGui.scrim(graphics, width, height)
	}

	override fun onClose() {
		val previous = parent
		if (previous == null) super.onClose() else minecraft.gui.setScreen(previous)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		reflowResizedLists()
		chrome.draw(graphics, font, mouseX, mouseY)
	}

	private fun reflowResizedLists() {
		val revision = SelectorSetting.optionCountRevision
		if (revision == seenOptionCounts) return
		seenOptionCounts = revision
		field.reflowAll()
		panel.reclamp()
	}

	private fun drawTabBody(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		if (!chrome.onFeatures) {
			panel.draw(graphics, font, mouseX, mouseY)
			return
		}
		chrome.drawSearch(graphics, font, liveFocus == null, field.isEmpty)
		field.draw(graphics, font, mouseX, mouseY)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val button = event.button()
		val armed = liveFocus
		if (armed != null) {
			val result = armed.captureMouse(button)
			if (result != ControlKey.IGNORED) {
				focused = null
				commitOrReflow(armed, result == ControlKey.COMMITTED)
				return true
			}
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return super.mouseClicked(event, doubleClick)
		}
		blurFocus()
		val x = event.x().toInt()
		val y = event.y().toInt()
		val listed = overlay
		if (listed != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && controlAt(x, y) === listed) pressHostControl(listed, x, y)
			else collapseOverlay()
			return true
		}
		val tab = chrome.tabAt(x, y)
		if (tab != ClickGuiShell.NONE) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) chrome.switchTab(tab)
			return true
		}
		if (!chrome.onFeatures) {
			val control = panel.controlAt(hit, x, y)
			if (control != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressHostControl(control, x, y)
			return true
		}
		if (chrome.searchContains(x, y)) return true
		pressColumn(button, x, y)
		return true
	}

	private fun pressColumn(button: Int, x: Int, y: Int) {
		val slot = field.slotAt(x, y)
		if (slot == ClickGuiShell.NONE) return
		val column = field.columnAt(slot)
		if (y < FIELD_TOP + HEADER_HEIGHT) {
			toggleHeader(column)
			return
		}
		val module = column.rowAt(y)
		if (module != null) {
			field.focusOn(module)
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !field.onChevron(slot, x)) manager.toggle(module)
			else column.toggleSettings(module)
			return
		}
		val control = field.controlAt(hit, slot, x, y)
		if (control != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressHostControl(control, x, y)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() != swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		val control = liveFocus
		if (control != null) {
			val result = control.keyPressed(event.key(), event.modifiers())
			if (result != ControlKey.IGNORED) {
				if (result != ControlKey.CONSUMED) {
					focused = null
					swallowCharKey = event.key()
					commitOrReflow(control, result == ControlKey.COMMITTED)
				}
				return true
			}
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && collapseOverlay()) return true
		if (chrome.onFeatures && clickGuiKey(event.key())) return true
		return super.keyPressed(event)
	}

	private fun clickGuiKey(key: Int): Boolean = when (key) {
		GLFW.GLFW_KEY_BACKSPACE -> backspaceSearch()
		GLFW.GLFW_KEY_ESCAPE -> field.collapseExpandedSettings()
		GLFW.GLFW_KEY_UP -> field.moveFocus(-1)
		GLFW.GLFW_KEY_DOWN -> field.moveFocus(1)
		GLFW.GLFW_KEY_LEFT -> field.jumpColumn(-1)
		GLFW.GLFW_KEY_RIGHT -> field.jumpColumn(1)
		GLFW.GLFW_KEY_TAB -> toggleExpandFocus()
		GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> toggleFocus()
		else -> false
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		if (event.key() == swallowCharKey) swallowCharKey = GLFW.GLFW_KEY_UNKNOWN
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		val armed = liveFocus
		if (armed != null && armed.charTyped(codepoint)) {
			reflowQuarantined(armed)
			return true
		}
		if (swallowCharKey != GLFW.GLFW_KEY_UNKNOWN) return true
		if (!chrome.onFeatures || !isPrintable(codepoint)) return super.charTyped(event)
		if (chrome.typed(codepoint)) applySearch()
		return true
	}

	override fun removed() {
		blurFocus()
		super.removed()
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val control = dragged ?: return super.mouseDragged(event, dragX, dragY)
		control.drag(event.x().toInt() - dragLeft, event.y().toInt() - dragTop, dragWidth)
		trackClient(control)
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (dragged != null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val delta = ((scrollY + scrollX) * SCROLL_STEP).roundToInt()
		if (delta == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		if (!chrome.onFeatures) {
			return panel.scrollBy(delta) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		}
		if (field.scrollBy(mouseX.toInt(), mouseY.toInt(), delta)) return true
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (dragged == null) return super.mouseReleased(event)
		cancelDrag()
		return true
	}

	fun invalidateMeasurements() {
		chrome.invalidateMeasurements(font)
		field.invalidateMeasurements(font)
		panel.invalidateMeasurements()
	}

	private fun pressHostControl(control: SettingControl, x: Int, y: Int) {
		if (pressControl(control, x, y) != ControlPress.CHANGED) return
		if (control.clientOwned) persistClient() else persistModules()
	}

	private fun pressControl(control: SettingControl, x: Int, y: Int): ControlPress? {
		val result = control.press(x - hit.left, y - hit.top, hit.width)
		when (result) {
			ControlPress.TRACK -> {
				dragged = control
				dragLeft = hit.left
				dragTop = hit.top
				dragWidth = hit.width
				trackClient(control)
			}
			ControlPress.FOCUS -> focused = control
			else -> Unit
		}
		syncOverlay(control)
		reflowQuarantined(control)
		if (result == ControlPress.CHANGED || result == ControlPress.RESIZED) reflowHost(control)
		if (result == ControlPress.RESIZED && control.expanded) hit.reveal(control.height)
		return result
	}

	private fun controlAt(x: Int, y: Int): SettingControl? =
		if (chrome.onFeatures) field.controlAt(hit, x, y) else panel.controlAt(hit, x, y)

	private fun reflowHost(control: SettingControl) {
		if (control.clientOwned) panel.reclamp() else field.reflowAll()
	}

	private fun reflowQuarantined(control: SettingControl) {
		if (control.failed) reflowHost(control)
	}

	private fun syncOverlay(control: SettingControl) {
		if (!control.expanded) {
			if (overlay === control) overlay = null
			return
		}
		if (overlay !== control) collapseOverlay()
		overlay = control
	}

	private fun collapseOverlay(): Boolean {
		val control = overlay ?: return false
		overlay = null
		if (!control.collapse()) return false
		reflowHost(control)
		return true
	}

	private fun commitOrReflow(control: SettingControl, committed: Boolean) {
		if (committed) persistArmed(control) else reflowQuarantined(control)
	}

	private fun persistArmed(control: SettingControl) {
		if (control.clientOwned) {
			persistClient()
			return
		}
		field.reflowAll()
		persistModules()
	}

	private fun trackClient(control: SettingControl) {
		if (control.clientOwned) ClientPrefs.sync()
	}

	private fun persistClient() {
		val changedFont = ClientPrefs.sync()
		field.relayout()
		panel.reclamp()
		if (changedFont) fontChanged()
		persistCore()
	}

	private fun toggleHeader(column: ClickGuiColumn) {
		collapseOverlay()
		field.toggleCollapsed(column)
		persistCore()
	}

	private fun backspaceSearch(): Boolean {
		if (!chrome.backspaced()) return false
		applySearch()
		return true
	}

	private fun applySearch() {
		collapseOverlay()
		field.filter(chrome.query)
		cancelDrag()
		field.refilter()
	}

	private fun toggleFocus(): Boolean {
		val module = field.focus ?: return false
		manager.toggle(module)
		return true
	}

	private fun toggleExpandFocus(): Boolean {
		if (field.focus == null) return false
		collapseOverlay()
		return field.toggleFocusedSettings()
	}

	private fun cancelDrag() {
		val control = dragged ?: return
		dragged = null
		persistArmed(control)
	}

	private fun blurFocus() {
		val control = focused ?: return
		focused = null
		commitOrReflow(control, control.blur())
	}

	private val liveFocus: SettingControl?
		get() = focused?.takeIf { !it.failed }
}
