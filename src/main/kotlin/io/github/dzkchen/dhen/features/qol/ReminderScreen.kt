package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.ScrollingStack
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.gui.pillButton
import io.github.dzkchen.dhen.gui.textTop
import io.github.dzkchen.dhen.input.TextInputTarget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

private enum class ReminderTab { LIST, FORM }

private enum class RepeatMode(val label: String) {
	ONCE("Once"),
	TIMES("A set number of times"),
	FOREVER("Until it is removed")
}

private class FormField(val placeholder: String, val digitsOnly: Boolean, val maxLength: Int) {
	var text: String = ""
}

internal class ReminderScreen : LiveWorldScreen(DhenType.component(TITLE)), TextInputTarget {
	private val titleMemo = DhenType.memo()
	private val noticeMemo = DhenType.memo()
	private val emptyMemo = DhenType.memo()
	private val tabMemos = Array(ReminderTab.entries.size) { DhenType.memo() }
	private val labelMemos = Array(ROW_COUNT) { DhenType.memo() }
	private val valueMemos = Array(ROW_COUNT) { DhenType.memo() }
	private val actionMemos = Array(ACTION_COUNT) { DhenType.memo() }
	private val rowMemos = ArrayList<TextMemo>()

	private val name = FormField("optional", digitsOnly = false, maxLength = 24)
	private val message = FormField("what it should say", digitsOnly = false, maxLength = 96)
	private val amount = FormField("0", digitsOnly = true, maxLength = 6)
	private val extra = FormField("0", digitsOnly = true, maxLength = 6)
	private val repeatCount = FormField("2", digitsOnly = true, maxLength = 4)

	private var unit = ReminderUnit.MINUTES
	private var extraUnit = ReminderUnit.SECONDS
	private var realTime = false
	private var visual = ReminderOutput.CHAT
	private var withSound = false
	private var repeatMode = RepeatMode.ONCE
	private var makingTodo = false
	private var editing: Reminder? = null

	private var tab = ReminderTab.LIST
	private var focused = NO_FOCUS
	private var notice = ""
	private var noticeUntil = 0L
	private var confirming = false
	private var shown: List<Reminder> = emptyList()
	private val rows = ScrollingStack(0, ROW_GAP, 0, { shown.size }, { LIST_HEIGHT }, { ROW_HEIGHT })

	override val textInputFocused: Boolean
		get() = focused != NO_FOCUS

