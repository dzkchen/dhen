package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AnimationsTest {
	@AfterEach
	fun reset() {
		for (setting in Animations.settings) setting.reset()
	}

	@Test
	fun `declares the complete held-item control surface`() {
		assertEquals("Animations", Animations.name)
		assertEquals(Category.VISUAL, Animations.category)
		assertEquals(
			listOf(
				"Item Scale", "X", "Y", "Z", "Rotation X", "Rotation Y", "Rotation Z",
				"Swing X", "Swing Y", "Swing Z", "Disable Hand Movement", "Disable Equip Animation",
				"Disable Swing Animation", "Terminator Only", "Swing Speed", "Ignore Haste", "Reset"
			),
			Animations.settings.map { it.name }
		)
		for (setting in Animations.settings.take(10)) assertInstanceOf(NumberSetting::class.java, setting)
		for (setting in Animations.settings.subList(10, 14)) assertInstanceOf(BooleanSetting::class.java, setting)
		assertInstanceOf(NumberSetting::class.java, Animations.settings[14])
		assertInstanceOf(BooleanSetting::class.java, Animations.settings[15])
		assertInstanceOf(ActionSetting::class.java, Animations.settings[16])
		assertEquals(-1.5, Animations.itemScaleSetting.min)
		assertEquals(1.5, Animations.itemScaleSetting.max)
		assertEquals(0.05, Animations.itemScaleSetting.step)
		assertEquals(0.01, Animations.xSetting.step)
		assertEquals(1.0, Animations.swingXSetting.default)
		assertEquals(-2.0, Animations.swingSpeedSetting.min)
		assertEquals(1.0, Animations.swingSpeedSetting.max)
	}

	@Test
	fun `dependent controls follow the disable-swing mode`() {
		assertFalse(Animations.terminatorOnlySetting.isVisible)
		assertTrue(Animations.swingSpeedSetting.isVisible)
		assertTrue(Animations.ignoreHasteSetting.isVisible)

		Animations.disableSwingAnimationSetting.on = true

		assertTrue(Animations.terminatorOnlySetting.isVisible)
		assertFalse(Animations.swingSpeedSetting.isVisible)
		assertFalse(Animations.ignoreHasteSetting.isVisible)

		Animations.terminatorOnlySetting.on = true

		assertTrue(Animations.swingSpeedSetting.isVisible)
		assertTrue(Animations.ignoreHasteSetting.isVisible)
	}

	@Test
	fun `stored controls round trip and reset restores every default`() {
		Animations.itemScaleSetting.amount = 0.75
		Animations.xSetting.amount = -1.25
		Animations.swingYSetting.amount = 1.6
		Animations.disableSwingAnimationSetting.on = true
		Animations.terminatorOnlySetting.on = true
		val saved = SettingCodec.writeInto(JsonObject(), Animations.settings)

		assertEquals(Animations.settings.dropLast(1).map { it.name }.toSet(), saved.keySet())
		Animations.resetSetting.value.invoke()
		assertEquals(0.0, Animations.itemScaleSetting.amount)
		assertEquals(0.0, Animations.xSetting.amount)
		assertEquals(1.0, Animations.swingYSetting.amount)
		assertFalse(Animations.disableSwingAnimationSetting.on)
		assertFalse(Animations.terminatorOnlySetting.on)

		SettingCodec.readInto(saved, Animations.settings, Animations.name)

		assertEquals(0.75, Animations.itemScaleSetting.amount)
		assertEquals(-1.25, Animations.xSetting.amount)
		assertEquals(1.6, Animations.swingYSetting.amount)
		assertTrue(Animations.disableSwingAnimationSetting.on)
		assertTrue(Animations.terminatorOnlySetting.on)
	}

	@Test
	fun `position scale rotation and swing settings change the pose`() = withAnimations {
		Animations.xSetting.amount = 0.5
		Animations.ySetting.amount = 0.25
		Animations.zSetting.amount = -0.75
		val main = PoseStack()
		Animations.applyHandOffset(main, InteractionHand.MAIN_HAND, ItemStack(Items.STICK))
		assertEquals(0.5f, main.last().pose().m30(), 0.0001f)
		assertEquals(0.25f, main.last().pose().m31(), 0.0001f)
		assertEquals(-0.75f, main.last().pose().m32(), 0.0001f)

		val off = PoseStack()
		Animations.applyHandOffset(off, InteractionHand.OFF_HAND, ItemStack(Items.STICK))
		assertEquals(-0.5f, off.last().pose().m30(), 0.0001f)

		Animations.itemScaleSetting.amount = 0.5
		Animations.rotationZSetting.amount = 30.0
		val item = PoseStack()
		Animations.applyItemTransform(item)
		assertEquals(1.5f, item.last().pose().getScale(org.joml.Vector3f()).x(), 0.0001f)
		assertTrue(item.last().pose().m01() > 0.0f)

		Animations.swingXSetting.amount = 0.5
		Animations.swingYSetting.amount = 1.5
		Animations.swingZSetting.amount = 2.0
		val swing = PoseStack()
		Animations.applySwingOffset(swing, 1.0f, 2.0f, 3.0f)
		assertEquals(0.5f, swing.last().pose().m30(), 0.0001f)
		assertEquals(3.0f, swing.last().pose().m31(), 0.0001f)
		assertEquals(6.0f, swing.last().pose().m32(), 0.0001f)
	}

	@Test
	fun `swing suppression respects Terminator Only`() = withAnimations {
		Animations.disableSwingAnimationSetting.on = true
		val ordinary = ItemStack(Items.BOW)
		val terminator = ItemStack(Items.BOW).also {
			it.set(DataComponents.CUSTOM_DATA, ItemFixture.customData { putString("id", "TERMINATOR") })
		}

		assertEquals(1.0f, Animations.swingProgress(0.4f, ordinary))
		Animations.terminatorOnlySetting.on = true
		assertEquals(0.4f, Animations.swingProgress(0.4f, ordinary))
		assertEquals(1.0f, Animations.swingProgress(0.4f, terminator))
	}

	@Test
	fun `swing duration preserves vanilla duration and applies speed boundaries`() {
		assertEquals(6, Animations.adjustedSwingDuration(6, 0.0))
		assertEquals(11, Animations.adjustedSwingDuration(11, 0.0))
		assertEquals(2, Animations.adjustedSwingDuration(6, 1.0))
		assertEquals(44, Animations.adjustedSwingDuration(6, -2.0))
		assertEquals(1, Animations.adjustedSwingDuration(-4, 0.0))
	}

	private inline fun withAnimations(block: () -> Unit) {
		val manager = ModuleManager()
		manager.register(Animations)
		try {
			manager.enable(Animations)
			block()
		} finally {
			manager.unregister(Animations)
		}
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
