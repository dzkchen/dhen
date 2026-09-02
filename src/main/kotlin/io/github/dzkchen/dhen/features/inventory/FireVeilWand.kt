package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.InteractionEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStack

object FireVeilWand : Module(
	name = "Fire Veil Wand",
	category = Category.COMBAT,
	description = "Replaces the Fire Veil Wand's flame ring with a clean outline, or hides it."
) {
	internal val designSetting = SelectorSetting(
		"Fire Veil Design",
		LINE,
		listOf(PARTICLES, LINE, OFF),
		description = "Particles keeps Hypixel's flames, Line draws a circle instead, Off shows neither."
	)

	internal val lineColorSetting = ColorSetting(
		"Line Color",
		Color.rgba(LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA),
		allowAlpha = true,
		description = "The colour of that circle."
	).withDependency { designSetting.value == LINE }

	private var design by designSetting
	private var lineColor by lineColorSetting

	private var lastClick = FAR_PAST

	init {
		on<InteractionEvent.UseItem> { clicked(it.item, Util.getMillis()) }
		on<InteractionEvent.UseBlock> { clicked(it.item, Util.getMillis()) }
		on<InteractionEvent.UseEntity> { clicked(it.item, Util.getMillis()) }
		on<PacketReceiveEvent.Pre> { event ->
			val packet = event.packet
			if (packet is ClientboundLevelParticlesPacket && swallows(packet, Util.getMillis())) event.cancelled = true
		}
		on<WorldRenderEvent> { drawRing(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onDisabled() = forget()

	internal fun forget() {
		lastClick = FAR_PAST
	}

	internal fun clicked(stack: ItemStack, now: Long) {
		if (!SkyBlockLocation.inSkyBlock || stack.isEmpty) return
		if (SkyBlockItems.of(stack).id == FIRE_VEIL_WAND) lastClick = now
	}

	internal fun active(now: Long): Boolean = now - lastClick <= ACTIVE_MILLIS

	internal fun swallows(packet: ClientboundLevelParticlesPacket, now: Long): Boolean {
		if (design == PARTICLES || !SkyBlockLocation.inSkyBlock || !active(now)) return false
		return packet.particle.type == ParticleTypes.FLAME && packet.maxSpeed == FLAME_SPEED
	}

	private fun drawRing(event: WorldRenderEvent) {
		if (design != LINE || !SkyBlockLocation.inSkyBlock || !active(Util.getMillis())) return
		val client = Minecraft.getInstance() ?: return
		val player = client.player ?: return
		val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(true)
		WorldDraw.drawWireCircle(
			event,
			Mth.lerp(partialTick.toDouble(), player.xOld, player.x),
			Mth.lerp(partialTick.toDouble(), player.yOld, player.y),
			Mth.lerp(partialTick.toDouble(), player.zOld, player.z),
			RADIUS,
			lineColor.argb,
			LINE_WIDTH
		)
	}

	internal const val PARTICLES = "Particles"
	internal const val LINE = "Line"
	internal const val OFF = "Off"

	private const val FIRE_VEIL_WAND = "FIRE_VEIL_WAND"
	private const val ACTIVE_MILLIS = 5_500L
	private const val FLAME_SPEED = 0.55f
	private const val RADIUS = 3.5
	private const val LINE_WIDTH = 5f
	private const val LINE_RED = 255
	private const val LINE_GREEN = 85
	private const val LINE_BLUE = 85
	private const val LINE_ALPHA = 245
}
