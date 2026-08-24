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

	const val LEVELING = """
		{
		  "leveling_xp": [50, 125, 200, 300, 500],
		  "leveling_caps": {"farming": 3, "combat": 5, "runecrafting": 2, "social": 2, "HOTM": 3, "HOTF": 2},
		  "HOTM": [0, 100, 200, 400],
		  "HOTF": [0, 50, 150],
		  "runecrafting_xp": [50, 100],
		  "social": [50, 100],
		  "slayer_xp": {"zombie": [5, 15, 200], "vampire": [20, 75]}
		}
	"""

	const val GARDEN = """
		{
		  "garden_exp": [0, 70, 70, 140],
		  "crop_milestones": {"WHEAT": [30, 50, 80], "CARROT": [100, 150]}
		}
	"""

	val PETS: String = """
		{
		  "pet_rarity_offset": {"COMMON": 0, "RARE": 0, "EPIC": 50, "LEGENDARY": 0, "MYTHIC": 50},
		  "pet_levels": [${List(99) { 1 }.joinToString(", ")}],
		  "custom_pet_leveling": {
		    "GOLDEN_DRAGON": {"pet_levels": [${List(100) { 2 }.joinToString(", ")}], "max_level": 200},
		    "BINGO": {"rarity_offset": {"COMMON": 0, "RARE": 0, "EPIC": 0, "LEGENDARY": 0}}
		  }
		}
	"""
}
