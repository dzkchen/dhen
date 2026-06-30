package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleId

class DhenRuntime(private val platform: PlatformServices) {
	private val registry = ModuleRegistry()
	private val store = ConfigStore(platform.configDir, platform.jsonCodec)
	private val config = ConfigManager(store)
	private val log = platform.logger("dhen-runtime")
	private val lifecycle = LifecycleManager(platform, config, log)

	private var started = false

	fun registerAddon(addon: DhenAddon) {
		val metadata = addon.metadata
		registry.recordAddon(metadata)
		val context = object : AddonContext {
			override val addonId: AddonId = metadata.id
			override val logger = platform.logger("addon/${metadata.id.value}")

			override fun registerModule(module: DhenModule) {
				if (module.metadata.id.addonId != metadata.id) {
					log.warn("Module ${module.metadata.id} declared by addon ${metadata.id} has a mismatched addon prefix")
				}
				val record = ModuleRecord(module, metadata.id)
				try {
					registry.register(record)
					record.state = LifecycleState.REGISTERED
					record.lastTransition = "registered"
				} catch (t: DuplicateModuleException) {
					log.error("Rejected duplicate module ${t.moduleId} from addon ${metadata.id}")
				}
			}
		}
		try {
			addon.register(context)
		} catch (t: Throwable) {
			log.error("Addon ${metadata.id} failed during register()", t)
		}
	}

	fun collectKeybinds(): List<PlatformKeybind> = registry.all().flatMap { record ->
		record.metadataKeybinds().map { spec -> PlatformKeybind(platformKeybindId(record.id, spec.id), spec) }
	}

	fun start() {
		if (started) return
		started = true

		for (addon in registry.addons()) {
			val schemas = registry.byAddon(addon.id).flatMap { it.module.metadata.settings }
			config.materializeDefaults(addon.id, schemas)
		}

		platform.registerKeybinds(collectKeybinds())

		val enabledIds = store.loadEnabledModules()
		for (record in registry.all()) {
			if (record.id.value in enabledIds) {
				lifecycle.enable(record)
			} else {
				record.state = LifecycleState.DISABLED
				record.lastTransition = "disabled"
			}
		}
		persistEnabledState()
		log.info("Dhen runtime started: ${registry.addons().size} addon(s), ${registry.all().size} module(s)")
	}

	fun enableModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		lifecycle.enable(record)
		persistEnabledState()
		return record.state == LifecycleState.ENABLED
	}

	fun disableModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		lifecycle.disable(record)
		persistEnabledState()
		return true
	}

	fun toggleModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		return if (record.state == LifecycleState.ENABLED) {
			!disableModule(id)
		} else {
			enableModule(id)
		}
	}

	fun modules(): List<ModuleRecord> = registry.all()

	fun diagnostics(): DiagnosticsSnapshot {
		val modules = registry.all().map { record ->
			val md = record.module.metadata
			ModuleDiagnostics(
				moduleId = md.id.value,
				addonId = record.addonId.value,
				name = md.name,
				category = md.category.name,
				state = record.state,
				activeHandles = record.activeHandleCount,
				failureReason = record.failureReason,
				lastTransition = record.lastTransition,
			)
		}
		val addons = registry.addons().map { md ->
			AddonDiagnostics(md.id.value, md.name, md.version, registry.byAddon(md.id).size)
		}
		return DiagnosticsSnapshot(addons, modules)
	}

	private fun persistEnabledState() {
		val enabled = registry.all().filter { it.state == LifecycleState.ENABLED }.map { it.id.value }.toSet()
		store.saveEnabledModules(enabled)
	}

	private fun ModuleRecord.metadataKeybinds() = module.metadata.keybinds
}
