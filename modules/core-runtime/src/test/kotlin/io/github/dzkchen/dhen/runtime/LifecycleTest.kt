package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonDependency
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleDisableContext
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.ModuleMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

// Configurable test module. onEnable/onDisable run the supplied blocks so a test can register
// handles, throw, observe disable, or do nothing.
private class TestModule(
	id: ModuleId,
	conflicts: List<ModuleId> = emptyList(),
	private val disableBlock: (ModuleDisableContext) -> Unit = {},
	private val enableBlock: (ModuleEnableContext) -> Unit = {},
) : DhenModule {
	override val metadata = ModuleMetadata(
		id = id,
		name = id.value,
		category = ModuleCategory.HUD_OVERLAYS,
		conflicts = conflicts,
	)

	override fun onEnable(context: ModuleEnableContext) = enableBlock(context)

	override fun onDisable(context: ModuleDisableContext) = disableBlock(context)
}

private class TestAddon(
	override val metadata: AddonMetadata,
	private val modules: List<DhenModule>,
) : DhenAddon {
	override fun register(context: AddonContext) = modules.forEach(context::registerModule)
}

private fun addon(id: String, depends: List<AddonDependency> = emptyList()) =
	AddonMetadata(id = AddonId(id), name = id, version = "1.0.0", depends = depends)

class LifecycleTest {
	// The state machine accepts the linear lifecycle and rejects skips/back-steps.
	@Test
	fun stateMachineRejectsInvalidTransitions() {
		val fresh = { ModuleRecord(TestModule(ModuleId("t.addon:m")), AddonId("t.addon")) }

		val record = fresh()
		record.transitionTo(LifecycleState.RESOLVED, "resolved")
		record.transitionTo(LifecycleState.REGISTERED, "registered")
		record.transitionTo(LifecycleState.ENABLED, "enabled")
		record.transitionTo(LifecycleState.DISABLED, "disabled")
		record.transitionTo(LifecycleState.ENABLED, "enabled")
		assertEquals(LifecycleState.ENABLED, record.state)

		assertThrows(IllegalLifecycleTransitionException::class.java) {
			fresh().transitionTo(LifecycleState.ENABLED, "skip")
		}
		assertThrows(IllegalLifecycleTransitionException::class.java) {
			fresh().transitionTo(LifecycleState.REGISTERED, "skip")
		}
		assertThrows(IllegalLifecycleTransitionException::class.java) {
			record.transitionTo(LifecycleState.REGISTERED, "back")
		}
		// FAILED is reachable from anywhere.
		fresh().transitionTo(LifecycleState.FAILED, "boom", "reason")
	}

	@Test
	fun transitionToFailedRecordsReasonAndIsClearedOnRecovery() {
		val record = ModuleRecord(TestModule(ModuleId("t.addon:m")), AddonId("t.addon"))
		record.transitionTo(LifecycleState.FAILED, "enable-failed", "kaboom")
		assertEquals("kaboom", record.failureReason)
		record.transitionTo(LifecycleState.DISABLED, "disabled")
		assertNull(record.failureReason)
	}

	// A second addon with the same id is rejected without registering its modules.
	@Test
	fun duplicateAddonIsRejected(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(TestAddon(addon("dup.addon"), listOf(TestModule(ModuleId("dup.addon:a")))))
		runtime.registerAddon(TestAddon(addon("dup.addon"), listOf(TestModule(ModuleId("dup.addon:b")))))
		runtime.start()

		assertEquals(1, runtime.diagnostics().addons.size)
		assertEquals(listOf("dup.addon:a"), runtime.modules().map { it.id.value })
	}

	// A module whose addon requires a missing addon resolves to FAILED and cannot enable.
	@Test
	fun missingDependencyBlocksAndFails(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"))))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(ModuleId("dep.consumer:m")))))
		runtime.start()

		val record = runtime.modules().single()
		assertEquals(LifecycleState.FAILED, record.state)
		assertNotNull(record.failureReason)
		assertFalse(runtime.enableModule(ModuleId("dep.consumer:m")))
		assertEquals(LifecycleState.FAILED, record.state)
	}

