package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.ColorSetting
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.EnumSetting
import io.github.dzkchen.dhen.api.FloatRangeSetting
import io.github.dzkchen.dhen.api.IntRangeSetting
import io.github.dzkchen.dhen.api.KeybindSetting
import io.github.dzkchen.dhen.api.ListSetting
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.ModuleMetadata
import io.github.dzkchen.dhen.api.ObjectSetting
import io.github.dzkchen.dhen.api.ObjectValueSchema
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.StringSetting
import io.github.dzkchen.dhen.api.StringValueSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConfigManagerTest {
	@Test
	fun materializesDefaultsRecursively(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val manager = ConfigManager(store)
		val addonId = AddonId("config.addon")

		val errors = manager.materializeDefaults(
			addonId,
			listOf(
				BooleanSetting(SettingId("enabled"), "Enabled", default = true),
				ObjectSetting(
					SettingId("advanced"),
					"Advanced",
					fields = listOf(
						IntRangeSetting(SettingId("scale"), "Scale", min = 1, max = 4, default = 2),
						ObjectSetting(
							SettingId("nested"),
							"Nested",
							fields = listOf(StringSetting(SettingId("label"), "Label", default = "demo")),
						),
					),
				),
			),
		)

		assertTrue(errors.isEmpty())
		val saved = store.loadAddonSettings(addonId)
		assertEquals(true, saved["enabled"])
		@Suppress("UNCHECKED_CAST")
		val advanced = saved["advanced"] as Map<String, Any?>
		assertEquals(2.0, advanced["scale"])
		@Suppress("UNCHECKED_CAST")
		val nested = advanced["nested"] as Map<String, Any?>
		assertEquals("demo", nested["label"])
	}

	@Test
	fun materializesDefaultsInsideListItemObjects(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val manager = ConfigManager(store)
		val addonId = AddonId("config.addon")
		store.saveAddonSettings(addonId, mapOf("rows" to listOf(emptyMap<String, Any?>())))

		val errors = manager.materializeDefaults(
			addonId,
			listOf(
				ListSetting(
					SettingId("rows"),
					"Rows",
					ObjectValueSchema(fields = listOf(StringSetting(SettingId("label"), "Label", default = "demo"))),
				),
			),
		)

		assertTrue(errors.isEmpty())
		val saved = store.loadAddonSettings(addonId)
		@Suppress("UNCHECKED_CAST")
		val rows = saved["rows"] as List<Map<String, Any?>>
		assertEquals("demo", rows.single()["label"])
	}

	@Test
	fun reportsMissingRequiredSettings(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val manager = ConfigManager(ConfigStore(platform.configDir, platform.jsonCodec))

		val errors = manager.materializeDefaults(
			AddonId("config.addon"),
			listOf(
				StringSetting(SettingId("name"), "Name", default = null),
				ObjectSetting(
					SettingId("advanced"),
					"Advanced",
					fields = listOf(BooleanSetting(SettingId("required"), "Required", default = null)),
				),
			),
		)

		assertEquals(
			listOf(
				"config.addon.name: required setting is missing",
				"config.addon.advanced.required: required setting is missing",
			),
			errors,
		)
	}

	@Test
	fun reportsInvalidValuesWithUsefulPaths(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val addonId = AddonId("config.addon")
		store.saveAddonSettings(
			addonId,
			mapOf(
				"mode" to "bad",
				"count" to 99,
				"opacity" to 2.0,
				"color" to 0x1000000,
				"key" to -2,
				"names" to listOf("ok", 7),
				"group" to mapOf("label" to 3),
			),
		)

		val errors = ConfigManager(store).materializeDefaults(
			addonId,
			listOf(
				EnumSetting(SettingId("mode"), "Mode", values = listOf("compact", "full")),
				IntRangeSetting(SettingId("count"), "Count", min = 1, max = 3),
				FloatRangeSetting(SettingId("opacity"), "Opacity", min = 0.0, max = 1.0),
				ColorSetting(SettingId("color"), "Color"),
				KeybindSetting(SettingId("key"), "Key"),
				ListSetting(SettingId("names"), "Names", StringValueSchema()),
				ObjectSetting(SettingId("group"), "Group", fields = listOf(StringSetting(SettingId("label"), "Label"))),
			),
		)

		assertEquals(
			listOf(
				"config.addon.mode: expected one of compact, full",
				"config.addon.count: expected integer in range 1..3",
				"config.addon.opacity: expected finite number in range 0.0..1.0",
				"config.addon.color: expected RGB integer in range 0..16777215",
				"config.addon.key: expected key code integer >= -1",
				"config.addon.names[1]: expected string",
				"config.addon.group.label: expected string",
			),
			errors,
		)
	}

	@Test
	fun booleanAccessFallsBackToSchemaDefaultForInvalidStoredValue(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val addonId = AddonId("config.addon")
		store.saveAddonSettings(addonId, mapOf("enabled" to 1))

		val manager = ConfigManager(store)
		val errors = manager.materializeDefaults(addonId, listOf(BooleanSetting(SettingId("enabled"), "Enabled", default = true)))

		assertEquals(listOf("config.addon.enabled: expected boolean"), errors)
		assertTrue(manager.getBoolean(addonId, SettingId("enabled")))
	}

	@Test
	fun requiredBooleanWithoutDefaultDoesNotReadAsFalse(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val addonId = AddonId("config.addon")
		store.saveAddonSettings(addonId, mapOf("enabled" to "bad"))

		val manager = ConfigManager(store)
		val errors = manager.materializeDefaults(
			addonId,
			listOf(BooleanSetting(SettingId("enabled"), "Enabled", default = null)),
		)

		assertEquals(listOf("config.addon.enabled: expected boolean"), errors)
		assertThrows(IllegalStateException::class.java) {
			manager.getBoolean(addonId, SettingId("enabled"))
		}

		val missingAddonId = AddonId("config.missing")
		val missingManager = ConfigManager(store)
		val missingErrors = missingManager.materializeDefaults(
			missingAddonId,
			listOf(BooleanSetting(SettingId("enabled"), "Enabled", default = null)),
		)

		assertEquals(listOf("config.missing.enabled: required setting is missing"), missingErrors)
		assertThrows(IllegalStateException::class.java) {
			missingManager.getBoolean(missingAddonId, SettingId("enabled"))
		}
	}

	@Test
	fun rejectsDuplicateSettingIdsAcrossFlattenedSchemas(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val addonId = AddonId("config.addon")

		val errors = ConfigManager(store).materializeDefaults(
			addonId,
			listOf(
				BooleanSetting(SettingId("enabled"), "Enabled", default = true),
				BooleanSetting(SettingId("enabled"), "Enabled again", default = false),
			),
		)

		assertEquals(listOf("config.addon.enabled: duplicate setting id"), errors)
		assertFalse(store.loadAddonSettings(addonId).containsKey("enabled"))
	}

	@Test
	fun runtimeLogsValidationErrorsWithoutFailingModules(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		ConfigStore(platform.configDir, platform.jsonCodec)
			.saveAddonSettings(AddonId("config.addon"), mapOf("enabled" to "bad"))

		val runtime = DhenRuntime(platform)
		runtime.registerAddon(ConfigAddon())
		runtime.start()

		assertEquals(LifecycleState.DISABLED, runtime.modules().single().state)
		assertTrue(platform.warnings.any { it.contains("Invalid config value: config.addon.enabled: expected boolean") })
	}

	@Test
	fun runtimeLogsDuplicateSettingIdsAcrossModules(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(DuplicateSettingsAddon())

		runtime.start()

		assertTrue(platform.warnings.any { it.contains("Invalid config value: config.addon.enabled: duplicate setting id") })
	}
}

private class ConfigAddon : DhenAddon {
	override val metadata = AddonMetadata(AddonId("config.addon"), "Config", "1.0.0")

	override fun register(context: AddonContext) = context.registerModule(ConfigModule())
}

private class ConfigModule : DhenModule {
	override val metadata = ModuleMetadata(
		id = ModuleId("config.addon:demo"),
		name = "Demo",
		category = ModuleCategory.HUD_OVERLAYS,
		settings = listOf(BooleanSetting(SettingId("enabled"), "Enabled", default = true)),
	)
}

private class DuplicateSettingsAddon : DhenAddon {
	override val metadata = AddonMetadata(AddonId("config.addon"), "Config", "1.0.0")

	override fun register(context: AddonContext) {
		context.registerModule(DuplicateSettingsModule("first", default = true))
		context.registerModule(DuplicateSettingsModule("second", default = false))
	}
}

private class DuplicateSettingsModule(name: String, default: Boolean) : DhenModule {
	override val metadata = ModuleMetadata(
		id = ModuleId("config.addon:$name"),
		name = name,
		category = ModuleCategory.HUD_OVERLAYS,
		settings = listOf(BooleanSetting(SettingId("enabled"), "Enabled", default = default)),
	)
}
