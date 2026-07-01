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
import java.nio.file.Files
import java.nio.file.Path

class ConfigManagerTest {
	@Test
	fun firstLaunchCreatesVersionedCoreAndAddonFiles(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(ConfigAddon())

		runtime.start()

		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		assertTrue(Files.exists(store.coreFile))
		assertTrue(Files.exists(store.addonsDir.resolve("config.addon.json")))

		val core = decodedMap(platform, store.coreFile)
		assertEquals(1.0, core["schemaVersion"])
		assertEquals(emptyList<String>(), core["enabledModules"])

		val addon = decodedMap(platform, store.addonsDir.resolve("config.addon.json"))
		assertEquals(1.0, addon["schemaVersion"])
		assertTrue(addon["settings"] is Map<*, *>)
	}

	@Test
	fun atomicWritePreservesOriginalWhenValidationFails(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val goodStore = ConfigStore(platform.configDir, platform.jsonCodec)
		goodStore.saveCoreState(CoreConfigState(enabledModules = setOf("config.addon:demo")))
		val before = Files.readString(goodStore.coreFile)

		val badStore = ConfigStore(
			platform.configDir,
			object : JsonCodec {
				override fun encode(value: Any?): String = "{"
				override fun decode(text: String): Any? = throw IllegalArgumentException("bad json")
			},
		)

		assertThrows(IllegalArgumentException::class.java) {
			badStore.saveCoreState(CoreConfigState(enabledModules = setOf("other.addon:demo")))
		}
		assertEquals(before, Files.readString(goodStore.coreFile))
		assertEquals(setOf("config.addon:demo"), goodStore.loadEnabledModules())
	}

	@Test
	fun saveEnabledStatePersistsModulesAndAddonsInOneAtomicWrite(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val goodStore = ConfigStore(platform.configDir, platform.jsonCodec)
		goodStore.saveCoreState(
			CoreConfigState(enabledModules = setOf("config.addon:demo"), enabledAddons = setOf("config.addon")),
		)
		val before = Files.readString(goodStore.coreFile)

		val badStore = ConfigStore(
			platform.configDir,
			object : JsonCodec {
				override fun encode(value: Any?): String = throw IllegalArgumentException("bad json")
				override fun decode(text: String): Any? = platform.jsonCodec.decode(text)
			},
		)

		assertThrows(IllegalArgumentException::class.java) {
			badStore.saveEnabledState(emptySet(), emptySet())
		}

		assertEquals(before, Files.readString(goodStore.coreFile))
		val reloaded = goodStore.loadCoreState()
		assertEquals(setOf("config.addon:demo"), reloaded.enabledModules)
		assertEquals(setOf("config.addon"), reloaded.enabledAddons)
	}

	@Test
	fun badJsonLoadsDefaultState(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		Files.createDirectories(store.coreFile.parent)
		Files.writeString(store.coreFile, "{")

		assertEquals(CoreConfigState(), store.loadCoreState())
	}

	@Test
	fun corruptAddonConfigIsWarnedAndPreserved(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec, logger = platform.logger("dhen-config"))
		val addonFile = store.addonsDir.resolve("config.addon.json")
		Files.createDirectories(addonFile.parent)
		Files.writeString(addonFile, "{ not json")

		val settings = store.loadAddonSettings(AddonId("config.addon"))

