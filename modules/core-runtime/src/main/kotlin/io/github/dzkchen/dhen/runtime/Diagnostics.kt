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
	val artifactType: String,
	val authors: List<String>,
	val sourceUrl: String?,
	val sourceType: String,
	val sourceLocation: String?,
	val requiredDhenApi: String,
	val minecraftVersionRange: String,
	val fabricLoaderVersionRange: String,
	val dependencies: List<String>,
	val conflicts: List<String>,
	val providedModules: List<String>,
	val releaseNotes: String,
) {
	val displayAuthors: String get() = if (authors.isEmpty()) "unknown author" else authors.joinToString(", ")
	val displaySource: String get() = sourceLocation ?: sourceUrl ?: "unknown"
}

data class DiagnosticsSnapshot(
	val addons: List<AddonDiagnostics>,
	val modules: List<ModuleDiagnostics>,
) {
	val totalActiveHandles: Int get() = modules.sumOf { it.activeHandles }
	val failedModules: List<ModuleDiagnostics> get() = modules.filter { it.state == LifecycleState.FAILED }
}
