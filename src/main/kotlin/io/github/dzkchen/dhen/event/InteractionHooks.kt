package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult

internal object InteractionHooks {
	private val failsafe = Failsafe("Dhen {} failed, its interaction events are off until restart")

	@Volatile
	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	fun uninstall() {
		channels = null
	}

	fun active(): Boolean = channels != null

	fun useBlock(player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult =
		interaction(player, "use block") { it.useBlock(player, hand, hit.blockPos) }

	fun useEntity(player: Player, hand: InteractionHand, entity: Entity): InteractionResult =
		interaction(player, "use entity") { it.useEntity(player, hand, entity) }

	fun useItem(player: Player, hand: InteractionHand): InteractionResult =
		interaction(player, "use item") { it.useItem(player, hand) }

	fun attackBlock(player: Player, pos: BlockPos): InteractionResult =
		interaction(player, "attack block") { it.attackBlock(pos) }

	fun attackEntity(player: Player, entity: Entity): InteractionResult =
		interaction(player, "attack entity") { it.attackEntity(entity) }

	fun attack(): Boolean = guarded("attack") { it.attack() }

	private inline fun interaction(player: Player, label: String, block: (Channels) -> Boolean): InteractionResult =
		if (player.isLocalPlayer && guarded(label, block)) InteractionResult.FAIL else InteractionResult.PASS

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			this.channels = null
			failsafe.fail(label, throwable)
			false
		}
	}

	private class Channels(bus: EventBus) {
		private val useBlocks = bus.type<InteractionEvent.UseBlock>()
		private val useEntities = bus.type<InteractionEvent.UseEntity>()
		private val useItems = bus.type<InteractionEvent.UseItem>()
		private val attackBlocks = bus.type<InteractionEvent.AttackBlock>()
		private val attackEntities = bus.type<InteractionEvent.AttackEntity>()
		private val attacks = bus.type<InteractionEvent.Attack>()

		fun useBlock(player: Player, hand: InteractionHand, pos: BlockPos): Boolean =
			useBlocks.publish { InteractionEvent.UseBlock(pos, hand, player.getItemInHand(hand)) }

		fun useEntity(player: Player, hand: InteractionHand, entity: Entity): Boolean =
			useEntities.publish { InteractionEvent.UseEntity(entity, hand, player.getItemInHand(hand)) }

		fun useItem(player: Player, hand: InteractionHand): Boolean =
			useItems.publish { InteractionEvent.UseItem(hand, player.getItemInHand(hand)) }

		fun attackBlock(pos: BlockPos): Boolean = attackBlocks.publish { InteractionEvent.AttackBlock(pos) }

		fun attackEntity(entity: Entity): Boolean =
			attackEntities.publish { InteractionEvent.AttackEntity(entity) }

		fun attack(): Boolean = attacks.publish { InteractionEvent.Attack() }
	}
}

private inline fun <T : InteractionEvent> EventBus.EventType<T>.publish(event: () -> T): Boolean {
	if (!hasSubscribers) return false
	return event().also { dispatch(it) }.cancelled
}
