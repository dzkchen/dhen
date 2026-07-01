package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.ChatReceiveEvent
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.ModuleMetadata
import io.github.dzkchen.dhen.api.onEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private val LISTENER_MODULE_ID = ModuleId("sample.addon:listener")

private class EventListenerModule : DhenModule {
	var lastMessage: String? = null

	override val metadata = ModuleMetadata(
		id = LISTENER_MODULE_ID,
		name = "Listener",
		category = ModuleCategory.CHAT,
	)

	override fun onEnable(context: ModuleEnableContext) {
		context.onEvent<ChatReceiveEvent> { lastMessage = it.text }
	}
}

private class EventListenerAddon(private val module: EventListenerModule) : DhenAddon {
	override val metadata = AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0")

	override fun register(context: AddonContext) {
		context.registerModule(module)
	}
}

class EventTest {
	@Test
	fun dispatchedEventReachesEnabledModule(@TempDir tmp: Path) {
		val module = EventListenerModule()
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(EventListenerAddon(module))
		runtime.start()
		runtime.enableModule(LISTENER_MODULE_ID)

		runtime.dispatch(ChatReceiveEvent("hello"))
		assertEquals("hello", module.lastMessage)
	}

	@Test
	fun disabledModuleStopsReceivingEventsWithNoLeak(@TempDir tmp: Path) {
		val module = EventListenerModule()
		val runtime = DhenRuntime(FakePlatformServices(tmp))
		runtime.registerAddon(EventListenerAddon(module))
		runtime.start()
		runtime.enableModule(LISTENER_MODULE_ID)
		runtime.dispatch(ChatReceiveEvent("first"))

		runtime.disableModule(LISTENER_MODULE_ID)
		val record = runtime.modules().single()
		assertEquals(0, record.activeHandleCount)

		runtime.dispatch(ChatReceiveEvent("second"))
		assertEquals("first", module.lastMessage)
	}

	@Test
	fun throwingHandlerIsIsolatedFromOtherHandlers(@TempDir tmp: Path) {
		val logger = RecordingLogger()
		val bus = EventBus(logger)
		var reached = false
		bus.subscribe(ChatReceiveEvent::class) { error("boom") }
		bus.subscribe(ChatReceiveEvent::class) { reached = true }

		bus.dispatch(ChatReceiveEvent("x"))

		assertTrue(reached)
		assertEquals(1, logger.errors.size)
	}

	@Test
	fun cancelledSubscriptionIsNotDispatched() {
		val bus = EventBus(RecordingLogger())
		var count = 0
		val subscription = bus.subscribe(ChatReceiveEvent::class) { count++ }
		bus.dispatch(ChatReceiveEvent("a"))
		subscription.cancel()
		bus.dispatch(ChatReceiveEvent("b"))
		assertEquals(1, count)
	}

	@Test
	fun subscriptionCancelledMidDispatchDoesNotRunInSamePass() {
		val bus = EventBus(RecordingLogger())
		var secondRan = false
		lateinit var second: EventBus.Subscription
		bus.subscribe(ChatReceiveEvent::class) { second.cancel() }
		second = bus.subscribe(ChatReceiveEvent::class) { secondRan = true }

		bus.dispatch(ChatReceiveEvent("a"))

		assertEquals(false, secondRan)
	}

	@Test
	fun eventWithNoSubscribersIsIgnored() {
		val logger = RecordingLogger()
		val bus = EventBus(logger)
		bus.dispatch(ChatReceiveEvent("a"))
		assertTrue(logger.errors.isEmpty())
	}
}

private class RecordingLogger : io.github.dzkchen.dhen.api.AddonLogger {
	val errors = ArrayList<String>()

	override fun info(message: String) {}

	override fun warn(message: String) {}

	override fun error(message: String, throwable: Throwable?) {
		errors.add(message)
	}
}
