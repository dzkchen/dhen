package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal object PetHooks : GuardedHooks<PetHooks.Channels> {
	override val feed = "Pets"

	override val failsafe = Failsafe("Dhen {} failed, its pet state is off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(Util::getMillis)
		subscriptions = arrayOf(
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.styled) },
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it) },
			bus.subscribe<ContainerUpdatedEvent>(BEFORE_FEATURES) { updated(it) },
			bus.subscribe<ContainerClickEvent>(BEFORE_FEATURES) { clicked(it) },
			bus.subscribe<ContainerClosedEvent>(BEFORE_FEATURES) { CurrentPet.deselect() },
			bus.subscribe<TabWidgetUpdateEvent>(BEFORE_FEATURES) { tabbed(it) },
			bus.subscribe<ClientTickEvent.End>(BEFORE_FEATURES) { ticked() },
			bus.subscribe<WorldChangeEvent> { left(it.phase) }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		CurrentPet.reset()
		PetStorage.reset()
	}

	override fun bound() = channels

	private fun chatted(styled: String) = guarded("pet chat") { it.chatted(styled) }

	private fun opened(event: ContainerReadyEvent) = guarded("pets menu") { it.opened(event) }

	private fun updated(event: ContainerUpdatedEvent) = guarded("pet storage") { it.updated(event) }

	private fun clicked(event: ContainerClickEvent) = guarded("pet loadout click") { it.clicked(event) }

	private fun tabbed(event: TabWidgetUpdateEvent) = guarded("pet tab widget") { it.tabbed(event) }

	private fun ticked() = guarded("pet tab delay") { it.ticked() }

	private fun left(phase: WorldChange) {
		if (phase == WorldChange.DISCONNECT) {
			CurrentPet.despawn()
			PetStorage.reset()
		} else CurrentPet.deselect()
	}

	internal class Channels(private val clock: () -> Long = Util::getMillis) {
		fun chatted(styled: String) {
			if (PetLines.despawned(styled)) {
				CurrentPet.despawn()
				return
			}
			val summoned = PetLines.summoned(styled) ?: PetLines.autopetted(styled) ?: return
			CurrentPet.summon(summoned, now = clock())
		}

		fun opened(event: ContainerReadyEvent) {
			val title = withoutCodes(event.title.string)
			PetStorage.observe(title, event.stacks)
			petsMenu(title, event.stacks)
		}

		fun updated(event: ContainerUpdatedEvent) {
			PetStorage.observe(withoutCodes(event.title.string), event.stacks)
		}

		internal fun petsMenu(title: String, stacks: List<ItemStack>) {
			CurrentPet.deselect()
			if (!PetLines.petsMenu(title)) return
			for (index in stacks.indices) {
				val stack = stacks[index]
				if (!despawnable(stack)) continue
				val item = SkyBlockItems.of(stack)
				val info = item.pet
				val hoverName = legacyCodes(stack.hoverName)
				CurrentPet.summon(
					PetLines.withoutLevel(hoverName),
					ownedUuid(stack),
					info,
					stack.copy(),
					PetLines.level(hoverName),
					info?.tier.orEmpty(),
					now = clock()
				)
				CurrentPet.select(index)
				return
			}
			selectRemembered(stacks)
		}

		private fun selectRemembered(stacks: List<ItemStack>) {
			val remembered = CurrentPet.uuid
			if (remembered.isEmpty()) return
			for (index in stacks.indices) {
				val stack = stacks[index]
				if (stack.isEmpty || ownedUuid(stack) != remembered) continue
				CurrentPet.select(index)
				return
			}
		}

		private fun ownedUuid(stack: ItemStack): String {
			val item = SkyBlockItems.of(stack)
			return item.pet?.ownedUuid ?: item.uuid
		}

		fun clicked(event: ContainerClickEvent) {
			val click = event.click
			if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || click.hasShiftDown()) return
			val slot = event.hoveredSlot ?: return
			loadout(withoutCodes(event.screen.title.string), slot.index, slot.item)
		}

		internal fun loadout(title: String, slot: Int, stack: ItemStack) {
			if (slot !in LOADOUT_SLOTS || !title.endsWith(LOADOUTS_TITLE_SUFFIX)) return
			val lore = SkyBlockItems.lore(stack)
			for (index in lore.indices) {
				val line = withoutCodes(lore[index].string)
				if (!line.startsWith(LOADOUT_PET_PREFIX)) continue
				CurrentPet.summon(PetLines.loadoutPet(line) ?: return, now = clock())
				return
			}
		}

		fun tabbed(event: TabWidgetUpdateEvent) {
			if (event.widget != TabWidget.PET) return
			val pet = PetTabLine.read(TabWidgetState.lines(TabWidget.PET), TabWidgetState.stripped(TabWidget.PET))
			if (pet == null) {
				CurrentPet.clearPendingTab()
				return
			}
			CurrentPet.tab(pet.styledName, pet.level, pet.tier, pet.progress, clock())
		}

		fun ticked() {
			CurrentPet.tick(clock())
		}

		private fun despawnable(stack: ItemStack): Boolean {
			if (stack.isEmpty) return false
			val lore = SkyBlockItems.lore(stack)
			if (lore.size < DESPAWN_LINE_DEPTH) return false
			return withoutCodes(lore[lore.size - DESPAWN_LINE_DEPTH].string) == DESPAWN_LINE
		}
	}

	private const val DESPAWN_LINE_DEPTH = 3
	private const val DESPAWN_LINE = "Click to despawn!"
	private const val LOADOUTS_TITLE_SUFFIX = ") Loadouts"
	private const val LOADOUT_PET_PREFIX = "Pet: "

	private val LOADOUT_SLOTS = intArrayOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
}
