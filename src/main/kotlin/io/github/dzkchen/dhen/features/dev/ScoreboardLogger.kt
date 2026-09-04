package io.github.dzkchen.dhen.features.dev

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ScoreboardUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScoreboardLogger : Module(
	name = "Scoreboard Logger",
	category = Category.DEV,
	description = "Writes every sidebar change to a file under the logs folder."
) {
	private val onlyInDungeonsSetting = BooleanSetting(
		"Only In Dungeons",
		default = true,
		description = "Write nothing outside the Catacombs."
	)

	private val withFormattingSetting = BooleanSetting(
		"With Formatting",
		description = "Keep Hypixel's colour codes in the file."
	)

	private val logModeSetting = SelectorSetting(
		"Log Mode",
		default = CHANGED_LINES,
		options = listOf(CHANGED_LINES, FULL_SNAPSHOT),
		description = "Write only the lines that moved, or the whole sidebar every time."
	)

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private var previous: List<String> = emptyList()

	private var blocks: Channel<String>? = null

	internal val openLogsSetting = ActionSetting(
		"Open Logs Folder",
		description = "Shows the folder the files land in."
	)

	init {
		registerSetting(onlyInDungeonsSetting)
		registerSetting(withFormattingSetting)
		registerSetting(logModeSetting)
		registerSetting(openLogsSetting)

		on<ScoreboardUpdateEvent> { record() }
		on<WorldChangeEvent> { if (it.phase != WorldChange.INIT) restart() }
	}

	override fun onEnabled() {
		restart()
	}

	override fun onDisabled() {
		blocks?.close()
		blocks = null
		previous = emptyList()
	}

	private fun restart() {
		blocks?.close()
		previous = emptyList()
		val channel = Channel<String>(Channel.UNLIMITED)
		blocks = channel
		launch {
			withContext(Dispatchers.IO) {
				var target: Path? = null
				for (block in channel) {
					val path = target ?: opened() ?: continue
					target = path
					runCatching {
						Files.writeString(path, block, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
					}.onFailure { log.error("Scoreboard Logger could not write {}", path, it) }
				}
			}
		}
	}

	private fun record() {
		if (ScoreboardState.objective.isEmpty()) return
		if (onlyInDungeonsSetting.on && SkyBlockLocation.island != Island.CATACOMBS) return
		val current = snapshot()
		if (current == previous) return
		val rows = if (logModeSetting.value == FULL_SNAPSHOT) current else changes(previous, current)
		previous = current
		send(rows)
	}

	private fun snapshot(): List<String> {
		val formatted = withFormattingSetting.on
		val lines = if (formatted) ScoreboardState.lines else ScoreboardState.stripped
		val rows = ArrayList<String>(lines.size + 1)
		rows += if (formatted) ScoreboardState.title else ScoreboardState.strippedTitle
		rows += lines
		return rows
	}

	internal fun changes(before: List<String>, after: List<String>): List<String> {
		val rows = ArrayList<String>()
		val depth = maxOf(before.size, after.size)
		for (index in 0 until depth) {
			val was = before.getOrNull(index)
			val now = after.getOrNull(index)
			if (was != now) rows += "${index + 1}. ${now ?: REMOVED}"
		}
		return rows
	}

	private fun send(rows: List<String>) {
		if (rows.isEmpty()) return
		val channel = blocks ?: return
		val block = StringBuilder()
		block.append(HEAD).append(LocalDateTime.now()).append(TAIL).append('\n')
		for (row in rows) block.append(INDENT).append(row).append('\n')
		block.append('\n')
		channel.trySend(block.toString())
	}

	private fun opened(): Path? = runCatching {
		val folder = Files.createDirectories(directory())
		folder.resolve("scoreboard_${LocalDateTime.now().format(STAMP)}.log")
	}.onFailure { log.error("Scoreboard Logger could not open its folder", it) }.getOrNull()

	internal fun directory(): Path =
		FabricLoader.getInstance().configDir.resolve(Dhen.MOD_ID).resolve(FOLDER)

	private const val CHANGED_LINES = "Changed Lines"
	private const val FULL_SNAPSHOT = "Full Snapshot"
	private const val REMOVED = "(removed)"
	private const val HEAD = "== "
	private const val TAIL = " =="
	private const val INDENT = "  "
	private const val FOLDER = "logs"
	private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd_HH-mm-ss")
}