	@Test
	fun outOfRangeDependencyVersionFails(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(TestAddon(addon("dep.provider"), listOf(TestModule(ModuleId("dep.provider:m")))))
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"), "[2.0,3.0)")))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(ModuleId("dep.consumer:m")))))
		runtime.start()

		assertEquals(LifecycleState.DISABLED, runtime.modules().first { it.id.value == "dep.provider:m" }.state)
		assertEquals(LifecycleState.FAILED, runtime.modules().first { it.id.value == "dep.consumer:m" }.state)
	}

	// Enabling a module is refused while a module it conflicts with is enabled (both ways).
	@Test
	fun conflictBlocksEnableBidirectionally(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		val a = ModuleId("conf.addon:a")
		val b = ModuleId("conf.addon:b")
		runtime.registerAddon(TestAddon(addon("conf.addon"), listOf(TestModule(a), TestModule(b, conflicts = listOf(a)))))
		runtime.start()

		assertTrue(runtime.enableModule(a))
		assertFalse(runtime.enableModule(b))
		assertEquals(LifecycleState.DISABLED, runtime.modules().first { it.id == b }.state)

		runtime.disableModule(a)
		assertTrue(runtime.enableModule(b))
		// b only declared the conflict, but enabling a is still refused because b is active.
		assertFalse(runtime.enableModule(a))
	}

	// A throwing onEnable rolls back the handles it created, in reverse order.
	@Test
	fun failedEnableRollsBackHandlesInReverseOrder(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		val id = ModuleId("roll.addon:m")
		runtime.registerAddon(
			TestAddon(
				addon("roll.addon"),
				listOf(
					TestModule(id) { ctx ->
						ctx.addHudText("first") { "1" }
						ctx.addHudText("second") { "2" }
						throw IllegalStateException("boom")
					},
				),
			),
		)
		runtime.start()

		assertFalse(runtime.enableModule(id))
		val record = runtime.modules().single()
		assertEquals(LifecycleState.FAILED, record.state)
		assertEquals("boom", record.failureReason)
		assertEquals(0, record.activeHandleCount)
		assertTrue(platform.hudWidgets.isEmpty())
		assertEquals(listOf("second", "first"), platform.disposedHandleIds.map { it.substringAfterLast('/') })
	}

	// One module failing to enable leaves unrelated modules running.
	@Test
	fun failingModuleDoesNotBreakOthers(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val runtime = DhenRuntime(platform)
		val good = ModuleId("iso.addon:good")
		val bad = ModuleId("iso.addon:bad")
		runtime.registerAddon(
			TestAddon(
				addon("iso.addon"),
				listOf(
					TestModule(good) { ctx -> ctx.addHudText("ok") { "ok" } },
					TestModule(bad) { throw IllegalStateException("nope") },
				),
			),
		)
		runtime.start()

		assertTrue(runtime.enableModule(good))
		assertFalse(runtime.enableModule(bad))

		assertEquals(LifecycleState.ENABLED, runtime.modules().first { it.id == good }.state)
		assertEquals(LifecycleState.FAILED, runtime.modules().first { it.id == bad }.state)
		assertEquals(1, runtime.modules().first { it.id == good }.activeHandleCount)
		assertEquals(1, platform.hudWidgets.size)
	}

	// Diagnostics expose addon source plus per-module state, failure, handles, and last transition.
	@Test
	fun diagnosticsExposeSourceAndLifecycleFields(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(
			TestAddon(addon("ok.addon"), listOf(TestModule(ModuleId("ok.addon:m")))),
			AddonSource(AddonSourceType.LOADED_JAR, "ok-addon.jar"),
		)
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"))))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(ModuleId("dep.consumer:m")))))
		runtime.start()
		runtime.enableModule(ModuleId("ok.addon:m"))

		val snapshot = runtime.diagnostics()
		val okAddon = snapshot.addons.first { it.addonId == "ok.addon" }
		assertEquals("Loaded JAR", okAddon.sourceType)
		assertEquals("ok-addon.jar", okAddon.sourceLocation)

		val unknownAddon = snapshot.addons.first { it.addonId == "dep.consumer" }
		assertEquals("Unknown", unknownAddon.sourceType)

		val enabled = snapshot.modules.first { it.moduleId == "ok.addon:m" }
		assertEquals(LifecycleState.ENABLED, enabled.state)
		assertEquals("enabled", enabled.lastTransition)
		assertEquals(0, enabled.activeHandles)
		assertNull(enabled.failureReason)

		val failed = snapshot.modules.first { it.moduleId == "dep.consumer:m" }
		assertEquals(LifecycleState.FAILED, failed.state)
		assertEquals("resolve-failed", failed.lastTransition)
		assertNotNull(failed.failureReason)
		assertEquals(1, snapshot.failedModules.size)
	}

