package io.github.dzkchen.dhen.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AddonContractTest {
	@Test
	fun localAddonCanRegisterFakeModuleThroughContext() {
		val context = FakeAddonContext()
		val addon = object : DhenAddon {
			override val metadata = AddonMetadata(AddonId("sample.addon"), "Sample", "1.0.0")

			override fun register(context: AddonContext) {
				context.registerModule(FakeModule)
			}
		}

		addon.register(context)

		assertEquals(listOf(FakeModule), context.modules)
		assertNull(context.client.player)
		assertNull(context.rawMinecraft.client)
		assertNull(context.rawFabric.loader)
	}
}

private object FakeModule : DhenModule {
	override val metadata = ModuleMetadata(
		id = ModuleId("sample.addon:fake"),
		name = "Fake",
		category = ModuleCategory.CHAT,
	)
}

private class FakeAddonContext : AddonContext {
	override val addonId = AddonId("sample.addon")
	override val logger = object : AddonLogger {
		override fun info(message: String) = Unit
		override fun warn(message: String) = Unit
		override fun error(message: String, throwable: Throwable?) = Unit
	}
	val modules = mutableListOf<DhenModule>()

	override fun registerModule(module: DhenModule) {
		modules += module
	}
}