	override fun init() = refresh()

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		GlassGui.scrim(graphics, width, height)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val left = panelLeft()
		val top = panelTop()
		val right = left + PANEL_WIDTH
		GlassGui.roundedFrame(graphics, left, top, right, top + PANEL_HEIGHT, PANEL_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		centeredText(graphics, font, titleMemo, TITLE, left, right, top + TITLE_TOP, DhenPalette.accent, TEXT_PAD)
		drawTabs(graphics, left, top, mouseX, mouseY)
		if (tab == ReminderTab.LIST) drawList(graphics, left, top, mouseX, mouseY)
		else drawForm(graphics, left, top, mouseX, mouseY)
		drawNotice(graphics, left, right, top)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val x = event.x().toInt()
		val y = event.y().toInt()
		val left = panelLeft()
		val top = panelTop()
		if (clickedTab(x, y, left, top)) return true
		val handled = if (tab == ReminderTab.LIST) clickedList(x, y, left, top) else clickedForm(x, y, left, top)
		if (handled) return true
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (tab != ReminderTab.LIST) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		val delta = (scrollY * SCROLL_STEP).toInt()
		if (delta == 0 || !rows.scrollBy(delta)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		when (event.key()) {
			GLFW.GLFW_KEY_ESCAPE -> if (focused != NO_FOCUS) {
				focused = NO_FOCUS
				return true
			} else if (tab == ReminderTab.FORM) {
				leaveForm()
				return true
			}

			GLFW.GLFW_KEY_TAB -> if (tab == ReminderTab.FORM) {
				val typable = visibleRows().filter { textField(it) != null }
				if (typable.isEmpty()) return true
				focused = typable[(typable.indexOf(focused) + 1) % typable.size]
				return true
			}

			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> if (tab == ReminderTab.FORM) {
				submit()
				return true
			}

			GLFW.GLFW_KEY_BACKSPACE -> {
				val field = textField(focused) ?: return super.keyPressed(event)
				val text = field.text
				if (text.isNotEmpty()) field.text = text.substring(0, text.offsetByCodePoints(text.length, -1))
				return true
			}
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val field = textField(focused) ?: return super.charTyped(event)
		val codepoint = event.codepoint()
		if (!isPrintable(codepoint)) return super.charTyped(event)
		if (field.digitsOnly && !Character.isDigit(codepoint)) return true
		if (field.text.length >= field.maxLength) return true
		field.text += String(Character.toChars(codepoint))
		return true
	}

	override fun tick() {
		if (tab == ReminderTab.LIST && Reminders.all().size != shown.size) refresh()
	}

	private fun refresh() {
		shown = Reminders.all().sortedBy { it.id }
		rows.reclamp()
		while (rowMemos.size < shown.size * ROW_MEMOS) rowMemos += DhenType.memo()
	}

	private fun drawTabs(graphics: GuiGraphicsExtractor, left: Int, top: Int, mouseX: Int, mouseY: Int) {
		val tabTop = top + TABS_TOP
		for (entry in ReminderTab.entries) {
			val tabLeft = left + TEXT_PAD + entry.ordinal * (TAB_WIDTH + TAB_GAP)
			val selected = tab == entry
			pillButton(
				graphics,
				font,
				tabMemos[entry.ordinal],
				tabLabel(entry),
				tabLeft,
				tabLeft + TAB_WIDTH,
				tabTop,
				TAB_HEIGHT,
				if (selected) tabLeft else mouseX,
				if (selected) tabTop else mouseY,
				TEXT_PAD
			)
		}
	}

	private fun clickedTab(x: Int, y: Int, left: Int, top: Int): Boolean {
		val tabTop = top + TABS_TOP
		if (y !in tabTop until tabTop + TAB_HEIGHT) return false
		for (entry in ReminderTab.entries) {
			val tabLeft = left + TEXT_PAD + entry.ordinal * (TAB_WIDTH + TAB_GAP)
			if (x !in tabLeft until tabLeft + TAB_WIDTH) continue
			if (entry == ReminderTab.LIST) leaveForm() else startCreating()
			return true
		}
		return false
	}

	private fun drawList(graphics: GuiGraphicsExtractor, left: Int, top: Int, mouseX: Int, mouseY: Int) {
		val listLeft = left + TEXT_PAD
		val listRight = left + PANEL_WIDTH - TEXT_PAD
		val listTop = top + LIST_TOP
		if (shown.isEmpty()) {
			centeredText(graphics, font, emptyMemo, EMPTY_LIST, listLeft, listRight, listTop + EMPTY_TOP, DhenPalette.TEXT_DISABLED, TEXT_PAD)
		} else {
			graphics.enableScissor(listLeft, listTop, listRight, listTop + LIST_HEIGHT)
			for (index in shown.indices) {
				val rowTop = listTop + rows.originOf(index)
				if (rowTop + ROW_HEIGHT < listTop || rowTop > listTop + LIST_HEIGHT) continue
				drawRow(graphics, shown[index], index, listLeft, listRight, rowTop, mouseX, mouseY)
			}
			graphics.disableScissor()
		}
		val footTop = top + PANEL_HEIGHT - FOOTER_TOP
		for (slot in FOOTER_LABELS.indices) {
			val buttonLeft = footerLeft(listLeft, slot)
			val label = if (slot == ACTION_CLEAR && confirming) CONFIRM_LABEL else FOOTER_LABELS[slot]
			pillButton(graphics, font, actionMemos[slot], label, buttonLeft, buttonLeft + FOOTER_WIDTH, footTop, BUTTON_HEIGHT, mouseX, mouseY, FOOTER_PAD)
		}
	}

	private fun drawRow(
		graphics: GuiGraphicsExtractor,
		reminder: Reminder,
		index: Int,
		listLeft: Int,
		listRight: Int,
		rowTop: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val hovered = mouseY in rowTop until rowTop + ROW_HEIGHT && mouseX in listLeft until listRight
		RoundedGui.fill(graphics, listLeft, rowTop, listRight, rowTop + ROW_HEIGHT, ROW_RADIUS, GlassGui.raised(hovered))
		val baseline = textTop(font, rowTop, ROW_HEIGHT)
		val nameMemo = rowMemos[index * ROW_MEMOS]
		val detailMemo = rowMemos[index * ROW_MEMOS + 1]
		val buttons = buttonCount(reminder)
		val buttonsLeft = listRight - buttons * (ICON_WIDTH + ICON_GAP)
		val nameRoom = buttonsLeft - listLeft - DETAIL_WIDTH - TEXT_PAD * 2
		val ink = if (reminder.off && reminder.kind == ReminderKind.TIMER) DhenPalette.TEXT_DISABLED else DhenPalette.TEXT_PRIMARY
		val label = nameMemo.fit(font, "#${reminder.id}  ${reminder.display}", nameRoom)
		nameMemo.text(graphics, font, label, listLeft + TEXT_PAD, baseline, ink)
		val detail = detailMemo.fit(font, rowDetail(reminder), DETAIL_WIDTH)
		detailMemo.text(graphics, font, detail, buttonsLeft - TEXT_PAD - detailMemo.width(font, detail), baseline, DhenPalette.TEXT_SECONDARY)
		for (slot in 0 until buttons) {
			val iconLeft = buttonsLeft + slot * (ICON_WIDTH + ICON_GAP)
			val iconHovered = mouseX in iconLeft until iconLeft + ICON_WIDTH && mouseY in rowTop until rowTop + ROW_HEIGHT
			RoundedGui.pill(graphics, iconLeft, rowTop + ICON_INSET, iconLeft + ICON_WIDTH, rowTop + ROW_HEIGHT - ICON_INSET, GlassGui.raised(iconHovered))
			val memo = rowMemos[index * ROW_MEMOS + ICON_MEMO_START + slot]
			centeredText(graphics, font, memo, iconLabel(reminder, slot), iconLeft, iconLeft + ICON_WIDTH, baseline, DhenPalette.TEXT_PRIMARY, 0)
		}
	}

	private fun clickedList(x: Int, y: Int, left: Int, top: Int): Boolean {
		val listLeft = left + TEXT_PAD
		val listRight = left + PANEL_WIDTH - TEXT_PAD
		val footTop = top + PANEL_HEIGHT - FOOTER_TOP
		if (y in footTop until footTop + BUTTON_HEIGHT) {
			for (slot in FOOTER_LABELS.indices) {
				val buttonLeft = footerLeft(listLeft, slot)
				if (x !in buttonLeft until buttonLeft + FOOTER_WIDTH) continue
				when (slot) {
					ACTION_NEW -> startCreating()
					ACTION_COPY -> show(Reminders.exportTodos())
					ACTION_PASTE -> {
						show(Reminders.importTodos())
						refresh()
					}
					else -> clearAll()
				}
				return true
			}
			return false
		}
		val listTop = top + LIST_TOP
		if (x !in listLeft until listRight || y !in listTop until listTop + LIST_HEIGHT) return false
		val index = rows.slotAt(y - listTop)
		val reminder = shown.getOrNull(index) ?: return false
		val rowTop = listTop + rows.originOf(index)
		val buttons = buttonCount(reminder)
		val buttonsLeft = listRight - buttons * (ICON_WIDTH + ICON_GAP)
		if (y !in rowTop until rowTop + ROW_HEIGHT || x < buttonsLeft) return false
		val offset = x - buttonsLeft
		if (offset % (ICON_WIDTH + ICON_GAP) >= ICON_WIDTH) return false
		val slot = offset / (ICON_WIDTH + ICON_GAP)
		if (slot >= buttons) return false
		activate(reminder, slot)
		refresh()
		return true
	}

	private fun clearAll() {
		if (!confirming) {
			confirming = true
			show(CONFIRM_NOTICE)
			return
		}
		confirming = false
		show("Removed ${Reminders.dropAll()} reminder(s).")
		refresh()
	}

	private fun activate(reminder: Reminder, slot: Int) {
		if (reminder.kind == ReminderKind.KAT) {
			Reminders.drop(reminder)
			return
		}
		if (reminder.kind == ReminderKind.TODO) {
			if (slot == TODO_EDIT) startEditing(reminder) else Reminders.drop(reminder)
			return
		}
		when (slot) {
			TIMER_TOGGLE -> Reminders.flip(reminder)
			TIMER_EDIT -> startEditing(reminder)
			TIMER_RESET -> Reminders.restart(reminder)
			else -> Reminders.drop(reminder)
		}
	}

	private fun drawForm(graphics: GuiGraphicsExtractor, left: Int, top: Int, mouseX: Int, mouseY: Int) {
		val rowLeft = left + TEXT_PAD
		val rowRight = left + PANEL_WIDTH - TEXT_PAD
		val controlLeft = rowLeft + FORM_LABEL_WIDTH
		val visible = visibleRows()
		for (position in visible.indices) {
			val index = visible[position]
			val rowTop = top + FORM_TOP + position * FORM_ROW_HEIGHT
			val baseline = textTop(font, rowTop, CONTROL_HEIGHT)
			labelMemos[index].text(graphics, font, ROW_LABELS[index], rowLeft, baseline, DhenPalette.TEXT_SECONDARY)
			val field = textField(index)
			if (field != null) drawTextField(graphics, field, index, controlLeft, rowRight, rowTop, baseline)
			else pillButton(graphics, font, valueMemos[index], cyclerValue(index), controlLeft, rowRight, rowTop, CONTROL_HEIGHT, mouseX, mouseY, TEXT_PAD)
		}
		val actionTop = top + PANEL_HEIGHT - FOOTER_TOP
		val saveLabel = if (editing == null) CREATE_LABEL else SAVE_LABEL
		pillButton(graphics, font, actionMemos[ACTION_SAVE], saveLabel, rowLeft, rowLeft + ACTION_WIDTH, actionTop, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
		pillButton(graphics, font, actionMemos[ACTION_CANCEL], CANCEL_LABEL, rowRight - ACTION_WIDTH, rowRight, actionTop, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
	}

	private fun drawTextField(
		graphics: GuiGraphicsExtractor,
		field: FormField,
		index: Int,
		controlLeft: Int,
		rowRight: Int,
		rowTop: Int,
		baseline: Int
	) {
		val active = focused == index
		RoundedGui.pillFrame(
			graphics,
			controlLeft,
			rowTop,
			rowRight,
			rowTop + CONTROL_HEIGHT,
			GlassGui.interactive(),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		val memo = valueMemos[index]
		val empty = field.text.isEmpty()
		val text = memo.fit(font, if (empty) field.placeholder else field.text, rowRight - controlLeft - TEXT_PAD * 2, fromEnd = true)
		val textLeft = controlLeft + TEXT_PAD
		memo.text(graphics, font, text, textLeft, baseline, if (empty) DhenPalette.TEXT_DISABLED else DhenPalette.TEXT_PRIMARY)
		if (!active || empty) return
		val caret = textLeft + memo.width(font, text)
		SharpGui.fill(graphics, caret, rowTop + CARET_INSET, caret + 1, rowTop + CONTROL_HEIGHT - CARET_INSET, DhenPalette.accent)
	}

	private fun clickedForm(x: Int, y: Int, left: Int, top: Int): Boolean {
		val rowLeft = left + TEXT_PAD
		val rowRight = left + PANEL_WIDTH - TEXT_PAD
		val actionTop = top + PANEL_HEIGHT - FOOTER_TOP
		if (y in actionTop until actionTop + BUTTON_HEIGHT) {
			if (x in rowLeft until rowLeft + ACTION_WIDTH) {
				submit()
				return true
			}
			if (x in rowRight - ACTION_WIDTH until rowRight) {
				leaveForm()
				return true
			}
			return false
		}
		val controlLeft = rowLeft + FORM_LABEL_WIDTH
		focused = NO_FOCUS
		if (x !in controlLeft until rowRight) return false
		val visible = visibleRows()
		for (position in visible.indices) {
			val index = visible[position]
			val rowTop = top + FORM_TOP + position * FORM_ROW_HEIGHT
			if (y !in rowTop until rowTop + CONTROL_HEIGHT) continue
			if (textField(index) != null) focused = index else cycle(index)
			return true
		}
		return false
	}

	private fun cycle(index: Int) {
		when (index) {
			ROW_KIND -> {
				makingTodo = !makingTodo
				focused = NO_FOCUS
			}
			ROW_UNIT -> unit = nextUnit(unit)
			ROW_EXTRA_UNIT -> extraUnit = nextUnit(extraUnit)
			ROW_TRIGGER -> realTime = !realTime
			ROW_OUTPUT -> visual = ReminderOutput.VISUALS[(ReminderOutput.VISUALS.indexOf(visual) + 1) % ReminderOutput.VISUALS.size]
			ROW_SOUND -> withSound = !withSound
			ROW_REPEAT -> repeatMode = RepeatMode.entries[(repeatMode.ordinal + 1) % RepeatMode.entries.size]
		}
	}

	private fun submit() {
		if (makingTodo) {
			submitTodo()
			return
		}
		val durationMs = (amount.text.toLongOrNull() ?: 0L) * unit.multiplierMs +
			(extra.text.toLongOrNull() ?: 0L) * extraUnit.multiplierMs
		if (durationMs <= 0L) {
			show("Give it a time longer than zero.")
			return
		}
		val body = ReminderText.sanitize(message.text)
		if (body.isEmpty()) {
			show("Give it something to say.")
			return
		}
		val repeats = when (repeatMode) {
			RepeatMode.ONCE -> REPEAT_ONCE
			RepeatMode.FOREVER -> REPEAT_FOREVER
			RepeatMode.TIMES -> repeatCount.text.toIntOrNull()?.takeIf { it >= MIN_REPEATS } ?: run {
				show("A repeat count has to be 2 or more.")
				return
			}
		}
		val output = ReminderOutput.composed(visual, withSound)
		val held = editing
		if (held == null) {
			Reminders.add(ReminderKind.TIMER, name.text, body, realTime, output, durationMs, repeats)
			show("Reminder created.")
		} else {
			Reminders.edit(held, name.text, body, realTime, output, durationMs, repeats)
			show("Reminder updated.")
		}
		leaveForm()
	}

	private fun submitTodo() {
		val body = ReminderText.sanitize(message.text)
		if (body.isEmpty()) {
			show("Give the todo some text.")
			return
		}
		val held = editing
		if (held == null) {
			Reminders.add(ReminderKind.TODO, "", body, realTime = false, ReminderOutput.CHAT, 0L, REPEAT_ONCE)
			show("Todo added.")
		} else {
			Reminders.edit(held, "", body, realTime = false, ReminderOutput.CHAT, 0L, REPEAT_ONCE)
			show("Todo updated.")
		}
		leaveForm()
	}

	private fun visibleRows(): List<Int> = when {
		makingTodo && editing != null -> EDIT_TODO_ROWS
		makingTodo -> NEW_TODO_ROWS
		editing != null -> EDIT_TIMER_ROWS
		else -> NEW_TIMER_ROWS
	}

	private fun startCreating() {
		editing = null
		clearForm()
		tab = ReminderTab.FORM
		focused = ROW_NAME
	}

	private fun startEditing(reminder: Reminder) {
		editing = reminder
		makingTodo = reminder.kind == ReminderKind.TODO
		if (makingTodo) {
			message.text = reminder.message
			tab = ReminderTab.FORM
			focused = ROW_MESSAGE
			return
		}
		name.text = reminder.label
		message.text = reminder.message
		realTime = reminder.realTime
		visual = ReminderOutput.visualOf(reminder.output)
		withSound = reminder.output.sound
		repeatMode = when {
			reminder.totalRepeats == REPEAT_FOREVER -> RepeatMode.FOREVER
			reminder.totalRepeats > REPEAT_ONCE -> RepeatMode.TIMES
			else -> RepeatMode.ONCE
		}
		repeatCount.text = if (repeatMode == RepeatMode.TIMES) reminder.totalRepeats.toString() else ""
		val (first, firstUnit) = ReminderText.split(reminder.durationMs)
		amount.text = first.toString()
		unit = firstUnit
		val (second, secondUnit) = ReminderText.split(ReminderText.remainder(reminder.durationMs))
		extra.text = if (second > 0L) second.toString() else ""
		extraUnit = secondUnit
		tab = ReminderTab.FORM
		focused = NO_FOCUS
	}

	private fun leaveForm() {
		editing = null
		clearForm()
		tab = ReminderTab.LIST
		focused = NO_FOCUS
		refresh()
	}

	private fun clearForm() {
		makingTodo = false
		name.text = ""
		message.text = ""
		amount.text = ""
		extra.text = ""
		repeatCount.text = ""
		unit = ReminderUnit.MINUTES
		extraUnit = ReminderUnit.SECONDS
		realTime = false
		visual = ReminderOutput.CHAT
		withSound = false
		repeatMode = RepeatMode.ONCE
		confirming = false
	}

	private fun drawNotice(graphics: GuiGraphicsExtractor, left: Int, right: Int, top: Int) {
		if (notice.isEmpty()) return
		if (System.currentTimeMillis() > noticeUntil) {
			notice = ""
			return
		}
		centeredText(graphics, font, noticeMemo, notice, left, right, top + NOTICE_TOP, DhenPalette.accent, TEXT_PAD)
	}

	private fun show(text: String) {
		notice = text
		noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS
	}

	private fun textField(index: Int): FormField? = when (index) {
		ROW_NAME -> name
		ROW_MESSAGE -> message
		ROW_AMOUNT -> amount
		ROW_EXTRA -> extra
		ROW_REPEAT_COUNT -> repeatCount
		else -> null
	}

	private fun tabLabel(entry: ReminderTab): String = when (entry) {
		ReminderTab.LIST -> LIST_TAB
		ReminderTab.FORM -> if (editing == null) NEW_TAB else EDIT_TAB
	}

	private fun rowDetail(reminder: Reminder): String = when (reminder.kind) {
		ReminderKind.TODO -> TODO_DETAIL
		ReminderKind.KAT -> "Kat · ${ReminderText.format(reminder.remaining(System.currentTimeMillis()))}"
		ReminderKind.TIMER ->
			if (reminder.off) "off · ${ReminderText.format(reminder.durationMs)}"
			else "${ReminderText.format(reminder.remaining(System.currentTimeMillis()))} left"
	}

	private fun buttonCount(reminder: Reminder): Int = when (reminder.kind) {
		ReminderKind.TIMER -> TIMER_BUTTONS
		ReminderKind.TODO -> TODO_BUTTONS
		ReminderKind.KAT -> KAT_BUTTONS
	}

	private fun iconLabel(reminder: Reminder, slot: Int): String = when {
		reminder.kind == ReminderKind.KAT -> REMOVE_ICON
		reminder.kind == ReminderKind.TODO -> when (slot) {
			TODO_EDIT -> EDIT_ICON
			TODO_DONE -> DONE_ICON
			else -> REMOVE_ICON
		}
		slot == TIMER_TOGGLE -> if (reminder.off) ON_ICON else OFF_ICON
		slot == TIMER_EDIT -> EDIT_ICON
		slot == TIMER_RESET -> RESET_ICON
		else -> REMOVE_ICON
	}

	private fun cyclerValue(index: Int): String = when (index) {
		ROW_UNIT -> unit.label
		ROW_EXTRA_UNIT -> extraUnit.label
		ROW_TRIGGER -> if (realTime) REAL_TIME_LABEL else PLAY_TIME_LABEL
		ROW_OUTPUT -> outputLabel()
		ROW_SOUND -> if (withSound) SOUND_ON else SOUND_OFF
		ROW_KIND -> if (makingTodo) TODO_KIND else TIMER_KIND
		else -> repeatMode.label
	}

	private fun outputLabel(): String = when (visual) {
		ReminderOutput.TITLE_BOX -> "A title on screen"
		ReminderOutput.CHAT_AND_TITLE -> "Chat and a title"
		ReminderOutput.SOUND_ONLY -> "Nothing on screen"
		else -> "A chat line"
	}

	private fun nextUnit(current: ReminderUnit): ReminderUnit =
		ReminderUnit.entries[(current.ordinal + 1) % ReminderUnit.entries.size]

	private fun footerLeft(listLeft: Int, slot: Int): Int = listLeft + slot * (FOOTER_WIDTH + FOOTER_GAP)

	private fun panelLeft(): Int = (width - PANEL_WIDTH) / 2

	private fun panelTop(): Int = (height - PANEL_HEIGHT) / 2

	private companion object {
		const val TITLE = "Reminders"
		const val LIST_TAB = "Reminders"
		const val NEW_TAB = "New"
		const val EDIT_TAB = "Edit"
		const val CONFIRM_LABEL = "Confirm"
		const val CONFIRM_NOTICE = "Press it again to remove everything."
		const val CREATE_LABEL = "Create"
		const val SAVE_LABEL = "Save"
		const val CANCEL_LABEL = "Cancel"
		const val EMPTY_LIST = "Nothing here yet. Press New to add a reminder or a todo."
		const val TODO_DETAIL = "todo"
		const val REAL_TIME_LABEL = "Real time"
		const val PLAY_TIME_LABEL = "While playing"
		const val SOUND_ON = "Play a sound"
		const val SOUND_OFF = "No sound"
		const val TIMER_KIND = "A timed reminder"
		const val TODO_KIND = "A todo, with no time"
		const val DONE_ICON = "✔"
		const val REMOVE_ICON = "✖"
		const val EDIT_ICON = "✎"
		const val RESET_ICON = "↺"
		const val ON_ICON = "▶"
		const val OFF_ICON = "❚"

		const val ROW_KIND = 0
		const val ROW_NAME = 1
		const val ROW_MESSAGE = 2
		const val ROW_AMOUNT = 3
		const val ROW_UNIT = 4
		const val ROW_EXTRA = 5
		const val ROW_EXTRA_UNIT = 6
		const val ROW_TRIGGER = 7
		const val ROW_OUTPUT = 8
		const val ROW_SOUND = 9
		const val ROW_REPEAT = 10
		const val ROW_REPEAT_COUNT = 11
		const val ROW_COUNT = 12

		val ROW_LABELS = arrayOf(
			"Kind",
			"Name",
			"Message",
			"Amount",
			"Unit",
			"And",
			"Unit",
			"Clock",
			"Output",
			"Sound",
			"Repeating",
			"How many times"
		)
		val NEW_TIMER_ROWS = (0 until ROW_COUNT).toList()
		val EDIT_TIMER_ROWS = (ROW_NAME until ROW_COUNT).toList()
		val NEW_TODO_ROWS = listOf(ROW_KIND, ROW_MESSAGE)
		val EDIT_TODO_ROWS = listOf(ROW_MESSAGE)
		val FOOTER_LABELS = arrayOf("New", "Copy todos", "Paste todos", "Remove all")

		const val PANEL_WIDTH = 380
		const val PANEL_HEIGHT = 330
		const val PANEL_RADIUS = 6f
		const val TEXT_PAD = 8
		const val TITLE_TOP = 10
		const val NOTICE_TOP = 24
		const val TABS_TOP = 38
		const val TAB_WIDTH = 110
		const val TAB_GAP = 6
		const val TAB_HEIGHT = 20
		const val LIST_TOP = 66
		const val LIST_HEIGHT = 220
		const val ROW_HEIGHT = 24
		const val ROW_GAP = 3
		const val ROW_RADIUS = 4f
		const val DETAIL_WIDTH = 90
		const val ICON_WIDTH = 20
		const val ICON_GAP = 2
		const val ICON_INSET = 3
		const val ICON_MEMO_START = 2
		const val ROW_MEMOS = 6
		const val EMPTY_TOP = 80
		const val FOOTER_TOP = 30
		const val BUTTON_HEIGHT = 20
		const val ACTION_WIDTH = 130
		const val FOOTER_WIDTH = 86
		const val FOOTER_GAP = 6
		const val FOOTER_PAD = 3
		const val FORM_TOP = 62
		const val FORM_ROW_HEIGHT = 19
		const val FORM_LABEL_WIDTH = 100
		const val CONTROL_HEIGHT = 17
		const val CARET_INSET = 4
		const val SCROLL_STEP = 12
		const val NOTICE_MILLIS = 3_000L
		const val NO_FOCUS = -1
		const val TIMER_BUTTONS = 4
		const val TODO_BUTTONS = 3
		const val KAT_BUTTONS = 1
		const val TIMER_TOGGLE = 0
		const val TIMER_EDIT = 1
		const val TIMER_RESET = 2
		const val TODO_EDIT = 0
		const val TODO_DONE = 1
		const val ACTION_NEW = 0
		const val ACTION_COPY = 1
		const val ACTION_PASTE = 2
		const val ACTION_CLEAR = 3
		const val ACTION_SAVE = 4
		const val ACTION_CANCEL = 5
		const val ACTION_COUNT = 6
	}
}