	// The registry indexes modules by category.
	@Test
	fun registryIndexesByCategory() {
		val registry = ModuleRegistry()
		registry.recordAddon(addon("cat.addon"), AddonSource.UNKNOWN)
		val record = ModuleRecord(TestModule(ModuleId("cat.addon:m")), AddonId("cat.addon"))
		registry.register(record)

		assertEquals(listOf(record), registry.byCategory(ModuleCategory.HUD_OVERLAYS))
		assertTrue(registry.byCategory(ModuleCategory.MINING).isEmpty())
	}

	// An enable preference survives a launch where the dependency is missing: the module resolve-fails
	// without being dropped from persisted intent, then re-enables once the provider returns.
	@Test
	fun enableIntentSurvivesTransientMissingDependency(@TempDir tmp: Path) {
		val consumerId = ModuleId("dep.consumer:m")
		val consumer = { addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider")))) }
		val provider = { addon("dep.provider") }

		// Run 1: provider present, enable the consumer module.
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(TestAddon(provider(), listOf(TestModule(ModuleId("dep.provider:m")))))
			registerAddon(TestAddon(consumer(), listOf(TestModule(consumerId))))
			start()
			assertTrue(enableModule(consumerId))
		}

		// Run 2: provider removed -> consumer resolve-fails but its intent must not be erased.
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(TestAddon(consumer(), listOf(TestModule(consumerId))))
			start()
			assertEquals(LifecycleState.FAILED, modules().single().state)
		}

