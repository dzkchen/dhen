package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.item.Skulls
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.ServerTickEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.WorldDepth
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.ServerClock
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

object FireFreeze : Module(
	name = "Fire Freeze",
	category = Category.COMBAT,
	description = "Counts a Fire Freeze Staff down before it lands and marks every mob it froze."
) {
	internal val freezeTimerSetting = BooleanSetting(
		"Freeze Timer",
		default = true,
		description = "Counts the seconds until an armed Fire Freeze goes off."
	)

	internal val mobTimerSetting = BooleanSetting(
		"Mob Timer",
		default = true,
		description = "Counts the ten seconds a frozen mob stays frozen, going red for the last five."
	)

	internal val mobHighlightSetting = BooleanSetting(
		"Box Frozen Mobs",
		description = "Fills a box around each frozen mob in the same colour."
	)

	internal val customCircleSetting = BooleanSetting(
		"Custom Circle",
		description = "Replaces Hypixel's ring of particles with a drawn circle."
	)

	internal val colorSetting = ColorSetting(
		"Freeze Circle Color",
		Color.rgba(0, 0, 0, CIRCLE_ALPHA),
		allowAlpha = true,
		description = "The colour of that circle."
	).withDependency { customCircleSetting.on }

	private var freezeTimer by freezeTimerSetting
	private var mobTimer by mobTimerSetting
	private var mobHighlight by mobHighlightSetting
	private var customCircle by customCircleSetting
	private var circleColor by colorSetting

	private val areas = arrayOfNulls<FreezeArea>(MAX_AREAS)
	private var areaCount = 0
	private val frozen = FrozenMobs()
	private val freezeSkull = Skulls.texture("FIRE_FREEZE_SKULLS")
	private val sparkSkull = Skulls.texture("THUNDER_SPARK")
	private val skullKeys = arrayOfNulls<ItemStack>(SKULL_CAPACITY)
	private val skullMatched = BooleanArray(SKULL_CAPACITY)
	private var skullHand = 0

	init {
		on<PacketReceiveEvent.Pre> { event ->
			when (val packet = event.packet) {
				is ClientboundSoundPacket -> heard(packet)
				is ClientboundLevelParticlesPacket -> if (swallows(packet)) event.cancelled = true
			}
		}
		on<ServerTickEvent> { swept(ServerClock.ticks) }
		on<WorldRenderEvent> { drew(it) }
		on<EntityRenderEvent> { hid(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onDisabled() = forget()

	override fun onReset() = forget()

	internal fun forget() {
		areas.fill(null)
		areaCount = 0
		skullKeys.fill(null)
		frozen.forget()
	}

	internal fun armed(x: Double, y: Double, z: Double, pitch: Float, tick: Long) {
		val existing = areaAt(x, y, z)
		if (existing != null) {
			existing.refine(pitch, tick)
			return
		}
		if (areaCount == MAX_AREAS) return
		areas[areaCount] = FreezeArea(x, y, z, pitch, tick)
		areaCount++
	}

	private fun triggered(x: Double, y: Double, z: Double, tick: Long) {
		val area = areaAt(x, y, z) ?: return
		drop(area)
		val level = Minecraft.getInstance()?.level ?: return
		for (entity in level.entitiesForRendering()) {
			if (entity !is LivingEntity || !entity.isAlive || !EntityHighlights.isValidEntity(entity)) continue
			if (area.inside(entity.x, entity.z, 0.0)) frozen.freeze(entity, tick)
		}
	}

	private fun swallows(packet: ClientboundLevelParticlesPacket): Boolean {
		if (!customCircle || !SkyBlockLocation.inSkyBlock) return false
		if (packet.particle.type != ParticleTypes.DUST) return false
		if (packet.count != 0 || packet.maxSpeed != DUST_SPEED) return false
		if (packet.xDist.toDouble() != PARTICLE_OFFSET) return false
		if (packet.yDist.toDouble() != PARTICLE_OFFSET || packet.zDist.toDouble() != PARTICLE_OFFSET) return false
		return covering(packet.x, packet.z)
	}

	internal fun covering(x: Double, z: Double): Boolean {
		val tick = ServerClock.ticks
		for (index in 0 until areaCount) {
			val area = areas[index] ?: continue
			if (!area.finished(tick) && area.inside(x, z, PARTICLE_SLACK)) return true
		}
		return false
	}

	private fun heard(packet: ClientboundSoundPacket) {
		if (!SkyBlockLocation.inSkyBlock) return
		val path = packet.sound.value().location().path
		val tick = ServerClock.ticks
		if (path == ARMING_SOUND && packet.volume == ARMING_VOLUME && packet.pitch in MIN_PITCH..MAX_PITCH) {
			if (!sparked(packet.x, packet.y, packet.z)) armed(packet.x, packet.y, packet.z, packet.pitch, tick)
			return
		}
		if (path == TRIGGER_SOUND && packet.volume == TRIGGER_VOLUME && packet.pitch == TRIGGER_PITCH) {
			triggered(packet.x, packet.y, packet.z, tick)
		}
	}

	private fun swept(tick: Long) {
		var index = 0
		while (index < areaCount) {
			val area = areas[index]!!
			if (tick - area.startTick > STALE_TICKS) {
				drop(area)
			} else {
				area.refresh(tick)
				index++
			}
		}
		frozen.sweep(tick)
	}

	private fun drew(event: WorldRenderEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val tick = ServerClock.ticks
		for (index in 0 until areaCount) {
			val area = areas[index] ?: continue
			if (area.finished(tick)) continue
			if (customCircle) {
				WorldDraw.drawWireCircle(event, area.top, RADIUS, circleColor.argb, LINE_WIDTH)
			}
			if (freezeTimer) {
				WorldDraw.drawText(event, area.memo, area.label, area.top, legacyColor(ChatFormatting.AQUA))
			}
		}
		if (!mobTimer && !mobHighlight) return
		frozen.draw(event, mobTimer, mobHighlight)
	}

	private fun hid(event: EntityRenderEvent) {
		if (!customCircle || !SkyBlockLocation.inSkyBlock) return
		val wanted = freezeSkull ?: return
		val stand = event.entity as? ArmorStand ?: return
		if (!stand.isInvisible || !zeroed(stand)) return
		val head = stand.getItemBySlot(EquipmentSlot.HEAD)
		if (head.item != Items.PLAYER_HEAD) return
		if (wearsSkull(head, wanted)) event.cancelled = true
	}

	private fun wearsSkull(head: ItemStack, wanted: String): Boolean {
		val home = skullHome(head)
		for (step in 0 until SKULL_PROBE) {
			val slot = (home + step) and SKULL_MASK
			if (skullKeys[slot] === head) return skullMatched[slot]
			if (skullKeys[slot] == null) return placeSkull(slot, head, wanted)
		}
		return placeSkull((home + (skullHand++ and (SKULL_PROBE - 1))) and SKULL_MASK, head, wanted)
	}

	private fun placeSkull(slot: Int, head: ItemStack, wanted: String): Boolean {
		val matched = SkyBlockItems.skullTexture(head) == wanted
		skullKeys[slot] = head
		skullMatched[slot] = matched
		return matched
	}

	private fun skullHome(head: ItemStack): Int {
		val hash = System.identityHashCode(head)
		return (hash xor (hash ushr 16)) and SKULL_MASK
	}

	private fun sparked(x: Double, y: Double, z: Double): Boolean {
		val wanted = sparkSkull ?: return false
		val level = Minecraft.getInstance()?.level ?: return false
		for (entity in level.entitiesForRendering()) {
			if (entity !is ArmorStand || !nearSpark(entity.x - x, entity.y - y, entity.z - z)) continue
			val held = entity.getItemBySlot(EquipmentSlot.MAINHAND)
			if (held.item != Items.PLAYER_HEAD) continue
			if (SkyBlockItems.skullTexture(held) == wanted) return true
		}
		return false
	}

	internal fun nearSpark(dx: Double, dy: Double, dz: Double): Boolean =
		dx * dx + dy * dy + dz * dz < SPARK_RANGE * SPARK_RANGE

	private fun zeroed(stand: ArmorStand): Boolean {
		val head = stand.headPose
		val body = stand.bodyPose
		return head.x() == 0f && head.y() == 0f && head.z() == 0f &&
			body.x() == 0f && body.y() == 0f && body.z() == 0f
	}

	private fun areaAt(x: Double, y: Double, z: Double): FreezeArea? {
		for (index in 0 until areaCount) {
			val area = areas[index] ?: continue
			if (area.at(x, y, z)) return area
		}
		return null
	}

	private fun drop(area: FreezeArea) {
		for (index in 0 until areaCount) {
			if (areas[index] !== area) continue
			areas[index] = areas[areaCount - 1]
			areas[areaCount - 1] = null
			areaCount--
			return
		}
	}

	internal const val RADIUS = 5.0
	internal const val FREEZE_TICKS = 200L
	internal const val TRIGGER_PITCH = 0.4920635f
	internal const val PARTICLE_OFFSET = 3.921568568330258E-4

	private const val MAX_AREAS = 4
	private const val ARMING_SOUND = "entity.elder_guardian.ambient"
	private const val TRIGGER_SOUND = "block.anvil.land"
	private const val ARMING_VOLUME = 0.2f
	private const val TRIGGER_VOLUME = 0.6f
	private const val MIN_PITCH = 0f
	private const val MAX_PITCH = 2f
	private const val DUST_SPEED = 1f
	private const val PARTICLE_SLACK = 0.5
	private const val LINE_WIDTH = 5f
	private const val CIRCLE_ALPHA = 245
	private const val STALE_TICKS = 40L
	private const val SPARK_RANGE = 2.0
	private const val SKULL_CAPACITY = 64
	private const val SKULL_MASK = SKULL_CAPACITY - 1
	private const val SKULL_PROBE = 4
}

internal class FreezeArea(
	private val x: Double,
	private val y: Double,
	private val z: Double,
	private val firstPitch: Float,
	tick: Long
) {
	internal val top: Vec3 = Vec3(x, y + 1.0, z)
	internal val memo: TextMemo = DhenType.memo()
	internal var startTick = startFrom(firstPitch, tick)
		private set
	internal var label = ""
		private set
	private var known = false

	fun at(otherX: Double, otherY: Double, otherZ: Double): Boolean = otherX == x && otherY == y && otherZ == z

	fun refine(pitch: Float, tick: Long) {
		if (known || pitch == firstPitch) return
		startTick = startFrom(pitch, tick)
		known = true
	}

	fun finished(tick: Long): Boolean = tick - startTick > SETTLE_TICKS

	fun inside(otherX: Double, otherZ: Double, extra: Double): Boolean {
		val dx = otherX - x
		val dz = otherZ - z
		val reach = FireFreeze.RADIUS + extra
		return dx * dx + dz * dz < reach * reach
	}

	fun refresh(tick: Long) {
		label = SNOWFLAKE + tenths(startTick - tick)
	}

	private fun startFrom(pitch: Float, tick: Long): Long =
		tick + ((2.0 * pitch + 1.0) * TICKS_PER_SECOND).toLong()
}

internal class FrozenMobs {
	private var mobs = arrayOfNulls<Entity>(CAPACITY)
	private var expiry = LongArray(CAPACITY)
	private var labels = Array(CAPACITY) { "" }
	private var inks = IntArray(CAPACITY)
	private var memos = Array(CAPACITY) { DhenType.memo() }
	private var count = 0

	fun forget() {
		mobs.fill(null)
		count = 0
	}

	fun freeze(mob: Entity, tick: Long) {
		for (index in 0 until count) {
			if (mobs[index] !== mob) continue
			if (expiry[index] > tick) return
			expiry[index] = tick + FireFreeze.FREEZE_TICKS
			return
		}
		if (count == mobs.size) grow()
		mobs[count] = mob
		expiry[count] = tick + FireFreeze.FREEZE_TICKS
		labels[count] = ""
		count++
	}

	fun sweep(tick: Long) {
		var index = 0
		while (index < count) {
			val mob = mobs[index]
			val left = expiry[index] - tick
			if (mob == null || !mob.isAlive || left <= 0L) {
				drop(index)
			} else {
				labels[index] = SNOWFLAKE + tenths(left)
				inks[index] = inkFor(left)
				index++
			}
		}
	}

	fun draw(event: WorldRenderEvent, text: Boolean, box: Boolean) {
		val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
		for (index in 0 until count) {
			val mob = mobs[index] ?: continue
			if (!mob.isAlive || labels[index].isEmpty()) continue
			val ink = inks[index]
			if (text) {
				WorldDraw.drawText(event, memos[index], labels[index], mob.x - 0.5, mob.y, mob.z - 0.5, ink)
			}
			if (box) {
				EntityHighlights.drawEntityBox(
					event,
					mob,
					partialTick,
					BOX_EXPAND,
					ink,
					ARGB.multiplyAlpha(ink, BOX_ALPHA),
					outline = false,
					fill = true,
					width = 1f,
					depth = WorldDepth.TESTED
				)
			}
		}
	}

	internal fun inkFor(left: Long): Int {
		val seconds = left.toDouble() / TICKS_PER_SECOND
		val redness = (1.0 - ((seconds - HALF_FREEZE_SECONDS) / HALF_FREEZE_SECONDS).coerceIn(0.0, 1.0)).toFloat()
		return ARGB.srgbLerp(redness, legacyColor(ChatFormatting.YELLOW), legacyColor(ChatFormatting.RED))
	}

	private fun drop(index: Int) {
		mobs[index] = mobs[count - 1]
		expiry[index] = expiry[count - 1]
		labels[index] = labels[count - 1]
		inks[index] = inks[count - 1]
		val moved = memos[index]
		memos[index] = memos[count - 1]
		memos[count - 1] = moved
		mobs[count - 1] = null
		count--
	}

	private fun grow() {
		val larger = count * 2
		mobs = mobs.copyOf(larger)
		expiry = expiry.copyOf(larger)
		labels = Array(larger) { if (it < count) labels[it] else "" }
		inks = inks.copyOf(larger)
		memos = Array(larger) { if (it < count) memos[it] else DhenType.memo() }
	}
}

private const val CAPACITY = 16
private const val SETTLE_TICKS = 10L
private const val TICKS_PER_SECOND = 20L
private const val HALF_FREEZE_SECONDS = 5.0
private const val BOX_EXPAND = 0.1
private const val BOX_ALPHA = 0.5f
private const val SNOWFLAKE = "❄ "

private fun tenths(ticks: Long): String {
	if (ticks <= 0L) return ZERO_TIME
	return (ticks / TICKS_PER_SECOND).toString() + '.' + (ticks % TICKS_PER_SECOND / 2) + SECONDS_SUFFIX
}

private const val ZERO_TIME = "0.0s"
private const val SECONDS_SUFFIX = "s"
