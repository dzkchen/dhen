package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.event.withoutCodes

object CurrentPet {
	const val NO_SLOT = -1

	private const val SKIN_MARK = "✦"

	var name: String = ""
		private set

	var bareName: String = ""
		private set

	var uuid: String = ""
		private set

	var menuSlot: Int = NO_SLOT
		private set

	val summoned: Boolean get() = name.isNotEmpty()

	internal fun summon(styledName: String, itemUuid: String = "") {
		val trimmed = styledName.trim()
		if (trimmed.isEmpty()) return
		name = trimmed
		bareName = stripOrnaments(trimmed)
		uuid = itemUuid
	}

	internal fun despawn() {
		name = ""
		bareName = ""
		uuid = ""
		menuSlot = NO_SLOT
	}

	internal fun select(slot: Int) {
		menuSlot = slot
	}

	internal fun deselect() {
		menuSlot = NO_SLOT
	}

	internal fun reset() = despawn()

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
