package io.github.dzkchen.dhen.data

enum class Island(val modeId: String?) {
	NONE(null),
	UNKNOWN(null),
	PRIVATE_ISLAND("dynamic"),
	PRIVATE_ISLAND_GUEST(null),
	HUB("hub"),
	DARK_AUCTION("dark_auction"),
	JERRYS_WORKSHOP("winter"),
	THE_FARMING_ISLANDS("farming_1"),
	GARDEN("garden"),
	GARDEN_GUEST(null),
	GOLD_MINE("mining_1"),
	DEEP_CAVERNS("mining_2"),
	DWARVEN_MINES("mining_3"),
	CRYSTAL_HOLLOWS("crystal_hollows"),
	MINESHAFT("mineshaft"),
	BACKWATER_BAYOU("fishing_1"),
	LOTUS_ATOLL("lotus_atoll"),
	THE_PARK("foraging_1"),
	MOONGLADE_MARSH("foraging_2"),
	TORRHUS_CANYON("foraging_3"),
	SPIDERS_DEN("combat_1"),
	THE_END("combat_3"),
	CRIMSON_ISLE("crimson_isle"),
	DUNGEON_HUB("dungeon_hub"),
	CATACOMBS("dungeon"),
	KUUDRA("kuudra"),
	THE_RIFT("rift"),
	CRITTER_SAFARI("safari");

	val guest: Island?
		get() = when (this) {
			PRIVATE_ISLAND -> PRIVATE_ISLAND_GUEST
			GARDEN -> GARDEN_GUEST
			else -> null
		}

	companion object {
		private val byModeId: Map<String, Island> =
			entries.mapNotNull { island -> island.modeId?.let { it to island } }.toMap()

		fun ofMode(modeId: String): Island = byModeId[modeId] ?: UNKNOWN
	}
}
