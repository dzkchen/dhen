package io.github.dzkchen.dhen.data

enum class Island(val modeId: String?, val displayName: String?) {
	NONE(null, null),
	UNKNOWN(null, null),
	PRIVATE_ISLAND("dynamic", "Private Island"),
	PRIVATE_ISLAND_GUEST(null, "Private Island Guest"),
	HUB("hub", "Hub"),
	DARK_AUCTION("dark_auction", "Dark Auction"),
	JERRYS_WORKSHOP("winter", "Jerry's Workshop"),
	THE_FARMING_ISLANDS("farming_1", "The Farming Islands"),
	GARDEN("garden", "Garden"),
	GARDEN_GUEST(null, "Garden Guest"),
	GOLD_MINE("mining_1", "Gold Mine"),
	DEEP_CAVERNS("mining_2", "Deep Caverns"),
	DWARVEN_MINES("mining_3", "Dwarven Mines"),
	CRYSTAL_HOLLOWS("crystal_hollows", "Crystal Hollows"),
	MINESHAFT("mineshaft", "Mineshaft"),
	BACKWATER_BAYOU("fishing_1", "Backwater Bayou"),
	LOTUS_ATOLL("lotus_atoll", "Lotus Atoll"),
	THE_PARK("foraging_1", "The Park"),
	MOONGLADE_MARSH("foraging_2", "Galatea"),
	TORRHUS_CANYON("foraging_3", "Torrhus Canyon"),
	SPIDERS_DEN("combat_1", "Spider"),
	THE_END("combat_3", "The End"),
	CRIMSON_ISLE("crimson_isle", "Crimson Isle"),
	DUNGEON_HUB("dungeon_hub", "Dungeon Hub"),
	CATACOMBS("dungeon", "Catacombs"),
	KUUDRA("kuudra", "Kuudra"),
	THE_RIFT("rift", "The Rift"),
	CRITTER_SAFARI("safari", "Safari");

	val guest: Island?
		get() = when (this) {
			PRIVATE_ISLAND -> PRIVATE_ISLAND_GUEST
			GARDEN -> GARDEN_GUEST
			else -> null
		}

	val guestHost: Island?
		get() = when (this) {
			PRIVATE_ISLAND_GUEST -> PRIVATE_ISLAND
			GARDEN_GUEST -> GARDEN
			else -> null
		}

	companion object {
		private val byModeId: Map<String, Island> =
			entries.mapNotNull { island -> island.modeId?.let { it to island } }.toMap()

		fun ofMode(modeId: String): Island = byModeId[modeId] ?: UNKNOWN
	}
}
