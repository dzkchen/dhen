package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.world.item.ItemStack

internal object PetStorage {
	private const val EXP_SHARING = "Exp Sharing"
	private const val FIRST_EXP_SHARE_SLOT = 30
	private const val EXP_SHARE_SLOTS = 3

	private val pets = LinkedHashMap<String, PetRecord>()
	private val expShare = arrayOfNulls<String>(EXP_SHARE_SLOTS)

	var revision = 0
		private set

	fun observe(title: String, stacks: List<ItemStack>) {
		when {
			PetLines.petsMenu(title) -> pets(stacks)
			title == EXP_SHARING -> expShare(stacks)
		}
	}

	fun find(name: String, tier: String): PetRecord? {
		for (pet in pets.values) {
			if (pet.bareName == name && (tier.isEmpty() || pet.info.tier == tier)) return pet
		}
		return null
	}

	fun expShare(index: Int): PetRecord? {
		if (index !in expShare.indices) return null
		return expShare[index]?.let(pets::get)
	}

	fun expShareActive(index: Int, sharingIsCaring: Boolean): Boolean = index == 0 || sharingIsCaring

	fun reset() {
		if (pets.isEmpty() && expShare.all { it == null }) return
		pets.clear()
		expShare.fill(null)
		revision++
	}

	private fun pets(stacks: List<ItemStack>) {
		var changed = false
		for (index in stacks.indices) {
			if (index !in 10..43 || index % 9 == 0 || (index + 1) % 9 == 0) continue
			val record = record(stacks[index]) ?: continue
			if (replace(record)) changed = true
		}
		if (changed) revision++
	}

	private fun expShare(stacks: List<ItemStack>) {
		var changed = false
		for (index in expShare.indices) {
			val stack = stacks.getOrNull(FIRST_EXP_SHARE_SLOT + index)
			val record = stack?.takeUnless(ItemStack::isEmpty)?.let(::record)
			val uuid = record?.uuid
			if (record != null && replace(record)) changed = true
			if (expShare[index] != uuid) {
				expShare[index] = uuid
				changed = true
			}
		}
		if (changed) revision++
	}

	private fun record(stack: ItemStack): PetRecord? {
		val info = SkyBlockItems.of(stack).pet ?: return null
		val uuid = info.ownedUuid ?: return null
		val hoverName = legacyCodes(stack.hoverName)
		val styledName = PetLines.withoutLevel(hoverName)
		return PetRecord(
			uuid,
			withoutCodes(styledName).trim(),
			styledName,
			PetLines.level(hoverName),
			info,
			stack.copy()
		)
	}

	private fun replace(record: PetRecord): Boolean {
		val previous = pets[record.uuid]
		if (previous != null && previous.same(record)) return false
		pets[record.uuid] = record
		return true
	}
}

internal class PetRecord(
	val uuid: String,
	val bareName: String,
	val styledName: String,
	val level: Int,
	val info: PetInfo,
	val stack: ItemStack
) {
	fun same(other: PetRecord): Boolean =
		uuid == other.uuid && bareName == other.bareName && styledName == other.styledName && level == other.level &&
			info.type == other.info.type && info.tier == other.info.tier && info.exp == other.info.exp &&
			info.heldItem == other.info.heldItem && info.candyUsed == other.info.candyUsed && info.skin == other.info.skin &&
			ItemStack.matches(stack, other.stack)
}
