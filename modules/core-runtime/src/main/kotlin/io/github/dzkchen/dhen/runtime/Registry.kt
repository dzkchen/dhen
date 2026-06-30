package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.RegistrationHandle

enum class LifecycleState {
	DISCOVERED,
	RESOLVED,
	REGISTERED,
	ENABLED,
	DISABLED,
	FAILED,
}

// Per-module runtime record: the module, its owning addon, lifecycle state, failure reason,
// and the live registration handles created while enabled (disposed in reverse order on disable).
class ModuleRecord(
	val module: DhenModule,
	val addonId: AddonId,
) {
	val id: ModuleId get() = module.metadata.id
	val handles: ArrayDeque<RegistrationHandle> = ArrayDeque()

	var state: LifecycleState = LifecycleState.DISCOVERED
	var failureReason: String? = null
	var lastTransition: String = "discovered"

	val activeHandleCount: Int get() = handles.size
}

class DuplicateModuleException(val moduleId: ModuleId) :
	IllegalStateException("Module already registered: $moduleId")

// Stores modules by stable ID and rejects duplicates. Also tracks discovered addons.
class ModuleRegistry {
	private val modulesById = LinkedHashMap<ModuleId, ModuleRecord>()
	private val addonsById = LinkedHashMap<AddonId, AddonMetadata>()

	fun recordAddon(metadata: AddonMetadata) {
		addonsById[metadata.id] = metadata
	}

	fun register(record: ModuleRecord): ModuleRecord {
		if (modulesById.containsKey(record.id)) throw DuplicateModuleException(record.id)
		modulesById[record.id] = record
		return record
	}

	fun get(id: ModuleId): ModuleRecord? = modulesById[id]

	fun all(): List<ModuleRecord> = modulesById.values.toList()

	fun byAddon(addonId: AddonId): List<ModuleRecord> = modulesById.values.filter { it.addonId == addonId }

	fun addons(): List<AddonMetadata> = addonsById.values.toList()
}
