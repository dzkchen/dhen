package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.PacketSendEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.EtherwarpGuess
import io.github.dzkchen.dhen.util.EtherwarpTarget
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.phys.Vec3

object ZeroPingEtherwarp : Module(
	name = "Zero-Ping Etherwarp",
	category = Category.QOL,
	description = "Moves you to the Etherwarp target the moment you click, ahead of the server. Private island only."
) {
	private val target = EtherwarpTarget()

	init {
		on<PacketSendEvent> { sent(it) }
	}

	override fun onEnabled() {
		Dhen.automationNotice.announce(name)
	}

	private fun sent(event: PacketSendEvent) {
		if (event.packet !is ServerboundUseItemPacket) return
		if (SkyBlockLocation.island != Island.PRIVATE_ISLAND) return
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val item = EtherwarpGuess.etherwarpItem(player.mainHandItem) ?: return
		if (EtherwarpGuess.requiresSneak(item) && !client.options.keyShift.isDown) return
		EtherwarpGuess.aimedAtTarget(false, EtherwarpGuess.distanceOf(item), target)
		if (!target.found || !target.succeeded) return

		val x = target.x + BLOCK_CENTER
		val y = target.y + LANDING_HEIGHT
		val z = target.z + BLOCK_CENTER
		player.connection.send(ServerboundMovePlayerPacket.PosRot(x, y, z, player.yRot, player.xRot, false, false))
		player.setPos(x, y, z)
		player.deltaMovement = Vec3.ZERO
		EtherwarpSound.playAheadOfTeleport()
	}

	private const val BLOCK_CENTER = 0.5
	private const val LANDING_HEIGHT = 1.05
}
