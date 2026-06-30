package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.RegistrationHandle

enum class LifecycleState {
	DISCOVERED,
	RESOLVED,
	REGISTERED,
	ENABLED,
	DISABLED,
	FAILED;

	// A module may fail from any stage (metadata, resolution, registration, enable, disable, cleanup),
	// so FAILED is always reachable. Every other transition follows the linear lifecycle.
	fun canTransitionTo(target: LifecycleState): Boolean = target == FAILED || target in next

	private val next: Set<LifecycleState>
		get() = when (this) {
			DISCOVERED -> setOf(RESOLVED)
			RESOLVED -> setOf(REGISTERED)
			REGISTERED -> setOf(ENABLED, DISABLED)
			ENABLED -> setOf(DISABLED)
			DISABLED -> setOf(ENABLED)
			FAILED -> setOf(RESOLVED, ENABLED, DISABLED)
		}
}

class IllegalLifecycleTransitionException(val from: LifecycleState, val to: LifecycleState) :
	IllegalStateException("Illegal lifecycle transition: $from -> $to")

// How a discovered addon reached the runtime. Diagnostics surface this so users can tell a
// Fabric-loaded JAR from a locally placed artifact and see where it came from.
enum class AddonSourceType(val displayName: String) {
	LOADED_JAR("Loaded JAR"),
	LOCAL("Local path"),
	CLASSPATH("Classpath"),
	UNKNOWN("Unknown"),
}

data class AddonSource(val type: AddonSourceType, val location: String? = null) {
	companion object {
		val UNKNOWN = AddonSource(AddonSourceType.UNKNOWN)
	}
}

class AddonRecord(val metadata: AddonMetadata, val source: AddonSource) {
	val id: AddonId get() = metadata.id
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
		private set
	var failureReason: String? = null
		private set
	var lastTransition: String = "discovered"
		private set

	val activeHandleCount: Int get() = handles.size

	// The single mutation point for lifecycle state. Rejects transitions the state machine forbids
	// so a runtime bug surfaces as an exception instead of a silently corrupt state.
	fun transitionTo(target: LifecycleState, label: String, reason: String? = null) {
		if (!state.canTransitionTo(target)) throw IllegalLifecycleTransitionException(state, target)
		state = target
		lastTransition = label
		failureReason = if (target == LifecycleState.FAILED) reason else null
	}
}

class DuplicateModuleException(val moduleId: ModuleId) :
	IllegalStateException("Module already registered: $moduleId")

class DuplicateAddonException(val addonId: AddonId) :
	IllegalStateException("Addon already registered: $addonId")

// Stores modules by stable ID and rejects duplicates. Also tracks discovered addons and their source.
class ModuleRegistry {
	private val modulesById = LinkedHashMap<ModuleId, ModuleRecord>()
	private val addonsById = LinkedHashMap<AddonId, AddonRecord>()

	fun recordAddon(metadata: AddonMetadata, source: AddonSource): AddonRecord {
		if (addonsById.containsKey(metadata.id)) throw DuplicateAddonException(metadata.id)
		val record = AddonRecord(metadata, source)
		addonsById[metadata.id] = record
		return record
	}

	fun register(record: ModuleRecord): ModuleRecord {
		if (modulesById.containsKey(record.id)) throw DuplicateModuleException(record.id)
		modulesById[record.id] = record
		return record
	}

	fun get(id: ModuleId): ModuleRecord? = modulesById[id]

	fun all(): List<ModuleRecord> = modulesById.values.toList()

	fun byAddon(addonId: AddonId): List<ModuleRecord> = modulesById.values.filter { it.addonId == addonId }

	fun byCategory(category: ModuleCategory): List<ModuleRecord> =
		modulesById.values.filter { it.module.metadata.category == category }

	fun addon(id: AddonId): AddonMetadata? = addonsById[id]?.metadata

	fun addons(): List<AddonRecord> = addonsById.values.toList()
}
