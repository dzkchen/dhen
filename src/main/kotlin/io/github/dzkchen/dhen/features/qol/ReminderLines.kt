package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.data.pet.KatDialog
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.TextMemo
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

internal const val TODO_TEMPLATE_PREFIX = "DHEN:TODO/"

internal fun reminderListLines(reminders: List<Reminder>, now: Long): List<Component> {
	if (reminders.isEmpty()) return listOf(DhenType.overWorld("§7No reminders yet. Try /remindme todo Take a break."))
	val lines = ArrayList<Component>(reminders.size + 2)
	lines += DhenType.overWorld("§7Your reminders: ").copy().append(DhenType.buttonOverWorld("§c[remove all]", "/remindme remove all"))
	for (reminder in reminders) lines += reminderLine(reminder, now)
	lines += DhenType.overWorld("§8Open the manager with /remindme gui.")
	return lines
}

internal fun reminderLine(reminder: Reminder, now: Long): Component {
	if (reminder.kind == ReminderKind.KAT) {
		val colour = KatDialog.rarityCode(reminder.message)
		return DhenType.overWorld(
			"§7#${reminder.id} §8| §eKat: $colour${reminder.label} §8| §b${ReminderText.format(reminder.remaining(now))} left §8| §6real time "
		).copy().append(removeButton(reminder))
	}
	if (reminder.kind == ReminderKind.TODO) {
		return DhenType.overWorld("§7#${reminder.id} §8| §e${reminder.display} §8| §atodo ").copy()
			.append(DhenType.buttonOverWorld("§a[done]", "/remindme done ${reminder.id}"))
			.append(DhenType.overWorld(" §8| "))
			.append(removeButton(reminder))
	}
	val time =
		if (reminder.off) ReminderText.format(reminder.durationMs)
		else "${ReminderText.format(reminder.remaining(now))} left"
	val trigger = if (reminder.realTime) "real time" else "play time"
	val repeat = when {
		reminder.totalRepeats == REPEAT_FOREVER -> " §8| §d∞"
		reminder.totalRepeats > REPEAT_ONCE -> " §8| §d${reminder.repeatCount + 1}/${reminder.totalRepeats}"
		else -> ""
	}
	val name = if (reminder.off) "§e${reminder.display} §8(off)" else "§e${reminder.display}"
	val toggleLabel = if (reminder.off) "§a[on]" else "§7[off]"
	return DhenType.overWorld("§7#${reminder.id} §8| $name §8| §b$time §8| §6$trigger$repeat §8| ").copy()
		.append(DhenType.buttonOverWorld(toggleLabel, "/remindme toggle ${reminder.id}"))
		.append(DhenType.overWorld(" §8| "))
		.append(removeButton(reminder))
}

internal fun createdLine(reminder: Reminder, amount: Int, unit: String, repeat: String?): String {
	val trigger = if (reminder.realTime) "real time" else "play time"
	val name = if (reminder.label.isNotEmpty()) " §e\"${reminder.label}\"" else ""
	val head = "§7Created §6$trigger§7 reminder §8#${reminder.id}$name §8- §f${reminder.message}"
	if (repeat == null) return "$head §7in §b$amount $unit"
	val label = if (reminder.totalRepeats == REPEAT_FOREVER) "until removed" else "${reminder.totalRepeats}x"
	return "$head §7every §b$amount $unit §8| §d$label"
}

internal fun todoTemplate(todos: List<Reminder>): String =
	todos.joinToString("\n") { TODO_TEMPLATE_PREFIX + ReminderText.sanitize(it.display) }

internal fun todosInTemplate(text: String): List<String> = text.lineSequence()
	.map { it.trim() }
	.filter { it.startsWith(TODO_TEMPLATE_PREFIX) }
	.map { it.removePrefix(TODO_TEMPLATE_PREFIX).trim() }
	.filter { it.isNotEmpty() }
	.toList()

private fun removeButton(reminder: Reminder): Component =
	DhenType.buttonOverWorld("§c[remove]", "/remindme remove ${reminder.id}")

internal class TodosHudElement : HudElement("Todos", HudAnchor.MIDDLE_RIGHT, -8, 0) {
	private var memos: Array<TextMemo> = emptyArray()
	private var lines: List<String> = PREVIEW
	private var seen = UNSEEN

	override val hasContent: Boolean
		get() = editingHud() || Reminders.enabled && Reminders.todosSetting.on && shownLines() !== PREVIEW

	override fun width(font: Font): Int {
		val shown = shownLines()
		ensureMemos(shown.size)
		var width = 1
		for (index in shown.indices) width = maxOf(width, memos[index].width(font, shown[index]))
		return width
	}

	override fun height(font: Font): Int = shownLines().size * DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val shown = shownLines()
		ensureMemos(shown.size)
		val lineHeight = DhenType.lineHeight(font)
		for (index in shown.indices) {
			memos[index].shadowed(graphics, font, shown[index], 0, index * lineHeight, DhenPalette.TEXT_ON_WORLD, scale)
		}
	}

	override fun invalidateMeasurement() {
		for (memo in memos) memo.invalidate()
	}

	private fun shownLines(): List<String> {
		if (editingHud()) return PREVIEW
		if (seen == Reminders.revision) return lines
		seen = Reminders.revision
		val todos = Reminders.todos()
		lines = if (todos.isEmpty()) PREVIEW else buildList(todos.size + 1) {
			add(HEADER)
			for (todo in todos) add(" §7- §f${todo.display}")
		}
		return lines
	}

	private fun ensureMemos(size: Int) {
		if (memos.size != size) memos = Array(size) { DhenType.memo() }
	}

	private companion object {
		const val UNSEEN = -1
		const val HEADER = "§3Todos"
		val PREVIEW = listOf(HEADER, " §7- §fFinish the Kat upgrade", " §7- §fBuy a Hyperion")
	}
}