		// Run 3: provider back -> consumer auto-enables from the preserved intent.
		val restored = DhenRuntime(FakePlatformServices(tmp))
		restored.registerAddon(TestAddon(provider(), listOf(TestModule(ModuleId("dep.provider:m")))))
		restored.registerAddon(TestAddon(consumer(), listOf(TestModule(consumerId))))
		restored.start()
		assertEquals(LifecycleState.ENABLED, restored.modules().first { it.id == consumerId }.state)
	}

	// Disabling a module that never enabled (resolve-failed) must not call onDisable.
	@Test
	fun disablingFailedModuleDoesNotCallOnDisable(@TempDir tmp: Path) {
		var onDisableCalled = false
		val id = ModuleId("dep.consumer:m")
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"))))
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(id, disableBlock = { onDisableCalled = true }))))
		runtime.start()

		assertEquals(LifecycleState.FAILED, runtime.modules().single().state)
		runtime.disableModule(id)
		assertFalse(onDisableCalled)
		assertEquals(LifecycleState.DISABLED, runtime.modules().single().state)
	}

	// A module that gets conflict-blocked at restore keeps its enable intent and reclaims ENABLED once
	// the conflicting module is gone.
	@Test
	fun conflictBlockedIntentSurvivesRestart(@TempDir tmp: Path) {
		val a = ModuleId("ca:a")
		val b = ModuleId("cb:b")

		// Run 1: no conflict declared yet; enable both.
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(TestAddon(addon("ca"), listOf(TestModule(a))))
			registerAddon(TestAddon(addon("cb"), listOf(TestModule(b))))
			start()
			assertTrue(enableModule(a))
			assertTrue(enableModule(b))
		}

		// Run 2: cb now conflicts ca, so b is conflict-blocked behind a (registered first) but keeps intent.
		DhenRuntime(FakePlatformServices(tmp)).apply {
			registerAddon(TestAddon(addon("ca"), listOf(TestModule(a))))
			registerAddon(TestAddon(addon("cb"), listOf(TestModule(b, conflicts = listOf(a)))))
			start()
			assertEquals(LifecycleState.ENABLED, modules().first { it.id == a }.state)
			assertEquals(LifecycleState.DISABLED, modules().first { it.id == b }.state)
		}

		// Run 3: ca uninstalled -> b reclaims ENABLED from preserved intent.
		val restored = DhenRuntime(FakePlatformServices(tmp))
		restored.registerAddon(TestAddon(addon("cb"), listOf(TestModule(b, conflicts = listOf(a)))))
		restored.start()
		assertEquals(LifecycleState.ENABLED, restored.modules().single().state)
	}

	// Disabling a pre-REGISTERED module (here RESOLVED) is a safe no-op rather than an illegal transition.
	@Test
	fun disableOnResolvedRecordIsNoOp(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val lifecycle = LifecycleManager(platform, ConfigManager(ConfigStore(platform.configDir, platform.jsonCodec)), EventBus(platform.logger("events")), platform.logger("test"))
		val record = ModuleRecord(TestModule(ModuleId("r.addon:m")), AddonId("r.addon"))
		record.transitionTo(LifecycleState.RESOLVED, "resolved")

		lifecycle.disable(record)
		assertEquals(LifecycleState.RESOLVED, record.state)
	}

	// Enabling a pre-REGISTERED module (here DISCOVERED) is a no-op: onEnable must not run.
	@Test
	fun enableOnDiscoveredRecordIsNoOp(@TempDir tmp: Path) {
		val platform = FakePlatformServices(tmp)
		val lifecycle = LifecycleManager(platform, ConfigManager(ConfigStore(platform.configDir, platform.jsonCodec)), EventBus(platform.logger("events")), platform.logger("test"))
		var enableCalled = false
		val record = ModuleRecord(TestModule(ModuleId("d.addon:m"), enableBlock = { enableCalled = true }), AddonId("d.addon"))

		lifecycle.enable(record)
		assertFalse(enableCalled)
		assertEquals(LifecycleState.DISCOVERED, record.state)
	}

	// A dependency provider whose version carries a semver suffix is still range-checked on its numeric
	// core, so an out-of-range core fails the dependency instead of slipping through unchecked.
	@Test
	fun suffixedProviderVersionIsRangeChecked(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(
			TestAddon(
				AddonMetadata(id = AddonId("dep.provider"), name = "provider", version = "1.0.0-beta"),
				listOf(TestModule(ModuleId("dep.provider:m"))),
			),
		)
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"), "[2.0,3.0)")))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(ModuleId("dep.consumer:m")))))
		runtime.start()

		assertEquals(LifecycleState.FAILED, runtime.modules().first { it.id.value == "dep.consumer:m" }.state)
	}

	// A provider version with no numeric prefix can't be compared, so the check fails open (accepts) and
	// the consumer still resolves.
	@Test
	fun unparseableProviderVersionIsAccepted(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(
			TestAddon(
				AddonMetadata(id = AddonId("dep.provider"), name = "provider", version = "snapshot"),
				listOf(TestModule(ModuleId("dep.provider:m"))),
			),
		)
		val consumer = addon("dep.consumer", depends = listOf(AddonDependency(AddonId("dep.provider"), "[2.0,3.0)")))
		runtime.registerAddon(TestAddon(consumer, listOf(TestModule(ModuleId("dep.consumer:m")))))
		runtime.start()

		assertEquals(LifecycleState.DISABLED, runtime.modules().first { it.id.value == "dep.consumer:m" }.state)
	}

	// A module that failed to enable can recover to ENABLED on a later enable attempt.
	@Test
	fun failedModuleRecoversOnReEnable(@TempDir tmp: Path) {
		var attempts = 0
		val id = ModuleId("rec.addon:m")
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(
			TestAddon(
				addon("rec.addon"),
				listOf(TestModule(id, enableBlock = { if (attempts++ == 0) throw IllegalStateException("first attempt fails") })),
			),
		)
		runtime.start()

		assertFalse(runtime.enableModule(id))
		assertEquals(LifecycleState.FAILED, runtime.modules().single().state)
		assertTrue(runtime.enableModule(id))
		assertEquals(LifecycleState.ENABLED, runtime.modules().single().state)
	}

	// Addons registered after start() are ignored (discovery happens at startup); no half-registered,
	// stuck-in-DISCOVERED modules are left behind.
	@Test
	fun registerAddonAfterStartIsIgnored(@TempDir tmp: Path) {
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.start()
		runtime.registerAddon(TestAddon(addon("late.addon"), listOf(TestModule(ModuleId("late.addon:m")))))

		assertTrue(runtime.modules().isEmpty())
		assertTrue(runtime.diagnostics().addons.isEmpty())
	}
}
