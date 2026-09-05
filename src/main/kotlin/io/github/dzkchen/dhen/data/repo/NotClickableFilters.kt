package io.github.dzkchen.dhen.data.repo

internal class NameFilter(
	private val equals: Set<String> = emptySet(),
	private val startsWith: List<String> = emptyList(),
	private val endsWith: List<String> = emptyList(),
	private val contains: List<String> = emptyList()
) {
	fun matches(name: String): Boolean {
		if (name in equals) return true
		for (prefix in startsWith) if (name.startsWith(prefix)) return true
		for (suffix in endsWith) if (name.endsWith(suffix)) return true
		for (fragment in contains) if (name.contains(fragment)) return true
		return false
	}
}

internal object NotClickableFilters {
	val npcSellAllowed = NameFilter(
		equals = setOf(
			"Ancient Claw", "Arachne's Belt", "Arachne's Boots", "Arachne's Chestplate", "Arachne's Cloak",
			"Arachne's Gloves", "Arachne's Helmet", "Arachne's Leggings", "Arachne's Necklace", "Arack",
			"Ascension Rope", "Azure Bluet", "Bag of Gold", "Battle Disc", "Birch Sapling", "Blaze Powder",
			"Cobblestone Slab", "Condensed Fermento", "Corrupted Fragment", "Cretan Urn", "Deep Root",
			"Defuse Kit", "Doubloon of the Family", "Enchanted Brown Mushroom", "Enchanted Golden Apple",
			"Enchanted Red Mushroom", "Enchanted Sugar", "Enchanted Wool", "Ender Belt", "Ender Boots",
			"Ender Chestplate", "Ender Cloak", "Ender Gauntlet", "Ender Helmet", "Ender Leggings",
			"Ender Necklace", "Endstone Rose", "Fairy's Fedora", "Fairy's Galoshes", "Fairy's Polo",
			"Fairy's Trousers", "Fermento", "Goblin Boots", "Goblin Chestplate", "Goblin Helmet",
			"Goblin Leggings", "Golden Apple", "Healing VIII Splash Potion", "Hilt of Revelations",
			"Icy Sinker", "Journal Entry", "Match-Sticks", "Netherrack-Looking Sunshade", "Pet Cake",
			"Polished Andesite", "Premium Flesh", "Revive Stone", "Sea Lantern", "Spruce Wood Plank",
			"Spruce Wood Slab", "Squid Boots", "Stone Brick Slab", "Stretching Sticks", "Sulphur",
			"Superboom TNT", "Toxic Arrow Poison", "Training Weights", "Tripwire Hook",
			"Twilight Arrow Poison", "White Wool", "Winter Disc", "Wishing Compass"
		),
		startsWith = listOf("Music Disc", "Wisp's Ice-Flavored Water I Splash"),
		endsWith = listOf(" Rune I", " Vinyl")
	)

	val storageBlocked = NameFilter(
		equals = setOf(
			"Basket of Seeds", "Builder's Ruler", "Builder's Wand", "Nether Wart Pouch", "Trick or Treat Bag"
		),
		endsWith = listOf("New Year Cake Bag")
	)

	val tradeBlocked = NameFilter(
		equals = setOf("Builder's Wand"),
		endsWith = listOf("Cat Talisman", "Cheetah Talisman", "Lynx Talisman"),
		contains = listOf("Personal Deletor ")
	)

	val auctionBlocked = NameFilter(
		equals = setOf("Basket of Seeds", "InfiniDirt™ Wand", "Nether Wart Pouch", "Prismapump"),
		endsWith = listOf("Cat Talisman", "Cheetah Talisman", "Lynx Talisman"),
		contains = listOf("Personal Deletor ")
	)

	val salvageable: List<String> = SALVAGE_ITEMS + SALVAGE_ARMOUR.flatMap { set ->
		ARMOUR_PIECES.map { piece -> "$set $piece" }
	}
}

private val ARMOUR_PIECES = listOf("Helmet", "Chestplate", "Leggings", "Boots")

private val SALVAGE_ARMOUR = listOf(
	"Bouncy", "Flaming", "Glacite", "Heavy", "Moogma", "Rampart", "Rotten", "Skeleton Grunt",
	"Skeleton Lord", "Skeleton Master", "Skeleton Soldier", "Sniper", "Zombie Commander",
	"Zombie Knight", "Zombie Soldier", "Skeletor", "Snow Suit"
)

private val SALVAGE_ITEMS = listOf(
	"Blade of the Volcano", "Conjuring", "Dreadlord Sword", "Earth Shard", "Machine Gun Shortbow",
	"Magma Rod", "Silent Death", "Soulstealer Bow", "Staff of the Volcano", "Sword of Bad Health",
	"Slug Boots", "Sniper Bow", "Taurus Helmet", "Zombie Commander Whip", "Zombie Knight Sword",
	"Zombie Soldier Cutlass", "Arachne's Belt", "Arachne's Boots", "Arachne's Chestplate",
	"Arachne's Cloak", "Arachne's Gloves", "Arachne's Helmet", "Arachne's Leggings",
	"Arachne's Necklace", "Arack", "Spider Talisman", "Icy Sinker", "Luxurious Spool",
	"Pickonimbus 2000", "Handy Blood Chalice", "Pocket Espresso Machine", "Glacial Talisman",
	"Tarantula Talisman"
)
