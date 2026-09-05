package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.command.ReminderCommands
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.SoundSetting
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.pet.KatDialog
import io.github.dzkchen.dhen.data.pet.KatSpecial
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

@Suppress("unused")
object Reminders : Module(
	name = "Reminders",
	category = Category.QOL,
	description = "Keeps timed reminders and untimed todos, and tells you when one comes due."
), ReminderCommands {
	private var storedReminders by StringSetting("Stored Reminders").hide()

	internal val soundSetting = BooleanSetting(
		"Reminder Sound",
		default = true,
		description = "Plays a sound when a reminder set to make one comes due."
	)
	private var soundEnabled by soundSetting

	internal val soundEventSetting = SoundSetting(
		"Sound",
		SoundEvents.EXPERIENCE_ORB_PICKUP,
		description = "The sound a reminder plays."
	).withDependency { soundSetting.on }
	private var soundEvent by soundEventSetting

	internal val volumeSetting = NumberSetting(
		"Volume",
		default = 1.5,
		min = 0.0,
		max = 3.0,
		step = 0.1,
		description = "How loud a reminder sound is."
	).withDependency { soundSetting.on }
	private var volume by volumeSetting

	internal val pitchSetting = NumberSetting(
		"Pitch",
		default = 1.0,
		min = 0.5,
		max = 2.0,
		step = 0.05,
		description = "How high a reminder sound is."
	).withDependency { soundSetting.on }
	private var pitch by pitchSetting

	internal val todosSetting = BooleanSetting(
		"Todos",
		default = true,
		description = "Lists your untimed todos on the HUD until you tick them off."
	)
	private val clock = ReminderClock()
	private val rows = ArrayList<Reminder>()
	private var source: String? = null
	private var nextId = FIRST_ID
	private var pendingKat: KatUpgradeState? = null

	@Volatile
	internal var revision = 0
		private set

	private var lastSaveMs = 0L

	internal val todosElement = hud(TodosHudElement())

	private class KatUpgradeState(val pet: String, val rarity: String)

	init {
		registerSetting(todosSetting)
		on<ClientTickEvent.End> { tick() }
	}

	override fun onEnabled() {
		clock.restart(now())
		lastSaveMs = now()
	}

	override fun onDisabled() {
		pendingKat = null
		save()
	}

	override fun onReset() {
		source = null
		rows.clear()
		pendingKat = null
		revision++
	}

	internal fun all(): List<Reminder> = live()

	internal fun sorted(): List<Reminder> {
		val moment = now()
		return live().sortedWith(compareBy({ if (it.off) 1 else 0 }, { it.remaining(moment) }))
	}

	internal fun todos(): List<Reminder> = live().filter { it.kind == ReminderKind.TODO }

	internal fun byId(id: Int): Reminder? = live().firstOrNull { it.id == id }

	internal fun add(
		kind: ReminderKind,
		label: String,
		message: String,
		realTime: Boolean,
		output: ReminderOutput,
		durationMs: Long,
		totalRepeats: Int
	): Reminder {
		val moment = now()
		val reminder = Reminder(
			id = claimId(),
			kind = kind,
			label = ReminderText.sanitize(label),
			message = ReminderText.sanitize(message),
			realTime = realTime,
			output = output,
			durationMs = durationMs,
			totalRepeats = totalRepeats,
			remainingMs = if (realTime) 0L else durationMs,
			dueAtMs = if (realTime) moment + durationMs else 0L,
			repeatCount = 0,
			off = kind == ReminderKind.TODO
		)
		live().add(reminder)
		save()
		return reminder
	}

	internal fun edit(
		reminder: Reminder,
		label: String,
		message: String,
		realTime: Boolean,
		output: ReminderOutput,
		durationMs: Long,
		totalRepeats: Int
	) {
		val wasOff = reminder.off
		reminder.label = ReminderText.sanitize(label)
		reminder.message = ReminderText.sanitize(message)
		reminder.realTime = realTime
		reminder.output = output
		reminder.durationMs = durationMs
		reminder.totalRepeats = totalRepeats
		reminder.repeatCount = 0
		if (wasOff) {
			reminder.remainingMs = durationMs
			if (realTime) reminder.dueAtMs = now() + durationMs
		} else {
			reminder.restart(now())
		}
		save()
	}

	internal fun drop(reminder: Reminder): Boolean {
		if (!live().remove(reminder)) return false
		save()
		return true
	}

	internal fun dropAll(): Int {
		val count = live().size
		if (count == 0) return 0
		live().clear()
		save()
		return count
	}

	internal fun flip(reminder: Reminder) {
		val moment = now()
		if (reminder.off) {
			reminder.off = false
			if (reminder.totalRepeats != REPEAT_FOREVER && reminder.repeatCount >= reminder.totalRepeats) {
				reminder.repeatCount = 0
			}
			if (reminder.remainingMs <= 0L) reminder.remainingMs = reminder.durationMs
			if (reminder.realTime) reminder.dueAtMs = moment + reminder.remainingMs
			clock.restart(moment)
		} else {
			if (reminder.realTime) reminder.remainingMs = maxOf(0L, reminder.dueAtMs - moment)
			reminder.off = true
		}
		save()
	}

	internal fun snooze(reminder: Reminder, extraMs: Long) {
		if (reminder.realTime) reminder.dueAtMs = maxOf(now(), reminder.dueAtMs) + extraMs
		else reminder.remainingMs += extraMs
		save()
	}

	internal fun restart(reminder: Reminder) {
		val moment = now()
		reminder.repeatCount = 0
		reminder.restart(moment)
		clock.restart(moment)
		save()
	}

	internal fun relabel(reminder: Reminder, name: String) {
		reminder.label = ReminderText.sanitize(name)
		save()
	}

	internal fun katUpgradeSpoken(dialog: String) {
		if (!enabled) return
		val special = KatDialog.special(dialog)
		if (special != null) {
			when (special) {
				KatSpecial.FLOWER -> shortenKat(KatDialog.FLOWER_REDUCTION_MS)
				KatSpecial.BOUQUET -> shortenKat(KatDialog.BOUQUET_REDUCTION_MS)
				KatSpecial.RESET -> pendingKat = null
			}
			return
		}
		val upgrade = KatDialog.upgradeStart(dialog)
		if (upgrade != null) {
			pendingKat = KatUpgradeState(upgrade.pet, upgrade.rarity)
			return
		}
		val remind = KatDialog.reminderStart(dialog)
		if (remind != null) {
			pendingKat = KatUpgradeState(remind, COMMON_RARITY)
			return
		}
		val pending = pendingKat ?: return
		val durationMs = KatDialog.duration(dialog)
		if (durationMs <= 0L) return
		pendingKat = null
		add(
			kind = ReminderKind.KAT,
			label = pending.pet,
			message = pending.rarity,
			realTime = true,
			output = ReminderOutput.CHAT_AND_SOUND,
			durationMs = durationMs,
			totalRepeats = REPEAT_ONCE
		)
	}

	override fun create(
		amount: Int,
		unit: String,
		trigger: String,
		output: String,
		repeat: String?,
		label: String?,
		message: String
	): String {
		val span = ReminderUnit.named(unit) ?: return "'$unit' is not a time unit. Try seconds, minutes, hours or days."
		val realTime = when (trigger.lowercase(Locale.ROOT)) {
			"real_time" -> true
			"while_playing" -> false
			else -> return "'$trigger' is not a trigger. Try while_playing or real_time."
		}
		val kind = ReminderOutput.named(output)
			?: return "'$output' is not an output. Try chat, title_box, chat_and_title or sound_only."
		val repeats = when {
			repeat == null -> REPEAT_ONCE
			repeat.equals("until_removed", ignoreCase = true) -> REPEAT_FOREVER
			else -> repeat.toIntOrNull()?.takeIf { it >= MIN_REPEATS }
				?: return "A repeat count has to be until_removed or a whole number of 2 or more."
		}
		val body = ReminderText.sanitize(message)
		if (body.isEmpty()) return "That reminder needs something to say."
		val reminder = add(
			kind = ReminderKind.TIMER,
			label = label.orEmpty(),
			message = body,
			realTime = realTime,
			output = kind,
			durationMs = amount * span.multiplierMs,
			totalRepeats = repeats
		)
		return createdLine(reminder, amount, span.label, repeat)
	}

	override fun addTodo(text: String): String {
		val body = ReminderText.sanitize(text)
		if (body.isEmpty()) return "That todo needs some text."
		val todo = add(
			kind = ReminderKind.TODO,
			label = "",
			message = body,
			realTime = false,
			output = ReminderOutput.CHAT,
			durationMs = 0L,
			totalRepeats = REPEAT_ONCE
		)
		return "§7Added todo §8#${todo.id} §f$body"
	}

	override fun complete(id: Int): String {
		val todo = byId(id) ?: return missing(id)
		if (todo.kind != ReminderKind.TODO) return "#${todo.id} is a reminder, not a todo. Remove it instead."
		drop(todo)
		return "§7Ticked off §f${todo.display}"
	}

	override fun remove(id: Int): String {
		val reminder = byId(id) ?: return missing(id)
		drop(reminder)
		return "§7Removed §8#${reminder.id} §f${reminder.display}"
	}

	override fun removeAll(confirmed: Boolean): List<Component> {
		if (!confirmed) {
			return listOf(
				DhenType.overWorld("§7Remove every reminder and todo? ").copy()
					.append(DhenType.buttonOverWorld("§c[confirm]", "/remindme remove all_confirmed"))
			)
		}
		val count = dropAll()
		return listOf(DhenType.overWorld("§7Removed $count reminder(s)."))
	}

	override fun rename(id: Int, name: String): String {
		val reminder = byId(id) ?: return missing(id)
		relabel(reminder, name)
		return "§7Renamed §8#${reminder.id} §7to §e${reminder.display}"
	}

	override fun toggle(id: Int): String {
		val reminder = byId(id) ?: return missing(id)
		if (reminder.kind == ReminderKind.TODO) return "A todo has no timer to switch off. Tick it off instead."
		flip(reminder)
		return "§7Reminder §8#${reminder.id} §7is now ${if (reminder.off) "§7off" else "§aon"}"
	}

	override fun snooze(id: Int, amount: Int, unit: String): String {
		val span = ReminderUnit.named(unit) ?: return "'$unit' is not a time unit. Try seconds, minutes, hours or days."
		val reminder = byId(id) ?: return missing(id)
		if (reminder.kind == ReminderKind.TODO) return "A todo has no timer to snooze."
		snooze(reminder, amount * span.multiplierMs)
		return "§7Snoozed §8#${reminder.id} §7for §b$amount ${span.label}"
	}

	override fun list(): List<Component> = reminderListLines(sorted(), now())

	override fun ids(): List<String> = live().map { it.id.toString() }

	override fun openManager(): String {
		Minecraft.getInstance().execute { Minecraft.getInstance().gui.setScreen(ReminderScreen()) }
		return "Opening the reminder manager."
	}

	override fun exportTodos(): String {
		val todos = todos()
		if (todos.isEmpty()) return "There are no todos to copy."
		Minecraft.getInstance().keyboardHandler.setClipboard(todoTemplate(todos))
		return "Copied ${todos.size} todo(s) to the clipboard."
	}

	override fun importTodos(): String {
		val pasted = todosInTemplate(Minecraft.getInstance().keyboardHandler.clipboard)
		if (pasted.isEmpty()) return "The clipboard holds no Dhen todos."
		for (body in pasted) {
			add(
				kind = ReminderKind.TODO,
				label = "",
				message = body,
				realTime = false,
				output = ReminderOutput.CHAT,
				durationMs = 0L,
				totalRepeats = REPEAT_ONCE
			)
		}
		return "Added ${pasted.size} todo(s) from the clipboard."
	}

	private fun missing(id: Int): String = "No reminder is numbered #$id."

	private fun shortenKat(reductionMs: Long) {
		val latest = live().lastOrNull { it.kind == ReminderKind.KAT } ?: return
		latest.dueAtMs = maxOf(now(), latest.dueAtMs - reductionMs)
		save()
	}

	private fun tick() {
		val moment = now()
		val step = clock.step(moment)
		if (step == ReminderClock.IDLE) return
		val client = Minecraft.getInstance()
		val playing = client.player != null && client.level != null
		if (!playing) return
		var fired = false
		var advanced = false
		val current = live()
		var index = 0
		while (index < current.size) {
			val reminder = current[index]
			val realTimeOnly = step == ReminderClock.REAL_TIME_ONLY
			if (reminder.kind == ReminderKind.TODO || (realTimeOnly && !reminder.realTime)) {
				index++
				continue
			}
			if (!reminder.realTime && !reminder.off) advanced = true
			if (!reminder.due(moment, step, true)) {
				index++
				continue
			}
			if (reminder.kind == ReminderKind.KAT && !SkyBlockLocation.inSkyBlock) {
				index++
				continue
			}
			fire(reminder, moment)
			fired = true
			if (reminder.kind == ReminderKind.KAT) {
				current.removeAt(index)
				continue
			}
			reminder.repeatCount++
			val repeating = reminder.totalRepeats == REPEAT_FOREVER || reminder.repeatCount < reminder.totalRepeats
			reminder.restart(moment)
			if (!repeating) {
				reminder.off = true
				reminder.repeatCount = 0
			}
			index++
		}
		if (fired || advanced && moment - lastSaveMs >= SAVE_INTERVAL_MS) save()
	}

	private fun fire(reminder: Reminder, moment: Long) {
		val client = Minecraft.getInstance()
		if (reminder.kind == ReminderKind.KAT) client.player?.sendSystemMessage(katReadyLine(reminder))
		else if (reminder.output.chat) client.player?.sendSystemMessage(dueLine(reminder, moment))
		if (reminder.output.title) DhenAlert.show(reminder.message, TITLE_SUBTITLE, TITLE_TICKS, sound = null)
		if (reminder.output.sound && soundEnabled) {
			client.soundManager.play(SimpleSoundInstance.forUI(soundEvent, pitch.toFloat(), volume.toFloat()))
		}
	}

	private fun dueLine(reminder: Reminder, moment: Long): Component {
		val late = reminder.lateBy(moment)
		val tail = if (late > LATE_THRESHOLD_MS) " §8(fired ${ReminderText.format(late)} late)" else ""
		return DhenType.overWorld("§6⏰ §f${reminder.message}$tail")
	}

	private fun katReadyLine(reminder: Reminder): Component {
		val colour = KatDialog.rarityCode(reminder.message)
		val line = DhenType.overWorld("§fYour $colour${reminder.label}§f is ready to pick up! ").copy()
		return line.append(DhenType.buttonOverWorld("§b[call]", KAT_CALL))
	}

	private fun claimId(): Int {
		live()
		val id = nextId
		nextId++
		return id
	}

	private fun live(): MutableList<Reminder> {
		val text = storedReminders
		if (text === source) return rows
		source = text
		rows.clear()
		nextId = FIRST_ID
		if (text.isNotEmpty()) {
			for (line in text.split(ROW_SEPARATOR)) {
				val reminder = readReminderRow(line) ?: continue
				rows += reminder
				nextId = maxOf(nextId, reminder.id + 1)
			}
		}
		return rows
	}

	private fun save() {
		storedReminders = writtenReminders(live())
		source = storedReminders
		lastSaveMs = now()
		revision++
		persist()
	}

	private fun now(): Long = System.currentTimeMillis()

	internal const val COMMON_RARITY = "COMMON"
	private const val FIRST_ID = 1
	private const val TITLE_TICKS = 60
	private const val TITLE_SUBTITLE = "Reminder"
	private const val LATE_THRESHOLD_MS = 5_000L
	private const val SAVE_INTERVAL_MS = 60_000L
	private const val KAT_CALL = "/call kat"
}
