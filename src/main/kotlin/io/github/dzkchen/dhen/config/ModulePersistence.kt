package io.github.dzkchen.dhen.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.ui.hud.HudPersistence
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.obj
import org.slf4j.LoggerFactory

object ModulePersistence {
	private const val HUD = "hud"
	private const val MODULES = "modules"
	private const val RETIRED_SOUND_MANAGER_MODULE = "Sound Manager"
	private const val RETIRED_ARROW_HIT_SOUND_MODULE = "Arrow Hit Sound"
	private const val MERGED_VISUAL_TWEAKS_MODULE = "Visual Tweaks"
	private const val FOLDED_LAVA_TO_WATER_MODULE = "Lava to Water"
	private const val FOLDED_DARK_MODE_MODULE = "Dark Mode"
	private const val ENABLED = "enabled"
	private const val SETTINGS = "settings"
	private const val SCOREBOARD_MODULE = "Custom Scoreboard"
	private const val SCOREBOARD_LINES = "Lines"

	private val scoreboardLinesAdded = listOf(
		"Island" to "Player Count",
		"Location" to "Visiting",
		"Visiting" to "Profile",
		"Purse" to "Motes",
		"Motes" to "Bank",
		"Bits" to "Copper",
		"Copper" to "Sowdust",
		"Sowdust" to "Gems",
		"Gems" to "Heat",
		"Heat" to "Cold",
		"Cold" to "North Stars",
		"North Stars" to "Soulflow",
		"Separator 3" to "Cookie Buff",
		"Quiver" to "Power",
		"Power" to "Tuning",
		"Separator 4" to "Objective",
		"Slayer" to "Powder",
		"Powder" to "Mayor"
	)

	internal val migrations: List<(JsonObject) -> Unit> = listOf(
		{ doc: JsonObject -> doc.obj(MODULES)?.remove(RETIRED_SOUND_MANAGER_MODULE) },
		{ doc: JsonObject -> doc.obj(MODULES)?.remove(RETIRED_ARROW_HIT_SOUND_MODULE) },
		{ doc: JsonObject -> doc.obj(MODULES)?.let(::foldVisualTweaks) },
		{ doc: JsonObject -> doc.obj(MODULES)?.let(::offerNewScoreboardLines) }
	)
	internal val version: Int
		get() = migrations.size

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private fun foldVisualTweaks(modules: JsonObject) {
		if (!modules.has(FOLDED_LAVA_TO_WATER_MODULE) && !modules.has(FOLDED_DARK_MODE_MODULE)) return
		val merged = modules.obj(MERGED_VISUAL_TWEAKS_MODULE) ?: JsonObject().also { modules.add(MERGED_VISUAL_TWEAKS_MODULE, it) }
		val settings = merged.obj(SETTINGS) ?: JsonObject().also { merged.add(SETTINGS, it) }
		var anyEnabled = merged.flag(ENABLED)
		for (folded in listOf(FOLDED_LAVA_TO_WATER_MODULE, FOLDED_DARK_MODE_MODULE)) {
			val old = modules.obj(folded)
			modules.remove(folded)
			if (old == null) continue
			old.obj(SETTINGS)?.entrySet()?.forEach { (key, value) -> settings.add(key, value) }
			val wasEnabled = old.flag(ENABLED)
			settings.add(folded, JsonPrimitive(wasEnabled))
			anyEnabled = anyEnabled || wasEnabled
		}
		merged.addProperty(ENABLED, anyEnabled)
	}

	private fun offerNewScoreboardLines(modules: JsonObject) {
		val lines = modules.obj(SCOREBOARD_MODULE)?.obj(SETTINGS)?.getAsJsonArray(SCOREBOARD_LINES) ?: return
		val held = ArrayList<String>(lines.size())
		for (line in lines) held += line.asString
		for ((after, added) in scoreboardLinesAdded) {
			if (added in held) continue
			val at = held.indexOf(after)
			if (at < 0) held += added else held.add(at + 1, added)
		}
		val replacement = JsonArray()
		for (line in held) replacement.add(line)
		modules.obj(SCOREBOARD_MODULE)?.obj(SETTINGS)?.add(SCOREBOARD_LINES, replacement)
	}

	fun snapshot(manager: ModuleManager): JsonObject {
		val modules = JsonObject()
		for (module in manager.modules) {
			val entry = JsonObject()
			entry.addProperty(ENABLED, module.enabled)
			entry.add(SETTINGS, SettingCodec.writeInto(JsonObject(), module.settings))
			if (module.hudElements.isNotEmpty()) entry.add(HUD, HudPersistence.snapshot(module.hudElements))
			modules.add(module.name, entry)
		}
		return JsonObject().apply { add(MODULES, modules) }
	}

	fun apply(manager: ModuleManager, doc: JsonObject) {
		val modules = doc.obj(MODULES) ?: return
		for ((name, element) in modules.entrySet()) {
			val module = manager[name] ?: continue
			val entry = element as? JsonObject ?: continue
			SettingCodec.readInto(entry.obj(SETTINGS), module.settings, name)
			entry.flagOrNull(ENABLED)?.let {
				if (it) manager.enable(name) else manager.disable(name)
			}
			entry.obj(HUD)?.let {
				try {
					HudPersistence.apply(module.hudElements, it)
				} catch (e: Exception) {
					log.warn("Skipping bad HUD layout in module '{}'", name, e)
				}
			}
		}
	}
}
