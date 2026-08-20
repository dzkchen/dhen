package io.github.dzkchen.dhen.data.repo

internal object ConstantsFixture {
	const val REFORGE_STONES = """
		{
		  "SPIRIT_STONE": {
		    "internalName": "SPIRIT_STONE",
		    "reforgeName": "Spiritual",
		    "nbtModifier": "spiritual",
		    "reforgeCosts": {"COMMON": 10000, "UNCOMMON": 20000, "RARE": 50000, "EPIC": 75000, "LEGENDARY": 100000, "MYTHIC": 150000, "DIVINE": 200000}
		  },
		  "BEADY_EYES": {
		    "internalName": "BEADY_EYES",
		    "reforgeName": "Beady Eyes!",
		    "reforgeCosts": {"EPIC": 75000}
		  },
		  "NAMELESS_STONE": {"internalName": "NAMELESS_STONE", "reforgeName": "Nameless"}
		}
	"""

	const val ESSENCE_COSTS = """
		{
		  "HYPERION": {
		    "type": "Wither",
		    "1": 150, "2": 300, "3": 500, "4": 900, "5": 1500,
		    "items": {"5": ["SKYBLOCK_COIN:25000"]}
		  },
		  "TERROR_CHESTPLATE": {"type": "Crimson", "1": 10, "2": 20},
		  "HOT_TERROR_CHESTPLATE": {"type": "Crimson", "1": 30, "2": 40}
		}
	"""

	const val GEMSTONE_COSTS = """
		{
		  "HYPERION": {
		    "COMBAT_0": ["FINE_AMBER_GEM:20", "SKYBLOCK_COIN:250000"],
		    "COMBAT_1": ["FLAWED_JASPER_GEM:20"]
		  }
		}
	"""

	val PETS: String = """
		{
		  "pet_rarity_offset": {"COMMON": 0, "LEGENDARY": 0},
		  "pet_levels": [${List(99) { 1 }.joinToString(", ")}],
		  "custom_pet_leveling": {
		    "GOLDEN_DRAGON": {"pet_levels": [${List(100) { 2 }.joinToString(", ")}], "max_level": 200}
		  }
		}
	"""
}
