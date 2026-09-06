package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.item.Skulls
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.trackedName
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.ParticleOverrides
import io.github.dzkchen.dhen.util.ThreadTuning
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.player.Player
import org.slf4j.LoggerFactory
import java.util.WeakHashMap

object RenderOptimizer : Module(
	name = "Render Optimizer",
	category = Category.VISUAL,
	description = "Drops the entities, particles and background work that cost frames and tell you nothing."
) {
	internal val hideStarNamesSetting = BooleanSetting(
		"Hide Star Mobs' Nametag",
		description = "Hides the health nametag above starred dungeon mobs."
	)
	internal val hideNonStarNamesSetting = BooleanSetting(
		"Hide Non Star Mobs' Nametag",
		description = "Hides the health nametag above unstarred dungeon mobs."
	)
	internal val hideFallingBlocksSetting = BooleanSetting("Hide Falling Blocks")
	internal val hideLightningSetting = BooleanSetting(
		"Hide Lightning Bolts",
		description = "Hides the bolt and the sky flash that comes with it."
	)
	internal val hideSoulWeaverSetting = BooleanSetting(
		"Hide Soul Weaver",
		description = "Hides the flying heads from the Soul Weaver gloves."
	)
	internal val hideHealerOrbsSetting = BooleanSetting(
		"Hide Healer Orbs",
		description = "Hides healer support orbs in dungeons, except the damage orb."
	)
	internal val hideLesserOrbsSetting = BooleanSetting(
		"Hide Lesser Orbs",
		description = "Hides the Lesser Orb of Healing and the dust cloud around it."
	)
	internal val hideZeroHealthSetting = BooleanSetting(
		"Hide 0 Health",
		description = "Hides nametags that have run down to zero health."
	)
	internal val hideDeadMobsSetting = BooleanSetting(
		"Hide Dead Mobs",
		description = "Hides the mob death animation."
	)
	internal val hideXpOrbsSetting = BooleanSetting("Hide XP Orbs")
	internal val hideTentaclesSetting = BooleanSetting(
		"Hide P5 Tentacles",
		description = "Hides the Wither King tentacles."
	)
	internal val hideFireSetting = BooleanSetting(
		"Hide Fire On Entities",
		description = "Hides the fire texture on burning mobs."
	)
	internal val hideNewArmorStandsSetting = BooleanSetting(
		"Hide Freshly Spawned Stands",
		description = "Hides blank armour stands for their first half-second, which is when Hypixel leaves them lying around."
	)
	internal val hideDistantSetting = BooleanSetting(
		"Hide Distant Entities",
		description = "Stops drawing mobs and nametags past a set range. Players, withers and dragons are always drawn."
	)
	internal val distantRangeSetting = NumberSetting(
		"Distant Entity Range",
		48.0,
		8.0,
		128.0,
		4.0,
		"Blocks. Anything further away than this stops being drawn."
	).withDependency { hideDistant }
	internal val hideWeatherSetting = BooleanSetting(
		"Hide Weather",
		description = "Stops rain and snow being drawn, and stops their splash particles being spawned."
	)
	internal val freezeTexturesSetting = BooleanSetting(
		"Freeze Animated Textures",
		description = "Stops water, lava, portals and animated pack textures from cycling their frames."
	)
	internal val hideCloudsSetting = BooleanSetting(
		"Hide Clouds Underground",
		description = "Turns clouds off in Dwarven Mines, Crystal Hollows, Mineshaft, Catacombs, Dungeon Hub and Kuudra. " +
			"This overrides your own Clouds video setting while you are on those islands."
	)
	internal val fixParticleColorsSetting = BooleanSetting(
		"Fix Particle Colours",
		description = "Rebuilds the colour Hypixel sends in the old 1.8 way, so tinted particles are the colour they should be."
	)
	internal val particleOverridesSetting = StringSetting(
		"Particle Overrides",
		description = "Per-particle-type rules, separated by spaces. Each is type=off, type=scale, type=scale,opacity " +
			"or type=scale,opacity,#rrggbb. For example: flame=off crit=0.5 dust=1,0.6,#66FFFF"
	)
	internal val unfocusedFrameLimitSetting = NumberSetting(
		"Unfocused Frame Limit",
		0.0,
		0.0,
		60.0,
		1.0,
		"Frames per second while the window is tabbed out. 0 leaves the game's own limit alone."
	)
	internal val unfocusedMuteSetting = BooleanSetting(
		"Mute While Unfocused",
		description = "Silences the game while the window is tabbed out."
	)
	internal val unfocusedNoWorldSetting = BooleanSetting(
		"Skip World While Unfocused",
		description = "Stops drawing the world at all while the window is tabbed out."
	)
	internal val renderPrioritySetting = NumberSetting(
		"Render Thread Priority",
		ThreadTuning.VANILLA.toDouble(),
		Thread.MIN_PRIORITY.toDouble(),
		Thread.MAX_PRIORITY.toDouble(),
		1.0,
		"Operating-system priority of the drawing thread. Higher is not necessarily better — leave it at 5 unless you are testing."
	)
	internal val ioPrioritySetting = NumberSetting(
		"IO Thread Priority",
		ThreadTuning.VANILLA.toDouble(),
		Thread.MIN_PRIORITY.toDouble(),
		Thread.MAX_PRIORITY.toDouble(),
		1.0,
		"Operating-system priority of the file and download threads. Higher is not necessarily better, and only threads " +
			"started after you change it are affected."
	)

	private var hideStarNames by hideStarNamesSetting
	private var hideNonStarNames by hideNonStarNamesSetting
	private var hideFallingBlocks by hideFallingBlocksSetting
	private var hideLightning by hideLightningSetting
	private var hideSoulWeaver by hideSoulWeaverSetting
	private var hideHealerOrbs by hideHealerOrbsSetting
	private var hideLesserOrbs by hideLesserOrbsSetting
	private var hideZeroHealth by hideZeroHealthSetting
	private var hideDeadMobs by hideDeadMobsSetting
	private var hideXpOrbs by hideXpOrbsSetting
	private var hideTentacles by hideTentaclesSetting
	private var hideFire by hideFireSetting
	private var hideNewArmorStands by hideNewArmorStandsSetting
	private var hideDistant by hideDistantSetting
	private var hideWeather by hideWeatherSetting
	private var freezeTextures by freezeTexturesSetting
	private var hideClouds by hideCloudsSetting
	private var fixParticleColors by fixParticleColorsSetting
	private var particleOverrides by particleOverridesSetting
	private var unfocusedMute by unfocusedMuteSetting
	private var unfocusedNoWorld by unfocusedNoWorldSetting

	private val tentacle = Skulls.texture("TENTACLE")
	private val soulWeaver = Skulls.texture("DUNGEONS_SOUL_WEAVER")
	private val abilityOrb = Skulls.texture("DUNGEONS_ABILITY_ORB")
	private val defenseOrb = Skulls.texture("DUNGEONS_SUPPORT_ORB")
	private val lesserOrb = Skulls.texture("LESSER_ORB")
	private val nameVerdicts = WeakHashMap<Entity, NameVerdict>()
	private val lesserOrbs = ArrayList<ArmorStand>()
	private var readOverrides = ""
	private var appliedRenderPriority = ThreadTuning.VANILLA
	private var recolouredFrom: ParticleOptions? = null
	private var recolouredTo: ParticleOptions? = null
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	init {
		on<PacketReceiveEvent.Pre> { received(it) }
		on<EntityRenderEvent> { rendering(it) }
		on<ClientTickEvent.End> { republish() }
		on<WorldChangeEvent> { lesserOrbs.clear() }
	}

	override fun onDisabled() {
		nameVerdicts.clear()
		lesserOrbs.clear()
		recolouredFrom = null
		recolouredTo = null
		readOverrides = ""
		ParticleOverrides.rules = emptyMap()
		ThreadTuning.publishIo(ThreadTuning.VANILLA)
		applyRenderPriority(ThreadTuning.VANILLA)
	}

	@JvmStatic
	fun hidesFireOnEntities(): Boolean = enabled && hideFire

	@JvmStatic
	fun hidesWeather(): Boolean = enabled && hideWeather

	@JvmStatic
	fun freezesTextureAnimation(): Boolean = enabled && freezeTextures

	@JvmStatic
	fun hidesClouds(): Boolean = enabled && hideClouds && SkyBlockLocation.island in CLOUDLESS_ISLANDS

	@JvmStatic
	fun mutesUnfocused(): Boolean = enabled && unfocusedMute && unfocused()

	@JvmStatic
	fun refreshVolumeOnFocusChange() {
		if (!enabled || !unfocusedMute) return
		val sounds = Minecraft.getInstance().soundManager ?: return
		try {
			sounds.refreshCategoryVolume(SoundSource.MASTER)
		} catch (exception: RuntimeException) {
			log.warn("Could not refresh sound volume after a window focus change", exception)
		}
	}

	@JvmStatic
	fun skipsUnfocusedWorld(): Boolean = enabled && unfocusedNoWorld && unfocused()

	@JvmStatic
	fun unfocusedFrameCap(): Int = if (enabled && unfocused()) unfocusedFrameLimitSetting.amount.toInt() else 0

	@JvmStatic
	fun recolouredParticle(
		particle: ParticleOptions,
		packet: ClientboundLevelParticlesPacket
	): ParticleOptions {
		if (!enabled || !fixParticleColors || !SkyBlockLocation.inSkyBlock) return particle
		if (particle !is ColorParticleOption) return particle
		val cached = recolouredTo
		if (particle === recolouredFrom && cached != null) return cached
		val replacement = ColorParticleOption.create(
			particle.type,
			ARGB.colorFromFloat(particle.alpha, 1f - packet.xDist, 1f - packet.zDist, 1f - packet.yDist)
		)
		recolouredFrom = particle
		recolouredTo = replacement
		return replacement
	}

	private fun unfocused(): Boolean = !Minecraft.getInstance().window.isFocused

	private fun republish() {
		if (particleOverrides != readOverrides) {
			readOverrides = particleOverrides
			ParticleOverrides.rules = ParticleOverrides.read(particleOverrides)
		}
		ThreadTuning.publishIo(ioPrioritySetting.amount.toInt())
		applyRenderPriority(renderPrioritySetting.amount.toInt())
	}

	private fun applyRenderPriority(priority: Int) {
		if (priority == appliedRenderPriority || !Minecraft.getInstance().isSameThread) return
		appliedRenderPriority = priority
		Thread.currentThread().priority = priority
	}

	private fun received(event: PacketReceiveEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		when (val packet = event.packet) {
			is ClientboundSetEntityDataPacket -> if (discardsNamed(packet)) {
				discard(packet.id)
				event.cancelled = true
			}

			is ClientboundAddEntityPacket -> if (discardsSpawn(packet.type)) event.cancelled = true

			is ClientboundSetEquipmentPacket -> {
				if (discardsWorn(packet)) discard(packet.entity)
				trackLesserOrb(packet)
			}

			is ClientboundLevelParticlesPacket -> if (swallowsOrbDust(packet)) event.cancelled = true

			else -> Unit
		}
	}

	private fun discardsNamed(packet: ClientboundSetEntityDataPacket): Boolean {
		val player = Minecraft.getInstance().player ?: return false
		if (packet.id == player.id) return false
		val name = packet.packedItems.firstNotNullOfOrNull(::trackedName) ?: return false
		val formatted = legacyCodes(name)
		if (hideZeroHealth && ZERO_HEALTH.any { it.matches(formatted) }) return true
		if (!hideHealerOrbs) return false
		val plain = withoutCodes(formatted)
		return ORB_LABELS.any { plain.startsWith(it) }
	}

	private fun discardsSpawn(type: EntityType<*>): Boolean = when (type) {
		EntityTypes.FALLING_BLOCK -> hideFallingBlocks
		EntityTypes.LIGHTNING_BOLT -> hideLightning
		EntityTypes.EXPERIENCE_ORB -> hideXpOrbs
		else -> false
	}

	private fun discardsWorn(packet: ClientboundSetEquipmentPacket): Boolean {
		if (SkyBlockLocation.island != Island.CATACOMBS) return false
		for (worn in packet.slots) {
			if (worn.first != EquipmentSlot.HEAD) continue
			val texture = SkyBlockItems.skullTexture(worn.second) ?: continue
			if (hideTentacles && texture == tentacle) return true
			if (hideSoulWeaver && texture == soulWeaver) return true
			if (hideHealerOrbs && (texture == abilityOrb || texture == defenseOrb)) return true
		}
		return false
	}

	private fun trackLesserOrb(packet: ClientboundSetEquipmentPacket) {
		if (lesserOrb == null) return
		for (worn in packet.slots) {
			if (worn.first != EquipmentSlot.MAINHAND) continue
			if (SkyBlockItems.skullTexture(worn.second) != lesserOrb) continue
			val stand = Minecraft.getInstance().level?.getEntity(packet.entity) as? ArmorStand ?: return
			if (!lesserOrbs.contains(stand)) lesserOrbs.add(stand)
			return
		}
	}

	private fun swallowsOrbDust(packet: ClientboundLevelParticlesPacket): Boolean {
		if (!hideLesserOrbs || lesserOrbs.isEmpty()) return false
		if (packet.particle.type != ParticleTypes.DUST) return false
		for (index in lesserOrbs.indices) {
			val orb = lesserOrbs[index]
			val dx = orb.x - packet.x
			val dy = orb.y - packet.y
			val dz = orb.z - packet.z
			if (dx * dx + dy * dy + dz * dz < ORB_DUST_RANGE) return true
		}
		return false
	}

	private fun discard(entityId: Int) {
		Minecraft.getInstance().level?.getEntity(entityId)?.remove(Entity.RemovalReason.DISCARDED)
	}

	private fun rendering(event: EntityRenderEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val entity = event.entity
		if (hideDeadMobs && (!entity.isAlive || (entity as? LivingEntity)?.health?.let { it <= 0f } == true)) {
			event.cancelled = true
			return
		}
		if (entity is ArmorStand) {
			if (hideNewArmorStands && freshlySpawned(entity)) {
				event.cancelled = true
				return
			}
			if (hideLesserOrbs && trackedOrb(entity)) {
				event.cancelled = true
				return
			}
		}
		if (hideDistant && tooFar(entity)) {
			event.cancelled = true
			return
		}
		if (SkyBlockLocation.island != Island.CATACOMBS) return
		if (!hideStarNames && !hideNonStarNames) return
		val name = entity.customName ?: return
		val verdict = nameVerdicts.getOrPut(entity) {
			val formatted = legacyCodes(name)
			NameVerdict(STAR in formatted, formatted.endsWith(HEALTH_SUFFIX))
		}
		if (!verdict.healthTag) return
		if (if (verdict.starred) hideStarNames else hideNonStarNames) event.cancelled = true
	}

	private fun freshlySpawned(stand: ArmorStand): Boolean {
		if (stand.tickCount >= NEW_STAND_TICKS || stand.customName != null) return false
		for (index in EQUIPMENT_SLOTS.indices) {
			if (!stand.getItemBySlot(EQUIPMENT_SLOTS[index]).isEmpty) return false
		}
		return true
	}

	private fun trackedOrb(stand: ArmorStand): Boolean {
		var index = 0
		var hidden = false
		while (index < lesserOrbs.size) {
			val tracked = lesserOrbs[index]
			if (tracked.isRemoved) {
				lesserOrbs.removeAt(index)
				continue
			}
			if (tracked === stand) hidden = true
			index++
		}
		return hidden
	}

	private fun tooFar(entity: Entity): Boolean {
		if (entity is Player || entity is WitherBoss || entity is EnderDragon) return false
		if (alwaysDrawnHere(entity)) return false
		val player = Minecraft.getInstance().player ?: return false
		val dx = entity.x - player.x
		val dy = entity.y - player.y
		val dz = entity.z - player.z
		val range = distantRangeSetting.amount
		return dx * dx + dy * dy + dz * dz > range * range
	}

	private fun alwaysDrawnHere(entity: Entity): Boolean = when (SkyBlockLocation.island) {
		Island.DWARVEN_MINES -> entity is Ghast || entity is IronGolem
		Island.JERRYS_WORKSHOP -> entity is MagmaCube
		else -> false
	}

	private class NameVerdict(val starred: Boolean, val healthTag: Boolean)

	private val ZERO_HEALTH = listOf(
		Regex("""^§.\[§.Lv\d+§.] §.+ (?:§.)+0§f/.+§c❤$"""),
		Regex("""^.+ (?:§.)+0§c❤$""")
	)
	private val ORB_LABELS = listOf("DEFENSE", "ABILITY DAMAGE")
	private val EQUIPMENT_SLOTS = EquipmentSlot.values()
	private val CLOUDLESS_ISLANDS = setOf(
		Island.DWARVEN_MINES,
		Island.CRYSTAL_HOLLOWS,
		Island.MINESHAFT,
		Island.CATACOMBS,
		Island.DUNGEON_HUB,
		Island.KUUDRA
	)
	private const val STAR = '✯'
	private const val HEALTH_SUFFIX = "§c❤"
	private const val NEW_STAND_TICKS = 10
	private const val ORB_DUST_RANGE = 16.0
}
