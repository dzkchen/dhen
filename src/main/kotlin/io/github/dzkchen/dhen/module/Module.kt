package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.event.DeepProfiledEvent
import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.delayServerTicks
import io.github.dzkchen.dhen.util.delayTicks
import io.github.dzkchen.dhen.util.repeatServerTicks
import io.github.dzkchen.dhen.util.repeatTicks
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadWriteProperty

abstract class Module(
	val name: String,
	val category: Category,
	val description: String
) {
	@Volatile
	var enabled: Boolean = false
		private set

	@Volatile
	var errorCount: Int = 0
		private set

	private val registrations = mutableListOf<Registration<out Event>>()
	private val settingList = mutableListOf<Setting<*>>()
	private val hudList = mutableListOf<HudElement>()
	private val disposables = mutableListOf<Handle>()
	@Volatile
	private var clientTickTiming: HandlerTiming? = null
	@Volatile
	private var serverTickTiming: HandlerTiming? = null

	val settings: List<Setting<*>>
		get() = settingList.toList()
	val hudElements: List<HudElement> = Collections.unmodifiableList(hudList)
	val handlerTimings: List<HandlerTiming>
		get() = buildList(registrations.size + 2) {
			registrations.mapTo(this) { it.timing }
			clientTickTiming?.let(::add)
			serverTickTiming?.let(::add)
		}
	val subscriptionCount: Int
		get() = registrations.size
	@Volatile
	private var host: Host = Host.UNBOUND
	private val bound: Boolean
		get() = host !== Host.UNBOUND
	private val stateLock = Any()
	private var windowStart = 0L
	private var warned = false
	@Volatile
	private var moduleScope: CoroutineScope? = null
	private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
		onHandlerError(throwable)
	}

	protected inline fun <reified T : Event> on(priority: Int = 0, noinline handler: (T) -> Unit) {
		register(T::class.java, priority, handler)
	}

	protected fun <T : HudElement> hud(element: T): T {
		require(!bound) { "Module '$name' HUD elements must be registered before manager registration." }
		require(hudList.none { it.name == element.name }) {
			"Module '$name' already owns a HUD element named '${element.name}'."
		}
		hudList += element
		return element
	}

	protected fun launch(block: suspend CoroutineScope.() -> Unit): Job? =
		moduleScope?.launch(block = block)

	protected fun inTicks(ticks: Int, block: () -> Unit): Job? =
		launch { delayTicks(ticks); block() }

	protected fun inServerTicks(ticks: Int, block: () -> Unit): Job? =
		launch { delayServerTicks(ticks); block() }

	protected fun everyTicks(ticks: Int, block: () -> Unit): Job? {
		val scope = moduleScope ?: return null
		val timing = tickTiming(server = false)
		val profiler = host.profiler
		return scope.launch { repeatTicks(ticks) { isolated(profiler, timing, block = block) } }
	}

	protected fun everyServerTicks(ticks: Int, block: () -> Unit): Job? {
		val scope = moduleScope ?: return null
		val timing = tickTiming(server = true)
		val profiler = host.profiler
		return scope.launch { repeatServerTicks(ticks) { isolated(profiler, timing, block = block) } }
	}

	private fun tickTiming(server: Boolean): HandlerTiming = synchronized(stateLock) {
		if (server) {
			serverTickTiming ?: HandlerTiming(SERVER_TICK_TASK).also { serverTickTiming = it }
		} else {
			clientTickTiming ?: HandlerTiming(CLIENT_TICK_TASK).also { clientTickTiming = it }
		}
	}

	private inline fun isolated(
		profiler: HandlerProfiler,
		timing: HandlerTiming? = null,
		timedOnlyInDeepMode: Boolean = true,
		block: () -> Unit
	) {
		val timed = timing != null && (!timedOnlyInDeepMode || profiler.deepMode)
		val started = if (timed) profiler.clock.nanoTime() else 0L
		try {
			block()
		} catch (throwable: Throwable) {
			onHandlerError(throwable)
		} finally {
			if (timed) timing.record(profiler.clock.nanoTime() - started)
		}
	}

	internal fun setEnabled(enabled: Boolean): Boolean {
		synchronized(stateLock) {
			if (this.enabled == enabled) return false
			this.enabled = enabled
			if (enabled) {
				resetErrorState()
				moduleScope = CoroutineScope(SupervisorJob() + host.scopeContext + coroutineExceptionHandler)
			} else {
				moduleScope?.cancel()
				moduleScope = null
			}
		}
		notifyStateChangeQuietly()
		return true
	}

	internal fun toggle() {
		setEnabled(!enabled)
	}

	internal fun reportError(throwable: Throwable) {
		onHandlerError(throwable)
	}

	internal fun activateKeybind(setting: KeybindSetting) {
		if (!enabled && !setting.firesWhileDisabled) return
		isolated(host.profiler) { setting.activate() }
	}

	internal fun bind(
		eventBus: EventBus,
		profiler: HandlerProfiler,
		notifier: ModuleNotifier,
		clock: () -> Long,
		clientDispatcher: CoroutineContext,
		stateListener: (Module) -> Unit
	) {
		requireUnbound()
		host = Host(notifier, clock, clientDispatcher, stateListener, profiler)
		for (registration in registrations) disposables += registration.bind(eventBus, profiler, this)
	}

	internal fun retain(handle: Handle) {
		require(bound) { "Module '$name' must be bound before it can retain a handle." }
		disposables += handle
	}

	internal fun unbind() {
		for (handle in disposables) handle.unsubscribe()
		disposables.clear()
		for (registration in registrations) registration.resetTiming()
		clientTickTiming = null
		serverTickTiming = null
		host = Host.UNBOUND
	}

	internal fun requireUnbound() {
		require(!bound) { "Module '$name' is already bound to an event bus." }
	}

	internal fun <T> registerSetting(setting: Setting<T>): ReadWriteProperty<Module, T> {
		require(!bound) { "Module '$name' settings must be registered before manager registration." }
		setting.owner = this
		settingList += setting
		return setting
	}

	@PublishedApi
	internal fun <T : Event> register(type: Class<T>, priority: Int, handler: (T) -> Unit) {
		require(!bound) { "Module '$name' event handlers must be registered before manager registration." }

		val registration = Registration(type, priority, handler)
		registrations += registration
	}

	private fun resetErrorState() {
		errorCount = 0
		warned = false
		for (element in hudList) element.clearFailure()
	}

	private fun onHandlerError(throwable: Throwable) {
		log.error("Module '{}' handler threw", name, throwable)

		var firstSinceEnable = false
		val overThreshold = synchronized(stateLock) {
			val now = host.clock()
			if (errorCount == 0 || now - windowStart > ERROR_WINDOW_MS) {
				windowStart = now
				errorCount = 0
			}
			errorCount++
			if (!warned) {
				warned = true
				firstSinceEnable = true
			}
			errorCount >= ERROR_THRESHOLD
		}

		if (firstSinceEnable) notifyQuietly("Module '$name' encountered an error.")

		if (overThreshold && setEnabled(false)) {
			notifyQuietly("Module '$name' auto-disabled after repeated errors.")
		}
	}

	private fun notifyStateChangeQuietly() {
		try {
			host.stateListener(this)
		} catch (throwable: Throwable) {
			log.error("Module '{}' state listener threw", name, throwable)
		}
	}

	private fun notifyQuietly(message: String) {
		try {
			host.notifier.notify(this, message)
		} catch (throwable: Throwable) {
			log.error("Module '{}' notifier threw", name, throwable)
		}
	}

	private class Registration<T : Event>(
		private val type: Class<T>,
		private val priority: Int,
		private val handler: (T) -> Unit
	) {
		var timing = HandlerTiming(type.simpleName.ifEmpty { type.name })
			private set

		fun bind(eventBus: EventBus, profiler: HandlerProfiler, module: Module): Handle {
			val timedOnlyInDeepMode = DeepProfiledEvent::class.java.isAssignableFrom(type)
			return eventBus.type(type).subscribe(priority) { event ->
				if (!module.enabled) return@subscribe
				module.isolated(profiler, timing, timedOnlyInDeepMode) { handler(event) }
			}
		}

		fun resetTiming() {
			timing = HandlerTiming(timing.eventName)
		}
	}

	private class Host(
		val notifier: ModuleNotifier,
		val clock: () -> Long,
		val scopeContext: CoroutineContext,
		val stateListener: (Module) -> Unit,
		val profiler: HandlerProfiler
	) {
		companion object {
			val UNBOUND = Host(
				ModuleNotifier.LogBacked,
				System::currentTimeMillis,
				Dispatchers.Unconfined,
				{},
				HandlerProfiler(NanoClock.SYSTEM)
			)
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		internal const val ERROR_THRESHOLD = 5
		internal const val ERROR_WINDOW_MS = 10_000L
		private const val CLIENT_TICK_TASK = "ClientTickTask"
		private const val SERVER_TICK_TASK = "ServerTickTask"
	}
}
