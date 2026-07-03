package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonDependency
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.KeybindId
import io.github.dzkchen.dhen.api.KeybindSpec
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.ModuleMetadata
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.WidgetId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private val MODULE_ID = ModuleId("sample.addon:demo")
private val SHOW_HUD_SETTING_ID = SettingId("show_hud")
private val GREET_KEYBIND_ID = KeybindId("greet")

private fun categoryModule(id: String, category: ModuleCategory, conflicts: List<String> = emptyList()): DhenModule =
	object : DhenModule {
		override val metadata = ModuleMetadata(
			id = ModuleId(id),
			name = id.substringAfter(':'),
			category = category,
			conflicts = conflicts.map(::ModuleId),
		)
		override fun onEnable(context: ModuleEnableContext) {}
	}

private fun conflictPairAddon(): DhenAddon = object : DhenAddon {
	override val metadata = AddonMetadata(AddonId("conflict.addon"), "Conflict", "1.0.0")

	override fun register(context: AddonContext) {
		context.registerModule(categoryModule("conflict.addon:a", ModuleCategory.CHAT, conflicts = listOf("conflict.addon:b")))
		context.registerModule(categoryModule("conflict.addon:b", ModuleCategory.CHAT))
	}
}

private fun brokenDependencyAddon(): DhenAddon = object : DhenAddon {
	override val metadata = AddonMetadata(
		AddonId("dep.addon"),
		"Dep",
		"1.0.0",
		depends = listOf(AddonDependency(AddonId("absent.addon"))),
	)

	override fun register(context: AddonContext) {
		context.registerModule(categoryModule("dep.addon:mod", ModuleCategory.MINING))
	}
}

private class SampleAddon : DhenAddon {
	override val metadata = AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0")

	override fun register(context: AddonContext) {
		context.registerModule(DemoModule())
	}
}

private class DemoModule : DhenModule {
	override val metadata = ModuleMetadata(
		id = MODULE_ID,
		name = "Demo",
		category = ModuleCategory.HUD_OVERLAYS,
		settings = listOf(BooleanSetting(SHOW_HUD_SETTING_ID, "Show HUD", default = true)),
		keybinds = listOf(KeybindSpec(GREET_KEYBIND_ID, "Greet", defaultKey = 71)),
	)

	override fun onEnable(context: ModuleEnableContext) {
		context.addHudText("hud") { if (context.booleanSetting(SHOW_HUD_SETTING_ID)) "hi" else null }
		context.onKeybind(GREET_KEYBIND_ID) { context.sendChat("hi") }
	}
}

class DhenRuntimeTest {
	@Test
	fun startLeavesModulesRegisteredAndDisabledByDefault(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.start()

		val record = runtime.modules().single()
		assertEquals(LifecycleState.DISABLED, record.state)
		assertEquals(0, record.activeHandleCount)
	}

	@Test
	fun enableRegistersHandlesAndDisableDisposesThem(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(SampleAddon())
		runtime.start()

		assertTrue(runtime.enableModule(MODULE_ID))
		val record = runtime.modules().single()
		assertEquals(LifecycleState.ENABLED, record.state)
		assertEquals(2, record.activeHandleCount)
		assertEquals(1, platform.keybindHandlers.size)
		assertEquals(1, platform.hudWidgets.size)

		runtime.disableModule(MODULE_ID)
		assertEquals(LifecycleState.DISABLED, record.state)
		assertEquals(0, record.activeHandleCount)
		assertTrue(platform.keybindHandlers.isEmpty())
		assertTrue(platform.hudWidgets.isEmpty())
	}

	@Test
	fun repeatedEnableDisableDoesNotLeakHandles(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(SampleAddon())
		runtime.start()

		repeat(10) {
			runtime.enableModule(MODULE_ID)
			runtime.disableModule(MODULE_ID)
		}

		assertEquals(0, runtime.modules().single().activeHandleCount)
		assertTrue(platform.keybindHandlers.isEmpty())
		assertTrue(platform.hudWidgets.isEmpty())
	}

	@Test
	fun enabledStateSurvivesRestart(@TempDir tmp: Path) {
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(SampleAddon())
			start()
			enableModule(MODULE_ID)
		}

		val restarted = DhenRuntime(FakePlatformServices(tmp))
		restarted.registerAddon(SampleAddon())
		restarted.start()

