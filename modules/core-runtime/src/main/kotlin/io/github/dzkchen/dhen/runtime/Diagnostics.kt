package io.github.dzkchen.dhen.runtime

data class ModuleDiagnostics(
	val moduleId: String,
	val addonId: String,
	val name: String,
	val category: String,
	val state: LifecycleState,
	val activeHandles: Int,
	val failureReason: String?,
	val lastTransition: String,
)

data class AddonDiagnostics(
	val addonId: String,
	val name: String,
	val version: String,
	val moduleCount: Int,
)

data class DiagnosticsSnapshot(
	val addons: List<AddonDiagnostics>,
	val modules: List<ModuleDiagnostics>,
) {
	val totalActiveHandles: Int get() = modules.sumOf { it.activeHandles }
	val failedModules: List<ModuleDiagnostics> get() = modules.filter { it.state == LifecycleState.FAILED }
}