		assertEquals(emptyMap<String, Any?>(), settings)
		assertTrue(Files.exists(addonFile.resolveSibling("config.addon.json.corrupt")))
		assertTrue(platform.warnings.any { it.contains("Failed to read config") })
	}

	@Test
	fun legacyCoreConfigMigratesToVersionedEnvelope(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		Files.createDirectories(store.coreFile.parent)
		Files.writeString(store.coreFile, platform.jsonCodec.encode(mapOf("enabledModules" to listOf("config.addon:demo"))))

		val state = store.loadCoreState()

		assertEquals(setOf("config.addon:demo"), state.enabledModules)
		val persisted = decodedMap(platform, store.coreFile)
		assertEquals(1.0, persisted["schemaVersion"])
		assertEquals(listOf("config.addon:demo"), persisted["enabledModules"])
		assertEquals(emptyList<String>(), persisted["enabledAddons"])
		assertTrue(persisted["installedAddons"] is Map<*, *>)
		assertTrue(persisted["pendingRestartAddons"] is Map<*, *>)
		assertTrue(persisted["hudLayout"] is Map<*, *>)
		assertTrue(persisted["keybinds"] is Map<*, *>)
		assertTrue(persisted["conflictPreferences"] is Map<*, *>)
	}

	@Test
	fun legacyAddonConfigMigratesPlainSettingsToVersionedEnvelope(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val path = store.addonsDir.resolve("config.addon.json")
		Files.createDirectories(path.parent)
		Files.writeString(path, platform.jsonCodec.encode(mapOf("enabled" to true)))

		assertEquals(mutableMapOf("enabled" to true), store.loadAddonSettings(AddonId("config.addon")))

		val persisted = decodedMap(platform, path)
		assertEquals(1.0, persisted["schemaVersion"])
		@Suppress("UNCHECKED_CAST")
		val settings = persisted["settings"] as Map<String, Any?>
		assertEquals(true, settings["enabled"])
	}

	@Test
	fun addonConfigWithoutSchemaVersionKeepsExistingSettingsEnvelope(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val path = store.addonsDir.resolve("config.addon.json")
		Files.createDirectories(path.parent)
		Files.writeString(path, platform.jsonCodec.encode(mapOf("settings" to mapOf("enabled" to true))))

		assertEquals(mutableMapOf("enabled" to true), store.loadAddonSettings(AddonId("config.addon")))

		val persisted = decodedMap(platform, path)
		assertEquals(1.0, persisted["schemaVersion"])
		@Suppress("UNCHECKED_CAST")
		val settings = persisted["settings"] as Map<String, Any?>
		assertEquals(true, settings["enabled"])
		assertFalse(settings.containsKey("settings"))
	}

	@Test
	fun unknownFieldsSurviveCoreReadModifyWrite(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		Files.createDirectories(store.coreFile.parent)
		Files.writeString(
			store.coreFile,
			platform.jsonCodec.encode(
				mapOf(
					"schemaVersion" to 1,
					"enabledModules" to emptyList<String>(),
					"futureField" to mapOf("kept" to true),
				),
			),
		)

		store.saveCoreState(store.loadCoreState().copy(enabledModules = setOf("config.addon:demo")))

		val persisted = decodedMap(platform, store.coreFile)
		@Suppress("UNCHECKED_CAST")
		val future = persisted["futureField"] as Map<String, Any?>
		assertEquals(true, future["kept"])
		assertEquals(listOf("config.addon:demo"), persisted["enabledModules"])
	}

	@Test
	fun allCoreRuntimeStateFieldsSurviveReadModifyWrite(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)

		store.saveEnabledModules(setOf("config.addon:demo"))
		store.saveEnabledAddons(setOf("config.addon"))
		store.saveInstalledAddons(
			mapOf(
				"config.addon" to InstalledAddonState(
					addonId = "config.addon",
					version = "1.2.3",
					artifactType = "Native JAR",
					source = "/addons/config.jar",
					checksum = "sha256:abc",
					signatureStatus = "trusted",
					installedAtEpochMillis = 123456789L,
					unknownFields = mapOf("catalogId" to "test"),
				),
			),
		)
		store.savePendingRestartAddons(
			mapOf(
				"config.addon" to PendingRestartAddonState(
					addonId = "config.addon",
					operation = "install",
					version = "2.0.0",
					artifactPath = "/downloads/config.jar",
					unknownFields = mapOf("reason" to "native"),
				),
			),
		)
		store.saveHudLayout(
			mapOf(
				"config.addon:demo" to HudLayoutState(
					x = 12,
					y = 34,
					enabled = false,
					unknownFields = mapOf("anchor" to "top-left"),
				),
			),
		)
		store.saveKeybinds(mapOf("config.addon:demo/greet" to 71))
		store.saveConflictPreferences(mapOf("config.addon:demo" to "config.addon:other"))

		val reloaded = ConfigStore(platform.configDir, platform.jsonCodec).loadCoreState()

		assertEquals(setOf("config.addon:demo"), reloaded.enabledModules)
		assertEquals(setOf("config.addon"), reloaded.enabledAddons)
		assertEquals(
			InstalledAddonState(
				addonId = "config.addon",
				version = "1.2.3",
				artifactType = "Native JAR",
				source = "/addons/config.jar",
				checksum = "sha256:abc",
				signatureStatus = "trusted",
				installedAtEpochMillis = 123456789L,
				unknownFields = mapOf("catalogId" to "test"),
			),
			reloaded.installedAddons["config.addon"],
		)
		assertEquals(
			PendingRestartAddonState(
				addonId = "config.addon",
				operation = "install",
				version = "2.0.0",
				artifactPath = "/downloads/config.jar",
				unknownFields = mapOf("reason" to "native"),
			),
			reloaded.pendingRestartAddons["config.addon"],
		)
		assertEquals(
			HudLayoutState(
				x = 12,
				y = 34,
				enabled = false,
				unknownFields = mapOf("anchor" to "top-left"),
			),
			reloaded.hudLayout["config.addon:demo"],
		)
		assertEquals(mapOf("config.addon:demo/greet" to 71), reloaded.keybinds)
		assertEquals(mapOf("config.addon:demo" to "config.addon:other"), reloaded.conflictPreferences)
	}

	@Test
	fun futureCoreSchemaVersionSurvivesKnownFieldWrite(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		Files.createDirectories(store.coreFile.parent)
		Files.writeString(
			store.coreFile,
			platform.jsonCodec.encode(
				mapOf(
					"schemaVersion" to 2,
					"enabledModules" to emptyList<String>(),
					"futureField" to mapOf("kept" to true),
				),
			),
		)

		store.saveEnabledModules(setOf("config.addon:demo"))

		val persisted = decodedMap(platform, store.coreFile)
		assertEquals(2.0, persisted["schemaVersion"])
		assertEquals(listOf("config.addon:demo"), persisted["enabledModules"])
		@Suppress("UNCHECKED_CAST")
		val future = persisted["futureField"] as Map<String, Any?>
		assertEquals(true, future["kept"])
	}

	@Test
	fun futureAddonSchemaVersionSurvivesSettingsWrite(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(platform.configDir, platform.jsonCodec)
		val addonId = AddonId("config.addon")
		val path = store.addonsDir.resolve("config.addon.json")
		Files.createDirectories(path.parent)
		Files.writeString(
			path,
			platform.jsonCodec.encode(
				mapOf(
					"schemaVersion" to 2,
					"settings" to mapOf("enabled" to false),
					"futureField" to mapOf("kept" to true),
				),
			),
		)

		store.saveAddonSettings(addonId, mapOf("enabled" to true))

		val persisted = decodedMap(platform, path)
		assertEquals(2.0, persisted["schemaVersion"])
		@Suppress("UNCHECKED_CAST")
		val settings = persisted["settings"] as Map<String, Any?>
		assertEquals(true, settings["enabled"])
		@Suppress("UNCHECKED_CAST")
		val future = persisted["futureField"] as Map<String, Any?>
		assertEquals(true, future["kept"])
	}

	@Test
	fun moduleAliasesApplyToPersistedRuntimeState(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(
			platform.configDir,
			platform.jsonCodec,
			ConfigAliases(moduleAliases = mapOf("old.addon:demo" to "config.addon:demo")),
		)
		Files.createDirectories(store.coreFile.parent)
		Files.writeString(
			store.coreFile,
			platform.jsonCodec.encode(
				mapOf(
					"enabledModules" to listOf("old.addon:demo"),
					"hudLayout" to mapOf("old.addon:demo/hud" to mapOf("x" to 5, "y" to 7)),
					"keybinds" to mapOf("old.addon:demo/greet" to 71),
					"conflictPreferences" to mapOf("old.addon:demo" to "old.addon:demo"),
				),
			),
		)

		val state = store.loadCoreState()

		assertEquals(setOf("config.addon:demo"), state.enabledModules)
		assertEquals(setOf("config.addon:demo/hud"), state.hudLayout.keys)
		assertEquals(setOf("config.addon:demo/greet"), state.keybinds.keys)
		assertEquals(mapOf("config.addon:demo" to "config.addon:demo"), state.conflictPreferences)
	}

	@Test
	fun moduleAliasCollisionsPreferDestinationStateRegardlessOfJsonOrder(@TempDir tmp: Path) {
		val aliases = ConfigAliases(
			moduleAliases = mapOf(
				"old.addon:demo" to "config.addon:demo",
				"old.addon:other" to "config.addon:other",
			),
		)
		val cases = listOf(
			linkedMapOf(
				"old.addon:demo" to mapOf("x" to 1, "y" to 1),
				"config.addon:demo" to mapOf("x" to 9, "y" to 9),
			),
			linkedMapOf(
				"config.addon:demo" to mapOf("x" to 9, "y" to 9),
				"old.addon:demo" to mapOf("x" to 1, "y" to 1),
			),
		)

		for ((index, hudLayout) in cases.withIndex()) {
			val platform = FakePlatformServices(tmp.resolve("case-$index"))
			val store = ConfigStore(platform.configDir, platform.jsonCodec, aliases)
			Files.createDirectories(store.coreFile.parent)
			Files.writeString(
				store.coreFile,
				platform.jsonCodec.encode(
					mapOf(
						"schemaVersion" to 1,
						"hudLayout" to hudLayout,
						"keybinds" to linkedMapOf(
							"old.addon:demo/greet" to 1,
							"config.addon:demo/greet" to 9,
						),
						"conflictPreferences" to linkedMapOf(
							"old.addon:demo" to "old.addon:other",
							"config.addon:demo" to "config.addon:other",
						),
					),
				),
			)

			val state = store.loadCoreState()

			assertEquals(HudLayoutState(x = 9, y = 9), state.hudLayout["config.addon:demo"])
			assertEquals(mapOf("config.addon:demo/greet" to 9), state.keybinds)
			assertEquals(mapOf("config.addon:demo" to "config.addon:other"), state.conflictPreferences)
		}
	}

	@Test
	fun runtimeRefreshesInstalledAddonCatalogWithoutClobberingCoreState(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		ConfigStore(platform.configDir, platform.jsonCodec).saveCoreState(
			CoreConfigState(
				enabledModules = setOf("config.addon:demo"),
				keybinds = mapOf("config.addon:demo/greet" to 71),
				installedAddons = mapOf(
					"config.addon" to InstalledAddonState(
						addonId = "config.addon",
						version = "0.9.0",
						artifactType = "Native JAR",
						source = "old-source",
						checksum = "sha256:abc",
						signatureStatus = "trusted",
						installedAtEpochMillis = 123456789L,
						unknownFields = mapOf("catalogId" to "test"),
					),
					"stale.addon" to InstalledAddonState(
						addonId = "stale.addon",
						version = "1.0.0",
						artifactType = "Native JAR",
						source = "/addons/stale.jar",
					),
				),
			),
		)

		val runtime = DhenRuntime(platform)
		runtime.registerAddon(ConfigAddon(), AddonSource(AddonSourceType.LOCAL, "/addons/config.jar"))
		runtime.start()

		val state = ConfigStore(platform.configDir, platform.jsonCodec).loadCoreState()
		val installed = state.installedAddons.getValue("config.addon")
		assertEquals("1.0.0", installed.version)
		assertEquals("Native JAR", installed.artifactType)
		assertEquals("/addons/config.jar", installed.source)
		assertEquals("sha256:abc", installed.checksum)
		assertEquals("trusted", installed.signatureStatus)
		assertEquals(123456789L, installed.installedAtEpochMillis)
		assertEquals(mapOf("catalogId" to "test"), installed.unknownFields)
		assertEquals(setOf("config.addon"), state.installedAddons.keys)
		assertEquals(mapOf("config.addon:demo/greet" to 71), state.keybinds)
		assertEquals(LifecycleState.ENABLED, runtime.modules().single().state)
	}

	@Test
	fun settingAliasesApplyInsideAddonSettings(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val store = ConfigStore(
			platform.configDir,
			platform.jsonCodec,
			ConfigAliases(settingAliases = mapOf("config.addon" to mapOf("group.old_name" to "group.new_name"))),
		)
		val path = store.addonsDir.resolve("config.addon.json")
		Files.createDirectories(path.parent)
		Files.writeString(
			path,
			platform.jsonCodec.encode(
				mapOf(
					"schemaVersion" to 1,
					"settings" to mapOf("group" to mapOf("old_name" to "kept")),
				),
			),
		)

		val settings = store.loadAddonSettings(AddonId("config.addon"))

		@Suppress("UNCHECKED_CAST")
		val group = settings["group"] as Map<String, Any?>
		assertFalse(group.containsKey("old_name"))
		assertEquals("kept", group["new_name"])
	}

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

private fun decodedMap(platform: FakePlatformServices, path: Path): Map<String, Any?> {
	@Suppress("UNCHECKED_CAST")
	return platform.jsonCodec.decode(Files.readString(path)) as Map<String, Any?>
}
