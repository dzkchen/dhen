package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private val MODULE_ID = ModuleId("sample.addon:demo")
private val SHOW_HUD_SETTING_ID = SettingId("show_hud")
private val GREET_KEYBIND_ID = KeybindId("greet")

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
}
