package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.ApiVersion
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.VersionRange

class DhenRuntime(
	private val platform: PlatformServices,
	aliases: ConfigAliases = ConfigAliases(),
) {
	private val registry = ModuleRegistry()
	private val store = ConfigStore(platform.configDir, platform.jsonCodec, aliases)
	private val config = ConfigManager(store)
	private val log = platform.logger("dhen-runtime")
	private val lifecycle = LifecycleManager(platform, config, log)

	private val desiredEnabled = LinkedHashSet<String>()
	private val desiredEnabledAddons = LinkedHashSet<String>()

	private var started = false

	fun registerAddon(addon: DhenAddon, source: AddonSource = AddonSource.UNKNOWN) {
		val metadata = addon.metadata
		if (started) {
			log.warn("Ignoring addon ${metadata.id} registered after startup; addons are discovered on restart")
			return
		}
		try {
			registry.recordAddon(metadata, source)
		} catch (t: DuplicateAddonException) {
			log.error("Rejected duplicate addon ${t.addonId} from source ${source.location ?: source.type.displayName}")
			return
		}
		val context = object : AddonContext {
			override val addonId: AddonId = metadata.id
			override val logger = platform.logger("addon/${metadata.id.value}")
			override val client = platform.clientContext

			override fun registerModule(module: DhenModule) {
				if (module.metadata.id.addonId != metadata.id) {
					log.warn("Module ${module.metadata.id} declared by addon ${metadata.id} has a mismatched addon prefix")
				}
				try {
					registry.register(ModuleRecord(module, metadata.id))
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

	// Physical keybinds for every module that registered successfully. The platform polls them and
	// invokes whichever handler the runtime has bound (none while the owning module is disabled).
	fun collectKeybinds(): List<PlatformKeybind> {
		val overrides = store.loadCoreState().keybinds
		return registry.all()
			.filter { it.state != LifecycleState.FAILED }
			.flatMap { record ->
				record.module.metadata.keybinds.map { spec ->
					val id = platformKeybindId(record.id, spec.id)
					PlatformKeybind(id, spec.copy(defaultKey = overrides[id] ?: spec.defaultKey))
				}
			}
	}

	fun start() {
		if (started) return
		started = true

		val previousCatalog = store.loadCoreState().installedAddons.keys
		store.ensureLayout(registry.addons().map { it.id })
		refreshInstalledAddonCatalogState()
		restoreAddonEnabledState(previousCatalog)
		resolveModules()
		registerResolvedModules()
		platform.registerKeybinds(collectKeybinds())
		restoreEnabledState()

		log.info("Dhen runtime started: ${registry.addons().size} addon(s), ${registry.all().size} module(s)")
	}

	// DISCOVERED -> RESOLVED when the owning addon's required dependencies are present and in range,
	// otherwise DISCOVERED -> FAILED so the module is excluded from the rest of startup.
	private fun resolveModules() {
		for (addon in registry.addons()) {
			val reason = unmetDependency(addon.metadata)
			for (record in registry.byAddon(addon.id)) {
				if (record.state != LifecycleState.DISCOVERED) continue
				if (reason == null) {
					record.transitionTo(LifecycleState.RESOLVED, "resolved")
				} else {
					record.transitionTo(LifecycleState.FAILED, "resolve-failed", reason)
				}
			}
		}
	}

	// RESOLVED -> REGISTERED and materialize config defaults from each module's schema.
	private fun registerResolvedModules() {
		for (record in registry.all()) {
			if (record.state == LifecycleState.RESOLVED) record.transitionTo(LifecycleState.REGISTERED, "registered")
		}
		for (addon in registry.addons()) {
			val schemas = registry.byAddon(addon.id)
				.filter { it.state == LifecycleState.REGISTERED }
				.flatMap { it.module.metadata.settings }
			if (schemas.isNotEmpty()) {
				for (error in config.materializeDefaults(addon.id, schemas)) log.warn("Invalid config value: $error")
			}
		}
	}

	private fun restoreEnabledState() {
		desiredEnabled.addAll(store.loadEnabledModules())
		for (record in registry.all()) {
			if (record.state != LifecycleState.REGISTERED) continue
			if (record.addonId.value !in desiredEnabledAddons) {
				record.transitionTo(LifecycleState.DISABLED, "addon-disabled")
			} else if (record.id.value in desiredEnabled) {
				tryEnable(record)
			} else {
				record.transitionTo(LifecycleState.DISABLED, "disabled")
			}
		}
	}

	fun enableAddon(id: AddonId): Boolean {
		if (registry.addon(id) == null) return false
		desiredEnabledAddons.add(id.value)
		persistEnabledAddonState()
		for (record in registry.byAddon(id)) {
			if (record.state == LifecycleState.DISABLED && record.id.value in desiredEnabled) tryEnable(record)
		}
		return true
	}

	fun disableAddon(id: AddonId): Boolean {
		if (registry.addon(id) == null) return false
		desiredEnabledAddons.remove(id.value)
		for (record in registry.byAddon(id)) {
			desiredEnabled.remove(record.id.value)
			if (record.state == LifecycleState.ENABLED || record.state == LifecycleState.REGISTERED) lifecycle.disable(record)
		}
		store.saveEnabledState(desiredEnabled.toSet(), desiredEnabledAddons.toSet())
		return true
	}

	fun enableModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		val ok = tryEnable(record)
		if (ok) desiredEnabled.add(id.value)
		persistEnabledState()
		return ok
	}

	fun disableModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		lifecycle.disable(record)
		desiredEnabled.remove(id.value)
		persistEnabledState()
		return record.state == LifecycleState.DISABLED
	}

	fun toggleModule(id: ModuleId): Boolean {
		val record = registry.get(id) ?: return false
		return if (record.state == LifecycleState.ENABLED) !disableModule(id) else enableModule(id)
	}

	fun enabledAddons(): Set<String> = store.loadCoreState().enabledAddons

	fun installedAddons(): Map<String, InstalledAddonState> = store.loadCoreState().installedAddons

	fun pendingRestartAddons(): Map<String, PendingRestartAddonState> = store.loadCoreState().pendingRestartAddons

	fun savePendingRestartAddons(addons: Map<String, PendingRestartAddonState>) {
		store.savePendingRestartAddons(addons)
	}

	fun hudLayout(): Map<String, HudLayoutState> = store.loadCoreState().hudLayout

	fun saveHudLayout(layout: Map<String, HudLayoutState>) {
		store.saveHudLayout(layout)
	}

	fun keybinds(): Map<String, Int> = store.loadCoreState().keybinds

	fun saveKeybinds(keybinds: Map<String, Int>) {
		store.saveKeybinds(keybinds)
	}

	fun conflictPreferences(): Map<String, String> = store.loadCoreState().conflictPreferences

	fun saveConflictPreferences(preferences: Map<String, String>) {
		store.saveConflictPreferences(preferences)
	}

	fun modules(): List<ModuleRecord> = registry.all()

	fun diagnostics(): DiagnosticsSnapshot {
		val modules = registry.all().map { record ->
			val md = record.module.metadata
			ModuleDiagnostics(
				moduleId = md.id.value,
				addonId = record.addonId.value,
				name = md.name,
				category = md.category.displayName,
				state = record.state,
				activeHandles = record.activeHandleCount,
				failureReason = record.failureReason,
				lastTransition = record.lastTransition,
			)
		}
		val addons = registry.addons().map { record ->
			val md = record.metadata
			AddonDiagnostics(
				addonId = md.id.value,
				name = md.name,
				version = md.version,
				enabled = md.id.value in desiredEnabledAddons,
				moduleCount = registry.byAddon(md.id).size,
				artifactType = md.artifactType.displayName,
				authors = md.authors,
				sourceUrl = md.sourceUrl?.toString(),
				sourceType = record.source.type.displayName,
				sourceLocation = record.source.location,
				requiredDhenApi = md.requiredDhenApi,
				minecraftVersionRange = md.minecraftVersionRange,
				fabricLoaderVersionRange = md.fabricLoaderVersionRange,
				dependencies = md.depends.map { it.id.value },
				conflicts = md.breaks.map { it.id.value },
				providedModules = md.providedModules.map { it.value },
				releaseNotes = md.releaseNotes,
			)
		}
		return DiagnosticsSnapshot(addons, modules)
	}

	// Enables a registered module after re-checking its dependencies and explicit module conflicts.
	// Returns false (leaving the module unenabled) when blocked instead of forcing it active.
	private fun tryEnable(record: ModuleRecord): Boolean {
		if (record.state == LifecycleState.ENABLED) return true
		if (record.addonId.value !in desiredEnabledAddons) {
			log.warn("Cannot enable ${record.id}: addon ${record.addonId.value} is disabled")
			if (record.state == LifecycleState.REGISTERED) record.transitionTo(LifecycleState.DISABLED, "addon-disabled")
			return false
		}
		val depReason = registry.addon(record.addonId)?.let(::unmetDependency)
		if (depReason != null) {
			log.warn("Cannot enable ${record.id}: $depReason")
			return false
		}
		val conflict = activeConflict(record)
		if (conflict != null) {
			log.warn("Cannot enable ${record.id}: conflicts with enabled module ${conflict.value}")
			if (record.state == LifecycleState.REGISTERED) record.transitionTo(LifecycleState.DISABLED, "conflict-blocked")
			return false
		}
		lifecycle.enable(record)
		return record.state == LifecycleState.ENABLED
	}

	// The first enabled module that this module declares a conflict with, or that declares one with it.
	private fun activeConflict(record: ModuleRecord): ModuleId? {
		val declared = record.module.metadata.conflicts
		return registry.all().firstOrNull { other ->
			other.id != record.id &&
				other.state == LifecycleState.ENABLED &&
				(other.id in declared || record.id in other.module.metadata.conflicts)
		}?.id
	}

	// The first required addon dependency that is absent or whose version is out of range, else null.
	private fun unmetDependency(addon: AddonMetadata): String? {
		for (dep in addon.depends) {
			val present = registry.addon(dep.id) ?: return "missing required addon '${dep.id.value}'"
			val range = dep.parsedVersionRange
			if (range == VersionRange.ANY) continue
			val version = ApiVersion.parseOrNull(present.version)
			if (version == null) {
				log.warn("Cannot version-check addon '${dep.id.value}' v${present.version} against ${dep.versionRange}; accepting")
			} else if (!range.contains(version)) {
				return "addon '${dep.id.value}' v${present.version} does not satisfy ${dep.versionRange}"
			}
		}
		return null
	}

	private fun persistEnabledState() {
		store.saveEnabledModules(desiredEnabled.toSet())
	}

	private fun persistEnabledAddonState() {
		store.saveEnabledAddons(desiredEnabledAddons.toSet())
	}

	private fun restoreAddonEnabledState(previousCatalog: Set<String>) {
		val currentIds = registry.addons().map { it.id.value }.toSet()
		val current = store.loadCoreState()
		desiredEnabledAddons.clear()
		desiredEnabledAddons.addAll(current.enabledAddons.filter { it in currentIds })
		desiredEnabledAddons.addAll(current.enabledModules.map { it.substringBefore(':') }.filter { it in currentIds })
		for (id in currentIds) {
			if (id !in previousCatalog) desiredEnabledAddons.add(id)
		}
		persistEnabledAddonState()
	}

	private fun refreshInstalledAddonCatalogState() {
		store.updateCoreState { current ->
			val installed = LinkedHashMap<String, InstalledAddonState>()
			for (record in registry.addons()) {
				val existing = current.installedAddons[record.id.value]
				val metadata = record.metadata
				installed[record.id.value] = InstalledAddonState(
					addonId = metadata.id.value,
					version = metadata.version,
					artifactType = metadata.artifactType.displayName,
					source = record.source.location ?: record.source.type.displayName,
					checksum = existing?.checksum.orEmpty(),
					signatureStatus = existing?.signatureStatus.orEmpty(),
					installedAtEpochMillis = existing?.installedAtEpochMillis,
					unknownFields = existing?.unknownFields.orEmpty(),
				)
			}
			current.copy(installedAddons = installed.toSortedMap())
		}
	}
}
