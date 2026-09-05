package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.input.ChordBinding
import io.github.dzkchen.dhen.input.KeybindRuntime
import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.client.gui.screens.Screen
import java.util.Collections
import java.util.Locale

class ModuleManager(
	val eventBus: EventBus = EventBus(),
	private val notifier: ModuleNotifier = ModuleNotifier.LogBacked,
	private val clock: () -> Long = System::currentTimeMillis,
	val clientDispatcher: ClientThreadDispatcher = ClientThreadDispatcher(),
	nanoClock: NanoClock = NanoClock.SYSTEM,
	currentScreen: () -> Screen? = { null }
) {
	val profiler = HandlerProfiler(nanoClock)
	private val keybindRuntime = KeybindRuntime(eventBus, currentScreen)
	private val modulesByName = linkedMapOf<String, Module>()
	private val modulesByCategory = linkedMapOf<Category, MutableList<Module>>()
	private val registrationOrder = mutableListOf<Module>()

	internal var stateListener: ((Module) -> Unit)? = null
	private val forwardStateChange: (Module) -> Unit = { module -> stateListener?.invoke(module) }

	val modules: Collection<Module>
		get() = modulesByName.values.toList()

	internal val ordered: List<Module> = Collections.unmodifiableList(registrationOrder)

	val categories: Map<Category, List<Module>>
		get() = modulesByCategory.mapValues { (_, modules) -> modules.toList() }

	internal fun chord(binding: ChordBinding): Handle = keybindRuntime.chord(binding)

	internal var chordDelayMillis: () -> Long
		get() = keybindRuntime.chordDelayMillis
		set(value) {
			keybindRuntime.chordDelayMillis = value
		}

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

	fun unregister(module: Module) {
		requireRegistered(module)
		module.setEnabled(false)
		module.unbind()
		modulesByName -= key(module.name)
		val inCategory = modulesByCategory.getValue(module.category)
		inCategory -= module
		if (inCategory.isEmpty()) modulesByCategory -= module.category
		registrationOrder -= module
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

	internal fun keybindCount(module: Module): Int =
		keybindRuntime.count(module)

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
		module.bind(eventBus, profiler, notifier, clock, clientDispatcher, forwardStateChange)
		module.retain(keybindRuntime.register(module))
		modulesByName[key] = module
		modulesByCategory.getOrPut(module.category) { mutableListOf() }.add(module)
		registrationOrder += module
	}

	private fun key(name: String): String =
		name.lowercase(Locale.ROOT)
}
