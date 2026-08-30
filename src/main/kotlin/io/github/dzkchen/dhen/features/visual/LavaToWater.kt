package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.color.block.BlockTintSources
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

object LavaToWater : Module(
	name = "Lava to Water",
	category = Category.VISUAL,
	description = "Replaces lava visuals with configurable water visuals."
) {
	internal val colorTintSetting = BooleanSetting("Color Tint")
	private var colorTint by colorTintSetting

	internal val tintColorSetting = ColorSetting("Tint Color", Color.rgba(63, 118, 228))
		.withDependency { colorTintSetting.on }
	private var tintColor by tintColorSetting

	internal val hideFogSetting = BooleanSetting("Hide Fog", true, "Removes lava fog.")
	private var hideFog by hideFogSetting

	internal val seeThroughWaterSetting = BooleanSetting(
		"See Through Water",
		false,
		"Renders water and replaced lava at 50% opacity."
	)
	private var seeThroughWater by seeThroughWaterSetting

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
		invalidateModels()
	}

	override fun onDisabled() {
		invalidateModels()
	}

	@JvmStatic
	fun replaceModel(set: FluidStateModelSet, state: FluidState, original: FluidModel): FluidModel {
		if (!enabled || !usesWaterModel(state.type)) return original
		return when (state.type) {
			Fluids.WATER, Fluids.FLOWING_WATER -> waterModel(original)
			Fluids.LAVA, Fluids.FLOWING_LAVA -> lavaModel(set)
			else -> original
		}
	}

	@JvmStatic
	fun isActive(): Boolean = enabled

	@JvmStatic
	fun shouldHideFog(): Boolean = enabled && hideFogSetting.on

	@JvmStatic
	fun hideFog(fog: FogData, renderDistance: Float) {
		fog.color.w = 0.0f
		fog.environmentalStart = renderDistance
		fog.environmentalEnd = renderDistance
	}

	@JvmStatic
	fun fogColor(waterColor: Int): Int =
		if (colorTintSetting.on) tintColorSetting.value.argb else waterColor

	internal fun transparent(color: Int): Int = ARGB.color(WATER_ALPHA, color)

	internal fun usesWaterModel(fluid: Fluid): Boolean = when (fluid) {
		Fluids.WATER, Fluids.FLOWING_WATER -> seeThroughWaterSetting.on
		Fluids.LAVA, Fluids.FLOWING_LAVA -> true
		else -> false
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
		if (!seeThroughWaterSetting.on) return original
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
		if (!enabled) return
		val signature = modelSignature()
		if (signature == appliedSignature) return
		appliedSignature = signature
		invalidateModels()
	}

	private fun modelSignature(): Int {
		var signature = if (colorTintSetting.on) tintColorSetting.value.argb else NO_TINT
		if (seeThroughWaterSetting.on) signature = signature xor TRANSPARENCY_SIGNATURE
		return signature
	}

	private fun invalidateModels() {
		transparentWater = null
		tintedWater = null
		val minecraft: Minecraft? = Minecraft.getInstance()
		val level = minecraft?.level ?: return
		minecraft.levelRenderer.invalidateCompiledGeometry(
			level,
			minecraft.options,
			minecraft.gameRenderer.mainCamera(),
			minecraft.blockColors
		)
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
	private const val NO_TINT = 0
	private const val TRANSPARENCY_SIGNATURE = Int.MIN_VALUE
}