		assertEquals(LifecycleState.ENABLED, restarted.modules().single().state)
	}

	@Test
	fun disabledAddonBlocksModuleRestoreUntilAddonIsReenabled(@TempDir tmp: Path) {
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(SampleAddon())
			start()
			assertTrue(enableModule(MODULE_ID))
			assertTrue(disableAddon(AddonId("sample.addon")))
		}

		val restarted = DhenRuntime(FakePlatformServices(tmp))
		restarted.registerAddon(SampleAddon())
		restarted.start()

		assertEquals(emptySet<String>(), restarted.enabledAddons())
		assertEquals(LifecycleState.DISABLED, restarted.modules().single().state)
		assertFalse(restarted.enableModule(MODULE_ID))

		assertTrue(restarted.enableAddon(AddonId("sample.addon")))
		assertEquals(LifecycleState.DISABLED, restarted.modules().single().state)
		assertTrue(restarted.enableModule(MODULE_ID))
		assertEquals(LifecycleState.ENABLED, restarted.modules().single().state)
	}

	@Test
	fun runtimeAliasesEnabledModulesAndKeybindOverridesOnStartup(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		ConfigStore(platform.configDir, platform.jsonCodec).saveCoreState(
			CoreConfigState(
				enabledModules = setOf("old.addon:demo"),
				keybinds = mapOf("old.addon:demo/greet" to 65),
			),
		)

		val runtime = DhenRuntime(
			platform,
			ConfigAliases(moduleAliases = mapOf("old.addon:demo" to MODULE_ID.value)),
		)
		runtime.registerAddon(SampleAddon())
		runtime.start()

		assertEquals(LifecycleState.ENABLED, runtime.modules().single().state)
		assertEquals(65, platform.registeredKeybinds.single { it.id == "${MODULE_ID.value}/greet" }.spec.defaultKey)
		assertEquals(mapOf("${MODULE_ID.value}/greet" to 65), runtime.keybinds())
	}

	@Test
	fun diagnosticsReportStateAndHandles(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.start()
		runtime.enableModule(MODULE_ID)

		val snapshot = runtime.diagnostics()
		assertEquals(1, snapshot.addons.size)
		assertEquals(1, snapshot.modules.size)
		assertEquals(2, snapshot.totalActiveHandles)
		assertTrue(snapshot.failedModules.isEmpty())
		assertEquals(LifecycleState.ENABLED, snapshot.modules.single().state)
	}

	@Test
	fun modulesByCategoryGroupsNonEmptyCategoriesInEnumOrder(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.registerAddon(object : DhenAddon {
			override val metadata = AddonMetadata(AddonId("other.addon"), "Other", "1.0.0")
			override fun register(context: AddonContext) {
				context.registerModule(categoryModule("other.addon:chat", ModuleCategory.CHAT))
				context.registerModule(categoryModule("other.addon:dungeon", ModuleCategory.DUNGEONS))
			}
		})
		runtime.start()

		val byCategory = runtime.modulesByCategory()
		assertEquals(
			listOf(ModuleCategory.DUNGEONS, ModuleCategory.HUD_OVERLAYS, ModuleCategory.CHAT),
			byCategory.keys.toList(),
		)
		assertEquals(MODULE_ID, byCategory.getValue(ModuleCategory.HUD_OVERLAYS).single().id)
	}

	@Test
	fun duplicateModuleIsRejected(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(object : DhenAddon {
			override val metadata = AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0")
			override fun register(context: AddonContext) {
				context.registerModule(DemoModule())
				context.registerModule(DemoModule())
			}
		})
		runtime.start()

		assertEquals(1, runtime.modules().size)
	}

	@Test
	fun addonIdsExposeDiscoveredAddonsForCommandSuggestions(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.start()

		assertEquals(listOf("sample.addon"), runtime.addonIds())
	}

	@Test
	fun markAddonPendingRestartPersistsRestartRequiredIntent(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.start()

		runtime.markAddonPendingRestart(AddonId("sample.addon"), "add")

		val pending = DhenRuntime(FakePlatformServices(tmp)).pendingRestartAddons()
		assertEquals("add", pending.getValue("sample.addon").operation)
	}

	@Test
	fun keybindHandlerOnlyFiresWhileEnabled(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(SampleAddon())
		runtime.start()

		val keybindId = platformKeybindId(MODULE_ID, "greet")
		assertFalse(platform.keybindHandlers.containsKey(keybindId))

		runtime.enableModule(MODULE_ID)
		platform.keybindHandlers.getValue(keybindId).invoke()
		assertEquals(listOf("hi"), platform.chatMessages)

		runtime.disableModule(MODULE_ID)
		assertFalse(platform.keybindHandlers.containsKey(keybindId))
	}

	@Test
	fun openGuiKeybindRegistersWithDefaultAndSavedOverride(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.start()

		val registered = platform.registeredKeybinds.single { it.id == DhenRuntime.OPEN_GUI_KEYBIND_ID }
		assertEquals(344, registered.spec.defaultKey)

		runtime.saveKeybinds(mapOf(DhenRuntime.OPEN_GUI_KEYBIND_ID to 342))

		val restartedPlatform = FakePlatformServices(tmp)
		DhenRuntime(restartedPlatform).start()
		val rebound = restartedPlatform.registeredKeybinds.single { it.id == DhenRuntime.OPEN_GUI_KEYBIND_ID }
		assertEquals(342, rebound.spec.defaultKey)
	}

	@Test
	fun updateSettingAppliesLiveToEnabledModuleAndSurvivesRestart(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(SampleAddon())
		runtime.start()
		runtime.enableModule(MODULE_ID)

		val addonId = AddonId("sample.addon")
		val widgetId = platformWidgetId(MODULE_ID, WidgetId("hud"))
		assertEquals("hi", platform.hudWidgets.getValue(widgetId).invoke())

		assertTrue(runtime.updateSetting(addonId, SHOW_HUD_SETTING_ID, false).isEmpty())
		assertNull(platform.hudWidgets.getValue(widgetId).invoke())
		assertEquals(false, runtime.settingValue(addonId, SHOW_HUD_SETTING_ID))

		assertEquals(
			listOf("sample.addon.show_hud: expected boolean"),
			runtime.updateSetting(addonId, SHOW_HUD_SETTING_ID, "nope"),
		)
		assertEquals(false, runtime.settingValue(addonId, SHOW_HUD_SETTING_ID))

		val restarted = DhenRuntime(FakePlatformServices(tmp))
		restarted.registerAddon(SampleAddon())
		restarted.start()
		assertEquals(false, restarted.settingValue(addonId, SHOW_HUD_SETTING_ID))
	}

	@Test
	fun moduleIssuesReportActiveConflictAndMissingDependency(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(conflictPairAddon())
		runtime.registerAddon(brokenDependencyAddon())
		runtime.start()
		assertTrue(runtime.enableModule(ModuleId("conflict.addon:a")))

		val conflicted = runtime.moduleIssues(ModuleId("conflict.addon:b"))
		assertEquals(ModuleId("conflict.addon:a"), conflicted.conflictWith)
		assertNull(conflicted.missingDependency)
		assertFalse(runtime.enableModule(ModuleId("conflict.addon:b")))

		val broken = runtime.moduleIssues(ModuleId("dep.addon:mod"))
		assertNull(broken.conflictWith)
		assertEquals("missing required addon 'absent.addon'", broken.missingDependency)
	}

	@Test
	fun searchModulesMatchesModuleAndAddonFields(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.registerAddon(object : DhenAddon {
			override val metadata = AddonMetadata(AddonId("other.addon"), "Other", "1.0.0")
			override fun register(context: AddonContext) {
				context.registerModule(categoryModule("other.addon:chat", ModuleCategory.CHAT))
			}
		})
		runtime.start()

		fun ids(query: String) = runtime.searchModules(query).map { it.id.value }

		assertEquals(listOf(MODULE_ID.value), ids("Demo")) // module name
		assertEquals(listOf(MODULE_ID.value), ids("addon:demo")) // stable id
		assertEquals(listOf(MODULE_ID.value), ids("Overlays")) // category
		assertEquals(listOf(MODULE_ID.value), ids("Sample")) // addon name
		assertEquals(listOf("other.addon:chat"), ids("other.addon")) // addon id
		assertEquals(listOf(MODULE_ID.value, "other.addon:chat"), ids(""))
		assertEquals(emptyList<String>(), ids("zzz"))
	}

	@Test
	fun searchFiltersReflectLiveRuntimeState(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(SampleAddon())
		runtime.registerAddon(conflictPairAddon())
		runtime.registerAddon(brokenDependencyAddon())
		runtime.start()

		fun ids(vararg filters: ModuleFilter) = runtime.searchModules(filters = filters.toSet()).map { it.id.value }

		assertEquals(emptyList<String>(), ids(ModuleFilter.ENABLED))
		runtime.enableModule(MODULE_ID)
		runtime.enableModule(ModuleId("conflict.addon:a"))
		assertEquals(listOf(MODULE_ID.value, "conflict.addon:a"), ids(ModuleFilter.ENABLED))
		assertEquals(listOf("conflict.addon:b"), ids(ModuleFilter.DISABLED, ModuleFilter.HAS_CONFLICT))
		assertEquals(listOf("dep.addon:mod"), ids(ModuleFilter.FAILED))
		assertEquals(listOf("dep.addon:mod"), ids(ModuleFilter.MISSING_DEPENDENCY))
		assertEquals(emptyList<String>(), ids(ModuleFilter.AVAILABLE_ADDON))

		val installed = ids(ModuleFilter.INSTALLED_ADDON)
		assertTrue(MODULE_ID.value in installed && "conflict.addon:a" in installed)

		assertEquals(emptyList<String>(), ids(ModuleFilter.PENDING_RESTART))
		runtime.markAddonPendingRestart(AddonId("sample.addon"), "remove")
		assertEquals(listOf(MODULE_ID.value), ids(ModuleFilter.PENDING_RESTART))

		runtime.disableModule(MODULE_ID)
		assertEquals(listOf("conflict.addon:a"), ids(ModuleFilter.ENABLED))
	}

	@Test
	fun hudWidgetRendersWhileEnabledAndDisappearsWhenDisabled(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		runtime.registerAddon(SampleAddon())
		runtime.start()

		val widgetId = platformWidgetId(MODULE_ID, WidgetId("hud"))
		assertFalse(platform.hudWidgets.containsKey(widgetId))

		runtime.enableModule(MODULE_ID)
		assertEquals("hi", platform.hudWidgets.getValue(widgetId).invoke())

		runtime.disableModule(MODULE_ID)
		assertFalse(platform.hudWidgets.containsKey(widgetId))
	}
}
