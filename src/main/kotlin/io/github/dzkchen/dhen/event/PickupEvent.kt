package io.github.dzkchen.dhen.event

enum class PickupSource {
	INVENTORY,
	SACK
}

class ItemPickupEvent internal constructor(
	val id: String,
	val name: String,
	val uuid: String,
	val createdAt: Long,
	val delta: Int,
	val source: PickupSource
) : Event

class PurseChangeEvent internal constructor(val delta: Long) : Event
