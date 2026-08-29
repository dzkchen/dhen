package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.ui.hud.HudPersistence
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.obj
import org.slf4j.LoggerFactory

object ModulePersistence {
	private const val HUD = "hud"
	private const val MODULES = "modules"
	private const val RETIRED_SOUND_MANAGER_MODULE = "Sound Manager"

	internal val migrations: List<(JsonObject) -> Unit> = listOf(
		{ doc: JsonObject -> doc.obj(MODULES)?.remove(RETIRED_SOUND_MANAGER_MODULE) }
	)
	internal val version: Int
		get() = migrations.size

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun snapshot(manager: ModuleManager): JsonObject {
		val modules = JsonObject()
		for (module in manager.modules) {
			val entry = JsonObject()
			entry.addProperty("enabled", module.enabled)
			entry.add("settings", SettingCodec.writeInto(JsonObject(), module.settings))
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
			SettingCodec.readInto(entry.obj("settings"), module.settings, name)
			entry.get("enabled").flagOrNull()?.let {
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
