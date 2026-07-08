package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.EventBus
import java.util.Locale

class ModuleManager(val eventBus: EventBus = EventBus()) {
	private val modulesByName = linkedMapOf<String, Module>()
	private val modulesByCategory = linkedMapOf<Category, MutableList<Module>>()

	val modules: Collection<Module>
		get() = modulesByName.values.toList()

	val categories: Map<Category, List<Module>>
		get() = modulesByCategory.mapValues { (_, modules) -> modules.toList() }

	fun register(module: Module): Module {
		val key = key(module.name)
		requireAvailable(module, key)

		addValidated(module, key)
		return module
	}

	fun registerAll(vararg modules: Module) {
		val pendingKeys = HashSet<String>(modules.size)
		for (module in modules) {
			val key = key(module.name)
			requireAvailable(module, key)
			require(pendingKeys.add(key)) { "Module '${module.name}' is already registered." }
		}
		for (module in modules) addValidated(module, key(module.name))
	}

	operator fun get(name: String): Module? =
		modulesByName[key(name)]

	fun enable(name: String): Module =
		requireModule(name).also { it.setEnabled(true) }

	fun disable(name: String): Module =
		requireModule(name).also { it.setEnabled(false) }

	fun toggle(name: String): Module =
		requireModule(name).also { it.toggle() }

	fun enable(module: Module) {
		requireRegistered(module).setEnabled(true)
	}

	fun disable(module: Module) {
		requireRegistered(module).setEnabled(false)
	}

	fun toggle(module: Module) {
		requireRegistered(module).toggle()
	}

	private fun requireModule(name: String): Module =
		this[name] ?: throw NoSuchElementException("No module named '$name' is registered.")

	private fun requireRegistered(module: Module): Module {
		val registered = requireModule(module.name)
		require(registered === module) { "Module '${module.name}' is not registered with this manager." }
		return registered
	}

	private fun requireAvailable(module: Module, key: String) {
		require(key !in modulesByName) { "Module '${module.name}' is already registered." }
		module.requireUnbound()
	}

	private fun addValidated(module: Module, key: String) {
		module.bind(eventBus)
		modulesByName[key] = module
		modulesByCategory.getOrPut(module.category) { mutableListOf() }.add(module)
	}

	private fun key(name: String): String =
		name.lowercase(Locale.ROOT)
}
