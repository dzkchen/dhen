package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.trackedName
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import java.util.WeakHashMap

object RenderOptimizer : Module(
	name = "Render Optimizer",
	category = Category.VISUAL,
	description = "Hides entities and nametags that cost frames and tell you nothing."
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
	internal val hideLightningSetting = BooleanSetting("Hide Lightning Bolts")
	internal val hideSoulWeaverSetting = BooleanSetting(
		"Hide Soul Weaver",
		description = "Hides the flying heads from the Soul Weaver gloves."
	)
	internal val hideHealerOrbsSetting = BooleanSetting(
		"Hide Healer Orbs",
		description = "Hides healer support orbs in dungeons, except the damage orb."
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

	private var hideStarNames by hideStarNamesSetting
	private var hideNonStarNames by hideNonStarNamesSetting
	private var hideFallingBlocks by hideFallingBlocksSetting
	private var hideLightning by hideLightningSetting
	private var hideSoulWeaver by hideSoulWeaverSetting
	private var hideHealerOrbs by hideHealerOrbsSetting
	private var hideZeroHealth by hideZeroHealthSetting
	private var hideDeadMobs by hideDeadMobsSetting
	private var hideXpOrbs by hideXpOrbsSetting
	private var hideTentacles by hideTentaclesSetting
	private var hideFire by hideFireSetting

	private val nameVerdicts = WeakHashMap<Entity, NameVerdict>()

	init {
		on<PacketReceiveEvent.Pre> { received(it) }
		on<EntityRenderEvent> { rendering(it) }
	}

	override fun onDisabled() {
		nameVerdicts.clear()
	}

	@JvmStatic
	fun hidesFireOnEntities(): Boolean = enabled && hideFire

	private fun received(event: PacketReceiveEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		when (val packet = event.packet) {
			is ClientboundSetEntityDataPacket -> if (discardsNamed(packet)) {
				discard(packet.id)
				event.cancelled = true
			}

			is ClientboundAddEntityPacket -> if (discardsSpawn(packet.type)) event.cancelled = true

			is ClientboundSetEquipmentPacket -> if (discardsWorn(packet)) discard(packet.entity)

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
			if (hideTentacles && texture == TENTACLE_TEXTURE) return true
			if (hideSoulWeaver && texture == SOUL_WEAVER_TEXTURE) return true
			if (hideHealerOrbs && (texture == ABILITY_ORB_TEXTURE || texture == DEFENSE_ORB_TEXTURE)) return true
		}
		return false
	}

	private fun discard(entityId: Int) {
		Minecraft.getInstance().level?.getEntity(entityId)?.remove(Entity.RemovalReason.DISCARDED)
	}

	private fun rendering(event: EntityRenderEvent) {
		val entity = event.entity
		if (hideDeadMobs && (!entity.isAlive || (entity as? LivingEntity)?.health?.let { it <= 0f } == true)) {
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

	private class NameVerdict(val starred: Boolean, val healthTag: Boolean)

	private val ZERO_HEALTH = listOf(
		Regex("""^§.\[§.Lv\d+§.] §.+ (?:§.)+0§f/.+§c❤$"""),
		Regex("""^.+ (?:§.)+0§c❤$""")
	)
	private val ORB_LABELS = listOf("DEFENSE", "ABILITY DAMAGE")
	private const val STAR = '✯'
	private const val HEALTH_SUFFIX = "§c❤"
	private const val TENTACLE_TEXTURE =
		"ewogICJ0aW1lc3RhbXAiIDogMTcxOTg1NzI3NzI0OSwKICAicHJvZmlsZUlkIiA6ICIxODA1Y2E2MmM0ZDI0M2NiOWQxYmY4YmM5N2E1YjgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJSdWxsZWQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdkODM2NzQ5MjZiODk3MTRlNmI1YTU1NDcwNTAxYzA0YjA2NmRkODdiZjZjMzM1Y2RkYzZlNjBhMWExYTVmNSIKICAgIH0KICB9Cn0="
	private const val SOUL_WEAVER_TEXTURE =
		"eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ=="
	private const val ABILITY_ORB_TEXTURE =
		"ewogICJ0aW1lc3RhbXAiIDogMTYzODUyNDAzODE5OCwKICAicHJvZmlsZUlkIiA6ICIzOWEzOTMzZWE4MjU0OGU3ODQwNzQ1YzBjNGY3MjU2ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZW1pbmVjcmFmdGVybG9sIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVlZTRiYjQ4MjFkMGY1ZWQ4NjVjMjEwOTBhODBiNWVlN2Q1MjI2ODQ3NmVlMjVkMzg5NzEwZjdjYzlmMTEwZDYiCiAgICB9CiAgfQp9"
	private const val DEFENSE_ORB_TEXTURE =
		"ewogICJ0aW1lc3RhbXAiIDogMTYwNTM1NjUyNzQzOSwKICAicHJvZmlsZUlkIiA6ICJhYTZhNDA5NjU4YTk0MDIwYmU3OGQwN2JkMzVlNTg5MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiejE0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE1NzhiNGFmM2ZkZDkxNTFiODUwYjEzYzY3YzQ1ODAyMjRjN2Y2MDA1MjcxM2YyZDE1MWY3YzE1ZGMwZDdiMzQiCiAgICB9CiAgfQp9"
}
