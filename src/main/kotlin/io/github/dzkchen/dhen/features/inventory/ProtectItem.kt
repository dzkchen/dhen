package io.github.dzkchen.dhen.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.slotMark
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

internal enum class ProtectType {
	NONE,
	UUID,
	SKYBLOCK_ID,
	STARRED,
	RECOMBOBULATED,
	RARITY
}

object ProtectItem : Module(
	name = "Protect Item",
	category = Category.INVENTORY,
	description = "Refuses to drop or sell the items you mark, and can lock inventory slots so nothing moves them."
) {
	private val rarityOptions = ItemRarity.entries.map { if (it == ItemRarity.NONE) RARITY_OFF else titled(it.loreName) }

	internal val notifySetting = BooleanSetting(
		"Protect Notification",
		default = true,
		description = "Shows a note in the bottom-right corner when Dhen stops an item from leaving."
	)

	internal val protectKeySetting = KeybindSetting(
		"Protect Key",
		GLFW.GLFW_KEY_L,
		"Hold this and click an item in any container to protect or unprotect it."
	)

	internal val indicatorSetting = BooleanSetting(
		"Show Protected Items",
		description = "Draws a small P in the corner of every protected item's slot."
	)

	internal val byUuidSetting = BooleanSetting(
		"Protect UUID",
		default = true,
		description = "Protects the one exact item you marked, even if you own others like it."
	)

	internal val byIdSetting = BooleanSetting(
		"Protect Skyblock ID",
		default = true,
		description = "Protects every item that shares the marked item's SkyBlock id."
	)

	internal val starredSetting = BooleanSetting(
		"Protect Starred",
		default = true,
		description = "Protects anything upgraded with dungeon stars."
	)

	internal val recombobulatedSetting = BooleanSetting(
		"Protect Recombobulated",
		default = true,
		description = "Protects anything a Recombobulator 3000 has been used on."
	)

	internal val raritySetting = SelectorSetting(
		"Protect Rarity",
		RARITY_OFF,
		rarityOptions,
		description = "Protects every item at or above the chosen rarity."
	)

	internal val tripleDropSetting = BooleanSetting(
		"Triple Drop",
		default = true,
		description = "Lets a rarity-protected item through when you press drop three times in a second."
	).withDependency { raritySetting.value != RARITY_OFF }

	internal val lockSlotsSetting = BooleanSetting(
		"Lock Slots",
		description = "Lets you pin an inventory slot so nothing can move the item sitting in it."
	)

	internal val lockKeySetting = KeybindSetting(
		"Lock Key",
		GLFW.GLFW_KEY_K,
		"Hold this and click one of your own inventory slots to lock or unlock it."
	).withDependency { lockSlotsSetting.on }

	internal val lockCornerSetting = SelectorSetting(
		"Lock Corner",
		TOP_RIGHT,
		listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT),
		description = "Which corner of a locked slot carries the marker."
	).withDependency { lockSlotsSetting.on }

	internal val lockedDropSetting = SelectorSetting(
		"Drop Locked With Q",
		IN_DUNGEONS,
		listOf(ALWAYS, IN_DUNGEONS, NEVER),
		description = "When the drop key is still allowed to throw a locked item."
	).withDependency { lockSlotsSetting.on }

	private val markMemo = DhenType.memo()

	private var pressIdentity = ""
	private var pressCount = 0
	private var pressedAt = 0L
	private var blockedAt = 0L

	init {
		for (setting in listOf(
			notifySetting,
			protectKeySetting,
			indicatorSetting,
			byUuidSetting,
			byIdSetting,
			starredSetting,
			recombobulatedSetting,
			raritySetting,
			tripleDropSetting,
			lockSlotsSetting,
			lockKeySetting,
			lockCornerSetting,
			lockedDropSetting
		)) registerSetting(setting)

		on<ContainerClickEvent> { event -> clicked(event) }
		on<ContainerKeyEvent> { event -> pressed(event) }
		on<KeyInputEvent> { event -> droppedInWorld(event) }
		on<TooltipEvent> { event -> annotate(event) }
		on<SlotRenderEvent.Post> { event -> mark(event.graphics, event.slot) }
	}

	internal fun protectionOf(stack: ItemStack): ProtectType {
		if (stack.isEmpty) return ProtectType.NONE
		val item = SkyBlockItems.of(stack)
		if (byUuidSetting.on && item.uuid.isNotEmpty() && item.uuid in ContainerState.protectedUuids) {
			return ProtectType.UUID
		}
		if (byIdSetting.on) {
			val id = identifier(item)
			if (id.isNotEmpty() && id in ContainerState.protectedIds) return ProtectType.SKYBLOCK_ID
		}
		if (starredSetting.on && item.isStarred) return ProtectType.STARRED
		if (recombobulatedSetting.on && item.isRecombobulated) return ProtectType.RECOMBOBULATED
		val floor = ItemRarity.entries[raritySetting.index]
		if (floor == ItemRarity.NONE) return ProtectType.NONE
		val rarity = SkyBlockItems.rarity(stack)
		if (rarity != ItemRarity.NONE && rarity.ordinal >= floor.ordinal) return ProtectType.RARITY
		return ProtectType.NONE
	}

	internal fun pressesLeft(identity: String, now: Long): Int {
		if (identity == pressIdentity && now - pressedAt <= PRESS_WINDOW_MS) {
			pressCount++
		} else {
			pressIdentity = identity
			pressCount = 1
		}
		pressedAt = now
		return REQUIRED_PRESSES - pressCount
	}

	override fun onDisabled() {
		forgetPresses()
	}

	private fun clicked(event: ContainerClickEvent) {
		val slot = event.hoveredSlot
		if (lockSlotsSetting.on && keyHeld(lockKeySetting.code)) {
			event.cancelled = true
			if (ownSlot(slot)) announceLock(ContainerState.toggleLock(slot!!.containerSlot))
			return
		}
		val stack = slot?.item ?: event.screen.menu.carried
		if (keyHeld(protectKeySetting.code) && !stack.isEmpty) {
			event.cancelled = true
			toggleProtection(stack)
			return
		}
		if (lockSlotsSetting.on && ownSlot(slot) && ContainerState.isLocked(slot!!.containerSlot)) {
			event.cancelled = true
			notifyBlocked(NO_PRESSES)
			return
		}
		if (stack.isEmpty || protectionOf(stack) == ProtectType.NONE) return
		if (slot != null && !sellMenu(event.screen)) return
		if (blocks(stack, if (slot == null) CURSOR else SLOT, droppable = slot == null)) event.cancelled = true
	}

	private fun pressed(event: ContainerKeyEvent) {
		val options = Minecraft.getInstance().options
		if (options.keyDrop.matches(event.input)) {
			val slot = event.hoveredSlot ?: return
			if (lockSlotsSetting.on && ownSlot(slot) && ContainerState.isLocked(slot.containerSlot)) {
				event.cancelled = true
				notifyBlocked(NO_PRESSES)
				return
			}
			if (blocks(slot.item, SLOT, droppable = true)) event.cancelled = true
			return
		}
		if (!lockSlotsSetting.on) return
		val hotbar = hotbarKey(event.input)
		if (hotbar < 0) return
		val hovered = event.hoveredSlot
		if (!ContainerState.isLocked(hotbar) && !(ownSlot(hovered) && ContainerState.isLocked(hovered!!.containerSlot))) {
			return
		}
		event.cancelled = true
		notifyBlocked(NO_PRESSES)
	}

	private fun droppedInWorld(event: KeyInputEvent) {
		if (event.action != InputAction.PRESS) return
		val client = Minecraft.getInstance()
		if (client.gui.screen() != null) return
		if (!client.options.keyDrop.matches(boundKey(event.key, event.scancode))) return
		val player = client.player ?: return
		if (lockSlotsSetting.on && ContainerState.isLocked(player.inventory.selectedSlot) && !dropsLocked()) {
			event.cancelled = true
			notifyBlocked(NO_PRESSES)
			return
		}
		if (SkyBlockLocation.island == Island.CATACOMBS) return
		if (blocks(player.inventory.selectedItem, HELD, droppable = true)) event.cancelled = true
	}

	private fun annotate(event: TooltipEvent) {
		if (event.stack.isEmpty || event.lines.isEmpty()) return
		val type = protectionOf(event.stack)
		if (type == ProtectType.NONE) return
		event.edit().add(1, DhenType.styled(tooltipLines[type.ordinal]))
	}

	private fun mark(graphics: GuiGraphicsExtractor, slot: Slot) {
		if (lockSlotsSetting.on && ownSlot(slot) && ContainerState.isLocked(slot.containerSlot)) {
			lockMarker(graphics, slot)
		}
		if (!indicatorSetting.on || slot.item.isEmpty) return
		if (protectionOf(slot.item) == ProtectType.NONE) return
		slotMark(
			graphics,
			Minecraft.getInstance().font,
			PROTECTED_MARK,
			slot.x + MARK_INSET,
			slot.y + MARK_INSET,
			MARK_SCALE,
			DhenPalette.accent,
			markMemo
		)
	}

	private fun lockMarker(graphics: GuiGraphicsExtractor, slot: Slot) {
		val corner = lockCornerSetting.value
		val left = if (corner == TOP_LEFT || corner == BOTTOM_LEFT) slot.x else slot.x + SLOT_BOX - MARKER_SIZE
		val top = if (corner == TOP_LEFT || corner == TOP_RIGHT) slot.y else slot.y + SLOT_BOX - MARKER_SIZE
		RoundedGui.fill(graphics, left, top, left + MARKER_SIZE, top + MARKER_SIZE, MARKER_RADIUS, DhenPalette.accent)
	}

	private fun toggleProtection(stack: ItemStack) {
		val item = SkyBlockItems.of(stack)
		val id = identifier(item)
		if (item.uuid.isEmpty() && id.isEmpty()) {
			push(WARNING_ICON, name, "That item has no id Dhen can protect.")
			return
		}
		val byUuid = item.uuid.isNotEmpty()
		val added = ContainerState.protect(if (byUuid) item.uuid else id, byUuid)
		val label = stack.hoverName.string
		if (added) push(ADDED_ICON, "Protection Added", "Now protecting $label.")
		else push(REMOVED_ICON, "Protection Removed", "No longer protecting $label.")
	}

	private fun announceLock(locked: Boolean) {
		if (locked) push(ADDED_ICON, "Slot Locked", "Nothing can move that slot now.")
		else push(REMOVED_ICON, "Slot Unlocked", "That slot moves normally again.")
	}

	private fun blocks(stack: ItemStack, context: String, droppable: Boolean): Boolean {
		val type = protectionOf(stack)
		if (type == ProtectType.NONE) return false
		if (type != ProtectType.RARITY || !tripleDropSetting.on || !droppable) {
			notifyBlocked(NO_PRESSES)
			return true
		}
		val remaining = pressesLeft(identityOf(stack, context), System.currentTimeMillis())
		if (remaining <= NO_PRESSES) {
			forgetPresses()
			return false
		}
		notifyBlocked(remaining)
		return true
	}

	private fun forgetPresses() {
		pressIdentity = ""
		pressCount = 0
	}

	private fun identityOf(stack: ItemStack, context: String): String {
		val item = SkyBlockItems.of(stack)
		return if (item.uuid.isNotEmpty()) item.uuid else context + identifier(item)
	}

	private fun notifyBlocked(remaining: Int) {
		if (!notifySetting.on) return
		val now = System.currentTimeMillis()
		if (now - blockedAt < BLOCK_NOTICE_GAP_MS) return
		blockedAt = now
		val message = if (remaining <= NO_PRESSES) {
			"This item is protected."
		} else {
			"This item is protected. Press drop $remaining more times to let it go."
		}
		push(BLOCKED_ICON, "Action Blocked", message)
	}

	private fun push(icon: String, title: String, message: String) {
		Notifications.push(icon, title, message)
	}

	internal fun locksSlot(containerSlot: Int): Boolean =
		enabled && lockSlotsSetting.on && ContainerState.isLocked(containerSlot)

	private fun dropsLocked(): Boolean = when (lockedDropSetting.value) {
		ALWAYS -> true
		IN_DUNGEONS -> SkyBlockLocation.island == Island.CATACOMBS
		else -> false
	}

	private fun boundKey(key: Int, scancode: Int): InputConstants.Key =
		if (key == GLFW.GLFW_KEY_UNKNOWN) InputConstants.Type.SCANCODE.getOrCreate(scancode)
		else InputConstants.Type.KEYSYM.getOrCreate(key)

	private fun hotbarKey(key: KeyEvent): Int {
		val slots = Minecraft.getInstance().options.keyHotbarSlots
		for (index in slots.indices) if (slots[index].matches(key)) return index
		return NO_HOTBAR
	}

	private fun ownSlot(slot: Slot?): Boolean = slot != null && slot.container is Inventory

	private fun sellMenu(screen: AbstractContainerScreen<*>): Boolean {
		val slots = screen.menu.slots
		val scanned = minOf(SELL_SCAN, slots.size)
		for (index in 0 until scanned) {
			val stack = slots[index].item
			if (stack.isEmpty) continue
			if (stack.item === Items.HOPPER && stack.hoverName.string.contains(SELL_ITEM)) return true
			for (line in SkyBlockItems.lore(stack)) if (line.string.contains(BUYBACK)) return true
		}
		return false
	}

	private fun identifier(item: SkyBlockItem): String = if (item.pet != null) item.marketId else item.id

	private fun titled(name: String): String =
		name.split(' ').joinToString(" ") { word -> word[0] + word.substring(1).lowercase() }

	private val tooltipLines = arrayOf(
		"",
		"Protected (UUID)",
		"Protected (SkyBlock ID)",
		"Protected (Starred)",
		"Protected (Recombobulated)",
		"Protected (Rarity)"
	)

	internal const val PRESS_WINDOW_MS = 1_000L
	internal const val REQUIRED_PRESSES = 3

	private const val RARITY_OFF = "Off"
	private const val TOP_LEFT = "Top Left"
	private const val TOP_RIGHT = "Top Right"
	private const val BOTTOM_LEFT = "Bottom Left"
	private const val BOTTOM_RIGHT = "Bottom Right"
	private const val ALWAYS = "Always"
	private const val IN_DUNGEONS = "In Dungeons"
	private const val NEVER = "Never"
	private const val PROTECTED_MARK = "P"
	private const val MARK_SCALE = 0.75f
	private const val MARK_INSET = 1
	private const val MARKER_SIZE = 5
	private const val MARKER_RADIUS = 1.5f
	private const val SELL_SCAN = 54
	private const val SELL_ITEM = "Sell Item"
	private const val BUYBACK = "Click to buyback"
	private const val CURSOR = "cursor"
	private const val SLOT = "slot"
	private const val HELD = "held"
	private const val NO_HOTBAR = -1
	private const val NO_PRESSES = 0
	private const val BLOCK_NOTICE_GAP_MS = 1_500L
	private const val BLOCKED_ICON = "⛔"
	private const val WARNING_ICON = "⚠"
	private const val ADDED_ICON = "✔"
	private const val REMOVED_ICON = "✖"
}
