package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TweaksTest {
	@AfterEach
	fun reset() {
		resetSettings()
	}

	@Test
	fun `declares one QOL module with dependent close visibility`() {
		assertEquals("Tweaks", Tweaks.name)
		assertEquals(Category.QOL, Tweaks.category)
		assertEquals(2, Tweaks.subscriptionCount)
		assertEquals(
			listOf(
				"Hide Recipe Book",
				"Close Recipe Book",
				"Hide Item Cooldowns",
				"Hide Hotbar Tooltips",
				"Cake Numbers",
				"Hide Advancement Toasts",
				"Hide Recipe Toasts",
				"Hide System Toasts",
				"Skip Reconfigure Screen",
				"Skip Multiplayer Warning",
				"Fit Title Text",
				"Steady Night Vision",
				"Hide Item Frames"
			),
			Tweaks.settings.map { it.name }
		)
		assertFalse(Tweaks.closeRecipeBookSetting.isVisible)

		Tweaks.hideRecipeBook = true

		assertTrue(Tweaks.closeRecipeBookSetting.isVisible)
	}

	@Test
	fun `the recipe book and slot settings round trip through the setting codec`() {
		Tweaks.hideRecipeBook = true
		Tweaks.closeRecipeBook = true
		Tweaks.hideItemCooldowns = true
		Tweaks.hideHotbarTooltips = true
		Tweaks.cakeNumbers = true
		val saved = SettingCodec.writeInto(com.google.gson.JsonObject(), Tweaks.settings)

		resetSettings()
		SettingCodec.readInto(saved, Tweaks.settings, Tweaks.name)

		assertTrue(Tweaks.hideRecipeBook)
		assertTrue(Tweaks.closeRecipeBook)
		assertTrue(Tweaks.hideItemCooldowns)
		assertTrue(Tweaks.hideHotbarTooltips)
		assertTrue(Tweaks.cakeNumbers)
	}

	@Test
	fun `runtime gates require the module and preserve hide close coupling`() {
		val manager = ModuleManager()
		manager.register(Tweaks)
		try {
			Tweaks.hideRecipeBook = true
			Tweaks.closeRecipeBook = true
			Tweaks.hideItemCooldowns = true
			Tweaks.hideHotbarTooltips = true

			assertFalse(Tweaks.shouldHideRecipeBook())
			assertFalse(Tweaks.shouldCloseRecipeBook())
			assertFalse(Tweaks.hidesItemCooldown(inSkyBlock = true))
			assertFalse(Tweaks.shouldHideHotbarTooltip())

			manager.enable(Tweaks)

			assertTrue(Tweaks.shouldHideRecipeBook())
			assertTrue(Tweaks.shouldCloseRecipeBook())
			assertTrue(Tweaks.hidesItemCooldown(inSkyBlock = true))
			assertFalse(Tweaks.hidesItemCooldown(inSkyBlock = false))
			assertTrue(Tweaks.shouldHideHotbarTooltip())

			Tweaks.hideRecipeBook = false

			assertFalse(Tweaks.shouldHideRecipeBook())
			assertFalse(Tweaks.shouldCloseRecipeBook())
		} finally {
			manager.unregister(Tweaks)
		}
	}

	@Test
	fun `cake parser accepts the reference shape and rejects incomplete names`() {
		assertEquals("1", CakeYearCache.parse("New Year Cake (Year 1)"))
		assertEquals("404", CakeYearCache.parse("§bNew Year Cake (Year 404)"))
		assertNull(CakeYearCache.parse("New Year Cake"))
		assertNull(CakeYearCache.parse("New Year Cake (Year )"))
	}

	@Test
	fun `cake cache reuses a parsed year and invalidates when the stack name changes`() {
		val cache = CakeYearCache()
		val stack = ItemStack(Items.CAKE)
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("New Year Cake (Year 12)"))

		val first = cache.year(stack)
		val second = cache.year(stack)

		assertEquals("12", first)
		assertSame(first, second)

		stack.set(DataComponents.CUSTOM_NAME, Component.literal("New Year Cake (Year 13)"))

		assertEquals("13", cache.year(stack))
	}

	private companion object {
		fun resetSettings() {
			for (setting in Tweaks.settings) setting.reset()
		}

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
