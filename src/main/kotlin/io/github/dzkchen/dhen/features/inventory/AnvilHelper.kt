package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.pickup.PickupHooks
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

object AnvilHelper : Module(
	name = "Anvil Combine Helper",
	category = Category.INVENTORY,
	description = "Paints your own copies of whatever sits in one side of the anvil, so you can see what combines."
) {
	private val slots = SkyBlockItems.memo(MENU_SLOTS)

	internal var inAnvil = false
		private set
	internal var leftId = ""
		private set
	internal var rightId = ""
		private set

	init {
		on<ContainerReadyEvent> { entered(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { entered(it.title.string, it.stacks) }
		on<ContainerClickEvent> { if (inAnvil) inTicks(1) { reread() } }
		on<ContainerClosedEvent> { closed() }
		on<SlotRenderEvent.Pre> { painted(it) }
	}

	override fun onDisabled() = forget()

	internal fun entered(title: String, stacks: List<ItemStack>) {
		if (withoutCodes(title) != ANVIL) {
			forget()
			return
		}
		inAnvil = true
		leftId = idOf(stacks.getOrNull(LEFT_SLOT))
		rightId = idOf(stacks.getOrNull(RIGHT_SLOT))
	}

	internal fun wanted(): String = when {
		!inAnvil -> ""
		leftId.isEmpty() == rightId.isEmpty() -> ""
		leftId.isEmpty() -> rightId
		else -> leftId
	}

	private fun forget() {
		inAnvil = false
		leftId = ""
		rightId = ""
	}

	private fun closed() {
		if (!inAnvil) return
		if (leftId.isNotEmpty()) PickupHooks.ignore(leftId, RETURN_MILLIS)
		if (rightId.isNotEmpty()) PickupHooks.ignore(rightId, RETURN_MILLIS)
		forget()
	}

	private fun reread() {
		if (!inAnvil) return
		val menu = Minecraft.getInstance()?.player?.containerMenu ?: return
		if (menu.slots.size <= RIGHT_SLOT) return
		leftId = idOf(menu.slots[LEFT_SLOT].item)
		rightId = idOf(menu.slots[RIGHT_SLOT].item)
	}

	private fun painted(event: SlotRenderEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		val target = wanted()
		if (target.isEmpty()) return
		val slot = event.slot
		if (slot.container !is Inventory || slot.item.isEmpty) return
		if (slots.of(slot.index, slot.item).id != target) return
		SlotTint.claim(legacyColor(ChatFormatting.GREEN), TINT_PRIORITY)
	}

	private fun idOf(stack: ItemStack?): String =
		if (stack == null || stack.isEmpty) "" else SkyBlockItems.of(stack).id

	internal const val LEFT_SLOT = 29
	internal const val RIGHT_SLOT = 33

	private const val ANVIL = "Anvil"
	private const val MENU_SLOTS = 128
	private const val RETURN_MILLIS = 3_000L
	private const val TINT_PRIORITY = 15
}
