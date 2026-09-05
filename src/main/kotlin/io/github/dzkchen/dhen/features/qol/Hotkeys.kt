package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.command.HotkeyCommands
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.config.RowBook
import io.github.dzkchen.dhen.features.visual.FIELD_SEPARATOR
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TablistState
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.features.dungeon.DungeonClass
import io.github.dzkchen.dhen.input.ChordBinding
import io.github.dzkchen.dhen.input.keyCode
import io.github.dzkchen.dhen.input.keyDisplayName
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

internal class Hotkey(val codes: IntArray, val context: HotkeyContext, val command: String)

object Hotkeys : Module(
	name = "Hotkeys",
	category = Category.QOL,
	description = "Runs a command when you press a key, or a run of keys in order, in the places you choose."
), HotkeyCommands {
	private var storedHotkeys by StringSetting("Stored Hotkeys").hide()

	internal val chordDelaySetting = NumberSetting(
		"Chord Delay",
		default = 250.0,
		min = 50.0,
		max = 2000.0,
		step = 50.0,
		description = "How long Dhen waits for the next key of a run before giving up on it, in milliseconds."
	)

	private val book = RowBook<String, Hotkey>({ storedHotkeys }, ::readRow)

	private var registerChord: ((ChordBinding) -> Handle)? = null
	private var handles = emptyList<Handle>()

	internal var ownClass: DungeonClass = DungeonClass.EMPTY
		private set

	init {
		registerSetting(chordDelaySetting)
		on<TablistUpdateEvent> { readOwnClass() }
		on<WorldChangeEvent> { ownClass = DungeonClass.EMPTY }
	}

	internal val chordDelayMillis: Long
		get() = chordDelaySetting.amount.toLong()

	internal fun install(register: (ChordBinding) -> Handle) {
		registerChord = register
		republish()
	}

	override fun onEnabled() = republish()

	override fun onDisabled() {
		release()
		ownClass = DungeonClass.EMPTY
	}

	override fun onReset() = republish()

	override fun add(keys: String, command: String): String {
		val codes = parseChord(keys) ?: return "'$keys' is not a key Dhen knows. Try letters, digits or names like left.shift."
		val body = command.removePrefix("/").trim()
		if (body.isEmpty()) return "That hotkey needs a command to run."
		val key = rowKey(codes, Anywhere)
		if (key in book.all()) return "${chordLabel(codes)} already runs a command everywhere. Remove it first."
		storedHotkeys = written(book.all().values + Hotkey(codes, Anywhere, body))
		republish()
		if (!enabled) return "${chordLabel(codes)} will run /$body once Hotkeys is switched on."
		return "${chordLabel(codes)} now runs /$body."
	}

	override fun remove(target: String): String {
		val current = book.all().values.toList()
		val index = locate(target, current) ?: return "No hotkey matches '$target'."
		val gone = current[index]
		storedHotkeys = written(current.filterIndexed { at, _ -> at != index })
		republish()
		return "Removed ${chordLabel(gone.codes)}."
	}

	override fun scope(target: String, context: String): String {
		val current = book.all().values.toList()
		val index = locate(target, current) ?: return "No hotkey matches '$target'."
		val parsed = parseHotkeyContext(context)
			?: return "'$context' is not a scope. Try always, island:hub, class:berserk, or those joined with & | ! and brackets."
		val old = current[index]
		val moved = rowKey(old.codes, parsed)
		if (current.filterIndexed { at, hotkey -> at != index && rowKey(hotkey.codes, hotkey.context) == moved }.isNotEmpty()) {
			return "Another hotkey on ${chordLabel(old.codes)} already runs when ${parsed.format()}."
		}
		val replaced = Hotkey(old.codes, parsed, old.command)
		storedHotkeys = written(current.mapIndexed { at, hotkey -> if (at == index) replaced else hotkey })
		republish()
		return "${chordLabel(old.codes)} now runs /${old.command} only when ${parsed.format()}."
	}

	override fun list(): List<String> {
		val current = book.all().values.toList()
		if (current.isEmpty()) return listOf("No hotkeys yet. Add one with /dhen hotkey add G,H /warp crypts.")
		return current.mapIndexed { index, hotkey ->
			"${index + 1}. ${chordLabel(hotkey.codes)} runs /${hotkey.command} when ${hotkey.context.format()}"
		} + "Edit one with /dhen hotkey where <number> <scope> or /dhen hotkey remove <number>."
	}

	override fun targets(): List<String> = List(book.all().size) { (it + 1).toString() }

	private fun locate(target: String, current: List<Hotkey>): Int? {
		val index = target.trim().toIntOrNull() ?: return null
		return if (index in 1..current.size) index - 1 else null
	}

	private fun republish() {
		release()
		val register = registerChord ?: return
		if (!enabled) return
		handles = book.all().values.map { hotkey ->
			register(ChordBinding(hotkey.codes, hotkey.context::test) { run(hotkey.command) })
		}
	}

	private fun release() {
		for (handle in handles) handle.unsubscribe()
		handles = emptyList()
	}

	private fun run(command: String) {
		try {
			Minecraft.getInstance().connection?.sendCommand(command)
		} catch (throwable: Throwable) {
			reportError(throwable)
		}
	}

	private fun readOwnClass() {
		if (SkyBlockLocation.island != Island.CATACOMBS) {
			ownClass = DungeonClass.EMPTY
			return
		}
		val self = Minecraft.getInstance().player?.gameProfile?.name ?: return
		for (line in TablistState.stripped) {
			val match = TABLIST_ENTRY.matchEntire(line.trim()) ?: continue
			if (match.groupValues[NAME_GROUP] != self) continue
			val role = match.groupValues[CLASS_GROUP]
			if (role != DEAD) ownClass = DungeonClass.of(role)
			return
		}
	}

	private fun written(hotkeys: Collection<Hotkey>): String =
		hotkeys.joinToString(ROW_SEPARATOR) { "${chordText(it.codes)}$FIELD_SEPARATOR${it.context.format()}$FIELD_SEPARATOR${it.command}" }

	private fun parseChord(keys: String): IntArray? {
		val tokens = keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }
		if (tokens.isEmpty() || tokens.size > MAX_CHORD_KEYS) return null
		val codes = IntArray(tokens.size)
		for (index in tokens.indices) {
			val code = keyCode(tokens[index])
			if (code == GLFW.GLFW_KEY_UNKNOWN) return null
			codes[index] = code
		}
		return codes
	}

	private fun chordText(codes: IntArray): String = codes.joinToString(",")

	private fun chordLabel(codes: IntArray): String = codes.joinToString(" then ", transform = ::keyDisplayName)

	private fun rowKey(codes: IntArray, context: HotkeyContext): String =
		"${chordText(codes)}$FIELD_SEPARATOR${context.format()}"

	private const val MAX_CHORD_KEYS = 4
	private const val NAME_GROUP = 1
	private const val CLASS_GROUP = 2
	private const val DEAD = "DEAD"

	private val TABLIST_ENTRY = Regex("""\[\d+] (?:\[[^]]+] )*([A-Za-z0-9_]{1,16}) .*\((\w+)(?: (\w+))?\)""")
}

private fun readRow(line: String): Pair<String, Hotkey>? {
	val fields = line.split(FIELD_SEPARATOR, limit = 3)
	if (fields.size != 3) return null
	val tokens = fields[0].split(',').filter { it.isNotEmpty() }
	if (tokens.isEmpty()) return null
	val codes = IntArray(tokens.size)
	for (index in tokens.indices) codes[index] = tokens[index].toIntOrNull() ?: return null
	val context = parseHotkeyContext(fields[1]) ?: return null
	if (fields[2].isEmpty()) return null
	return "${fields[0]}$FIELD_SEPARATOR${fields[1]}" to Hotkey(codes, context, fields[2])
}
