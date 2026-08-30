package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.color.block.BlockTintSources
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.FluidModel
import net.minecraft.client.renderer.block.FluidStateModelSet
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.core.BlockPos
import net.minecraft.util.ARGB
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import kotlin.math.roundToInt

object VisualTweaks : Module(
	name = "Visual Tweaks",
	category = Category.VISUAL,
	description = "Renders lava as water and darkens the screen."
) {
	internal val lavaToWaterSetting = BooleanSetting(
		"Lava to Water",
		false,
		"Replaces lava visuals with configurable water visuals."
	)
	private var lavaToWater by lavaToWaterSetting

	internal val colorTintSetting = BooleanSetting("Color Tint")
		.withDependency { lavaToWaterSetting.on }
	private var colorTint by colorTintSetting

	internal val tintColorSetting = ColorSetting("Tint Color", Color.rgba(63, 118, 228))
		.withDependency { lavaToWaterSetting.on && colorTintSetting.on }
	private var tintColor by tintColorSetting

	internal val hideFogSetting = BooleanSetting("Hide Fog", true, "Removes lava fog.")
		.withDependency { lavaToWaterSetting.on }
	private var hideFog by hideFogSetting

	internal val seeThroughWaterSetting = BooleanSetting(
		"See Through Water",
		false,
		"Renders water and replaced lava at 50% opacity."
	).withDependency { lavaToWaterSetting.on }
	private var seeThroughWater by seeThroughWaterSetting

	internal val darkModeSetting = BooleanSetting(
		"Dark Mode",
		false,
		"Darkens the world behind screens with an optional HUD tint."
	)
	private var darkMode by darkModeSetting

	internal val opacitySetting = NumberSetting("Opacity", 25.0, 1.0, 80.0, 1.0, "Strength of the dark tint.")
		.withDependency { darkModeSetting.on }
	private var opacity by opacitySetting

	internal val tintHudSetting = BooleanSetting("Tint HUD", false, "Draws the tint over HUD elements.")
		.withDependency { darkModeSetting.on }
	private var tintHud by tintHudSetting

	@Volatile
	private var transparentWater: ModelCache? = null
	@Volatile
	private var tintedWater: TintModelCache? = null
	private var appliedSignature = modelSignature()

	init {
		on<ClientTickEvent.End> { refreshModels() }
	}

	override fun onEnabled() {
		appliedSignature = modelSignature()
		if (lavaToWaterSetting.on) invalidateModels()
	}

	override fun onDisabled() {
		if (lavaToWaterSetting.on) invalidateModels()
	}

	@JvmStatic
	fun replaceModel(set: FluidStateModelSet, state: FluidState, original: FluidModel): FluidModel {
		if (!rendersLavaAsWater() || !usesWaterModel(state.type)) return original
		return when (state.type) {
			Fluids.WATER, Fluids.FLOWING_WATER -> waterModel(original)
			Fluids.LAVA, Fluids.FLOWING_LAVA -> lavaModel(set)
			else -> original
		}
	}

	@JvmStatic
	fun rendersLavaAsWater(): Boolean = enabled && lavaToWaterSetting.on

	@JvmStatic
	fun shouldHideFog(): Boolean = rendersLavaAsWater() && hideFogSetting.on

	@JvmStatic
	fun hideFog(fog: FogData, renderDistance: Float) {
		fog.color.w = 0.0f
		fog.environmentalStart = renderDistance
		fog.environmentalEnd = renderDistance
	}

	@JvmStatic
	fun tintsFog(): Boolean = colorTintSetting.on

	@JvmStatic
	fun fogTint(): Int = tintColorSetting.value.argb

	@JvmStatic
	fun drawBehindHud(graphics: GuiGraphicsExtractor) {
		if (drawsBehindHud()) fillScreen(graphics)
	}

	@JvmStatic
	fun drawOverHud(graphics: GuiGraphicsExtractor) {
		if (!drawsOverHud(subtitlesDeferred(), afterDeferredSubtitles = false, hudExtracted = true)) return
		graphics.nextStratum()
		fillScreen(graphics)
	}

	@JvmStatic
	fun drawOverDeferredHud(graphics: GuiGraphicsExtractor, hudExtracted: Boolean) {
		if (!drawsOverHud(subtitlesDeferred(), afterDeferredSubtitles = true, hudExtracted)) return
		graphics.nextStratum()
		fillScreen(graphics)
	}

	internal fun drawsBehindHud(): Boolean = enabled && darkModeSetting.on && !tintHudSetting.on

	internal fun drawsOverHud(deferred: Boolean, afterDeferredSubtitles: Boolean, hudExtracted: Boolean): Boolean =
		hudExtracted && enabled && darkModeSetting.on && tintHudSetting.on && deferred == afterDeferredSubtitles

	internal fun overlayColor(): Int = ARGB.black((opacitySetting.amount * CHANNEL_MAX / 100.0).roundToInt())

	internal fun transparent(color: Int): Int = ARGB.color(WATER_ALPHA, color)

	internal fun usesWaterModel(fluid: Fluid): Boolean = when (fluid) {
		Fluids.WATER, Fluids.FLOWING_WATER -> seeThroughWaterSetting.on
		Fluids.LAVA, Fluids.FLOWING_LAVA -> true
		else -> false
	}

	internal fun modelSignature(): Long {
		var signature = tintColorSetting.value.argb.toLong() shl FLAG_BITS
		if (lavaToWaterSetting.on) signature = signature or LAVA_FLAG
		if (colorTintSetting.on) signature = signature or TINT_FLAG
		if (seeThroughWaterSetting.on) signature = signature or TRANSPARENT_FLAG
		return signature
	}

	private fun subtitlesDeferred(): Boolean {
		val screen = Minecraft.getInstance().gui.screen()
		return screen == null || screen.isInGameUi
	}

	private fun fillScreen(graphics: GuiGraphicsExtractor) {
		SharpGui.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), overlayColor())
	}

	private fun lavaModel(set: FluidStateModelSet): FluidModel {
		val water = set.get(Fluids.WATER.defaultFluidState())
		if (!colorTintSetting.on) return water
		val selected = tintColorSetting.value.argb
		val argb = if (seeThroughWaterSetting.on) transparent(selected) else selected
		val cached = tintedWater
		if (cached != null && cached.base === water && cached.argb == argb) return cached.model
		val model = water.withTint(BlockTintSources.constant(argb, argb))
		tintedWater = TintModelCache(water, argb, model)
		return model
	}

	private fun waterModel(original: FluidModel): FluidModel {
		val cached = transparentWater
		if (cached != null && cached.base === original) return cached.model
		val source = original.tintSource() ?: return original
		val model = original.withTint(TransparentTint(source))
		transparentWater = ModelCache(original, model)
		return model
	}

	private fun FluidModel.withTint(source: BlockTintSource): FluidModel = FluidModel(
		layer(),
		stillMaterial(),
		flowingMaterial(),
		overlayMaterial(),
		source
	)

	private fun refreshModels() {
		val signature = modelSignature()
		if (signature == appliedSignature) return
		appliedSignature = signature
		invalidateModels()
	}

	private fun invalidateModels() {
		transparentWater = null
		tintedWater = null
		val minecraft: Minecraft? = Minecraft.getInstance()
		minecraft?.levelExtractor?.allChanged()
	}

	private class TransparentTint(private val source: BlockTintSource) : BlockTintSource {
		override fun color(state: BlockState): Int = transparent(source.color(state))

		override fun colorInWorld(state: BlockState, level: BlockAndTintGetter, pos: BlockPos): Int =
			transparent(source.colorInWorld(state, level, pos))

		override fun colorAsTerrainParticle(state: BlockState, level: BlockAndTintGetter, pos: BlockPos): Int =
			transparent(source.colorAsTerrainParticle(state, level, pos))

		override fun relevantProperties(): Set<Property<*>> = source.relevantProperties()
	}

	private class ModelCache(val base: FluidModel, val model: FluidModel)

	private class TintModelCache(val base: FluidModel, val argb: Int, val model: FluidModel)

	private const val WATER_ALPHA = 128
	private const val CHANNEL_MAX = 255.0
	private const val FLAG_BITS = 3
	private const val LAVA_FLAG = 1L
	private const val TINT_FLAG = 2L
	private const val TRANSPARENT_FLAG = 4L
}
