package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SoundSetting
import io.github.dzkchen.dhen.data.Dungeons
import io.github.dzkchen.dhen.data.stats.PlayerStats
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.PacketSendEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.EtherwarpGuess
import io.github.dzkchen.dhen.util.EtherwarpTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object EtherwarpSound : Module(
	name = "Etherwarp Sound",
	category = Category.QOL,
	description = "Replaces the sound Etherwarp makes, and can play it the moment you click instead of when the server answers."
) {
	private var sound by SoundSetting(
		"Sound",
		SoundEvents.EXPERIENCE_ORB_PICKUP,
		"The sound played in place of the dragon hurt noise."
	)

	private var volume by NumberSetting("Volume", 1.0, 0.1, 1.0, 0.01)

	private var pitch by NumberSetting("Pitch", 1.0, 0.1, 2.0, 0.01)

	init {
		registerSetting(ActionSetting("Play Sound", { play() }, "Plays the selected sound once."))
	}

	private var zeroPing by BooleanSetting(
		"Zero-Ping Sound",
		description = "Plays the sound as soon as you click, instead of waiting for the server to send it."
	)

	private val target = EtherwarpTarget()

	private var playedForThisUse = false

	init {
		on<PacketReceiveEvent.Pre> { silence(it) }
		on<PacketSendEvent> { predict(it) }
	}

	internal fun playAheadOfTeleport() {
		if (!enabled || playedForThisUse) return
		play()
		playedForThisUse = true
	}

	private fun play() {
		Minecraft.getInstance().soundManager
			.play(SimpleSoundInstance.forUI(sound, pitch.toFloat(), volume.toFloat()))
	}

	private fun silence(event: PacketReceiveEvent.Pre) {
		val packet = event.packet as? ClientboundSoundPacket ?: return
		if (packet.sound.value() !== SoundEvents.ENDER_DRAGON_HURT || packet.pitch != ETHERWARP_PITCH) return
		event.cancelled = true
		if (!playedForThisUse) play()
		playedForThisUse = false
	}

	private fun predict(event: PacketSendEvent) {
		val packet = event.packet
		if (packet !is ServerboundUseItemPacket && packet !is ServerboundUseItemOnPacket) return
		playedForThisUse = false
		if (!zeroPing) return
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		if (!client.options.keyShift.isDown) return
		if (packet is ServerboundUseItemOnPacket) {
			val clicked = client.level?.getBlockState(packet.hitResult.blockPos)?.block ?: return
			if (clicked !in TILLABLE_BLOCKS) return
		}
		val useYaw = if (packet is ServerboundUseItemPacket) packet.yRot else player.yRot
		val usePitch = if (packet is ServerboundUseItemPacket) packet.xRot else player.xRot
		if (!landsOnTarget(player) || !teleportGoesThrough(player, useYaw, usePitch)) return
		play()
		playedForThisUse = true
	}

	private fun landsOnTarget(player: LocalPlayer): Boolean {
		val item = EtherwarpGuess.etherwarpItem(player.mainHandItem) ?: return false
		EtherwarpGuess.aimedAtTarget(false, EtherwarpGuess.distanceOf(item), target)
		return target.found && target.succeeded
	}

	private fun teleportGoesThrough(player: LocalPlayer, yaw: Float, pitch: Float): Boolean {
		if (player.isPassenger) return false
		val dungeon = Dungeons.context
		if (dungeon != null && dungeon.floorNumber == BLAZE_FLOOR && dungeon.inBossRoom) return false
		if (PlayerStats.mana + PlayerStats.overflowMana < PlayerStats.maxMana * MANA_RESERVE) return false
		if (dungeon?.roomName in TELEPORT_BLOCKING_ROOMS) return false
		val looked = Minecraft.getInstance().hitResult
		if (looked is BlockHitResult && looked.type == HitResult.Type.BLOCK &&
			player.level().getBlockState(looked.blockPos).block in INTERACTABLE_BLOCKS
		) {
			return false
		}
		return !aimedAtNpc(player, yaw, pitch)
	}

	private fun aimedAtNpc(player: LocalPlayer, yaw: Float, pitch: Float): Boolean {
		val level = player.level()
		val start = player.position().add(0.0, player.eyeHeight.toDouble(), 0.0)
		val look = player.calculateViewVector(pitch, yaw)
		val end = start.add(look.scale(NPC_REACH))
		val context = ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
		val blockDistance = level.clip(context).location.distanceTo(start)
		val hit = ProjectileUtil.getEntityHitResult(
			player,
			start,
			end,
			player.boundingBox.expandTowards(look.scale(NPC_REACH)).inflate(1.0),
			{ it !== player && !it.isSpectator },
			Mth.square(blockDistance)
		) ?: return false
		if (labelled(hit.entity)) return true
		return level.getEntities(hit.entity, hit.entity.boundingBox.move(0.0, -1.0, 0.0)) { it is ArmorStand }
			.any(::labelled)
	}

	private fun labelled(entity: Entity): Boolean {
		val name = entity.customName ?: return false
		return withoutCodes(name.string) == CLICKABLE_NPC_LABEL
	}

	private val INTERACTABLE_BLOCKS = setOf(
		Blocks.CHEST,
		Blocks.TRAPPED_CHEST,
		Blocks.ENDER_CHEST,
		Blocks.HOPPER,
		Blocks.CAULDRON,
		Blocks.LEVER,
		Blocks.STONE_BUTTON,
		Blocks.OAK_BUTTON,
		Blocks.OAK_TRAPDOOR,
		Blocks.IRON_TRAPDOOR
	)

	private val TILLABLE_BLOCKS = setOf(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.COARSE_DIRT,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.MOSS_BLOCK,
		Blocks.ROOTED_DIRT
	)

	private val TELEPORT_BLOCKING_ROOMS = setOf("New Trap", "Old Trap", "Teleport Maze", "Boulder")

	internal const val ETHERWARP_PITCH = 0.53968257f
	private const val BLAZE_FLOOR = 7
	private const val MANA_RESERVE = 0.1
	private const val NPC_REACH = 4.0
	private const val CLICKABLE_NPC_LABEL = "CLICK"
}
