package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.ui.hud.HudPersistence
import org.slf4j.LoggerFactory

object ModulePersistence {
	private const val HUD = "hud"

	internal val migrations: List<(JsonObject) -> Unit> = emptyList()
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
		return JsonObject().apply { add("modules", modules) }
	}

	fun apply(manager: ModuleManager, doc: JsonObject) {
		val modules = doc.get("modules") as? JsonObject ?: return
		for ((name, element) in modules.entrySet()) {
			val module = manager[name] ?: continue
			val entry = element as? JsonObject ?: continue
			SettingCodec.readInto(entry.get("settings") as? JsonObject, module.settings, name)
			(entry.get("enabled") as? JsonPrimitive)?.takeIf { it.isBoolean }?.let {
				if (it.asBoolean) manager.enable(name) else manager.disable(name)
			}
			(entry.get(HUD) as? JsonObject)?.let {
				try {
					HudPersistence.apply(module.hudElements, it)
				} catch (e: Exception) {
					log.warn("Skipping bad HUD layout in module '{}'", name, e)
				}
			}
		}
	}
}
