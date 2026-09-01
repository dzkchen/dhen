package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CurrentPet {
	const val NO_SLOT = -1

	private const val SKIN_MARK = "✦"
	private const val TAB_DELAY_MS = 5_000L

	var name: String = ""
		private set

	var bareName: String = ""
		private set

	var uuid: String = ""
		private set

	var menuSlot: Int = NO_SLOT
		private set

	var level: Int = 0
		private set

	var tier: String = ""
		private set

	var progress: Double = Double.NaN
		private set

	var info: PetInfo? = null
		private set

	var stack: ItemStack = ItemStack.EMPTY
		private set

	var revision: Int = 0
		private set

	private var strongAt = Long.MIN_VALUE
	private var pendingName = ""
	private var pendingLevel = 0
	private var pendingTier = ""
	private var pendingProgress = Double.NaN

	val summoned: Boolean get() = name.isNotEmpty()

	internal fun summon(
		styledName: String,
		itemUuid: String = "",
		petInfo: PetInfo? = null,
		itemStack: ItemStack = ItemStack.EMPTY,
		reportedLevel: Int = 0,
		reportedTier: String = "",
		reportedProgress: Double = Double.NaN,
		now: Long = 0L
	) {
		val trimmed = styledName.trim()
		if (trimmed.isEmpty()) return
		val bare = stripOrnaments(trimmed)
		val record = if (petInfo == null) PetStorage.find(bare, reportedTier) else null
		name = record?.styledName ?: trimmed
		bareName = bare
		uuid = itemUuid.ifEmpty { record?.uuid.orEmpty() }
		info = petInfo ?: record?.info
		stack = if (itemStack.isEmpty) record?.stack ?: ItemStack.EMPTY else itemStack
		level = reportedLevel.takeIf { it > 0 } ?: record?.level ?: 0
		tier = reportedTier.ifEmpty { petInfo?.tier ?: record?.info?.tier.orEmpty() }
		progress = reportedProgress
		strongAt = now
		clearPending()
		revision++
	}

	internal fun tab(styledName: String, reportedLevel: Int, reportedTier: String, reportedProgress: Double, now: Long) {
		if (strongAt != Long.MIN_VALUE && now - strongAt <= TAB_DELAY_MS) {
			pendingName = styledName
			pendingLevel = reportedLevel
			pendingTier = reportedTier
			pendingProgress = reportedProgress
			return
		}
		applyTab(styledName, reportedLevel, reportedTier, reportedProgress)
	}

	internal fun tick(now: Long) {
		if (pendingName.isEmpty() || now - strongAt <= TAB_DELAY_MS) return
		applyTab(pendingName, pendingLevel, pendingTier, pendingProgress)
	}

	internal fun clearPendingTab() = clearPending()

	internal fun despawn() {
		name = ""
		bareName = ""
		uuid = ""
		menuSlot = NO_SLOT
		level = 0
		tier = ""
		progress = Double.NaN
		info = null
		stack = ItemStack.EMPTY
		strongAt = Long.MIN_VALUE
		clearPending()
		revision++
	}

	internal fun select(slot: Int) {
		if (menuSlot == slot) return
		menuSlot = slot
		revision++
	}

	internal fun deselect() {
		if (menuSlot == NO_SLOT) return
		menuSlot = NO_SLOT
		revision++
	}

	internal fun reset() = despawn()

	private fun applyTab(styledName: String, reportedLevel: Int, reportedTier: String, reportedProgress: Double) {
		val bare = stripOrnaments(styledName)
		val record = PetStorage.find(bare, reportedTier)
		name = record?.styledName ?: styledName
		bareName = bare
		uuid = record?.uuid.orEmpty()
		info = record?.info
		stack = record?.stack ?: ItemStack(Items.PLAYER_HEAD)
		level = reportedLevel.takeIf { it > 0 } ?: record?.level ?: 0
		tier = reportedTier
		progress = reportedProgress
		clearPending()
		revision++
	}

	private fun clearPending() {
		pendingName = ""
		pendingLevel = 0
		pendingTier = ""
		pendingProgress = Double.NaN
	}

	private fun stripOrnaments(styledName: String): String {
		var text = withoutCodes(styledName).trim()
		if (text.startsWith('[')) {
			val close = text.indexOf(']')
			if (close >= 0) text = text.substring(close + 1).trimStart()
		}
		if (text.endsWith(SKIN_MARK)) text = text.dropLast(SKIN_MARK.length).trimEnd()
		return text
	}
}
