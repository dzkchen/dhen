package io.github.dzkchen.dhen.data.repo

internal class EndcapEnchant(val requiredLevel: Int, val endcapItem: String)

private class FreeEnchant(val level: Int, val items: Set<String>)

internal object CuratedConstants {
	private const val DEFAULT_BOOK_BUNDLE = 5

	private val TURBO_CROP_ENDCAPS = listOf(EndcapEnchant(5, "TURBO_GOURD"), EndcapEnchant(6, "ENCHANTED_TURBO_GOURD"))

	private val TURBO_CROPS = listOf(
		"TURBO_CACTUS", "TURBO_CANE", "TURBO_CARROT", "TURBO_COCO", "TURBO_MELON", "TURBO_MOONFLOWER",
		"TURBO_MUSHROOMS", "TURBO_POTATO", "TURBO_PUMPKIN", "TURBO_ROSE", "TURBO_SUNFLOWER", "TURBO_WARTS",
		"TURBO_WHEAT"
	)

	private val ENDCAPS: Map<String, List<EndcapEnchant>> = TURBO_CROPS.associateWith { TURBO_CROP_ENDCAPS } + mapOf(
		"BANE_OF_ARTHROPODS" to listOf(EndcapEnchant(6, "ENSNARED_SNAIL")),
		"CHARM" to listOf(EndcapEnchant(5, "CHAIN_END_TIMES")),
		"ENDER_SLAYER" to listOf(EndcapEnchant(6, "ENDSTONE_IDOL")),
		"FOREST_PLEDGE" to listOf(EndcapEnchant(5, "WATER_HYACINTH")),
		"FRAIL" to listOf(EndcapEnchant(6, "SEVERED_PINCER")),
		"KARMA" to listOf(EndcapEnchant(5, "DISTANT_ECHO")),
		"LUCK_OF_THE_SEA" to listOf(EndcapEnchant(6, "GOLD_BOTTLE_CAP")),
		"PESTERMINATOR" to listOf(EndcapEnchant(5, "PESTHUNTING_GUIDE")),
		"PISCARY" to listOf(EndcapEnchant(6, "TROUBLED_BUBBLE")),
		"SCAVENGER" to listOf(EndcapEnchant(5, "GOLDEN_BOUNTY")),
		"SCUBA" to listOf(EndcapEnchant(5, "VIBRANT_CORAL")),
		"SMITE" to listOf(EndcapEnchant(6, "SEVERED_HAND")),
		"SPIKED_HOOK" to listOf(EndcapEnchant(6, "OCTOPUS_TENDRIL")),
		"VENOMOUS" to listOf(EndcapEnchant(6, "FATEFUL_STINGER"))
	)

	private val ALWAYS_ACTIVE: Map<String, FreeEnchant> = mapOf(
		"SCAVENGER" to FreeEnchant(
			5,
			setOf(
				"CRYPT_DREADLORD_SWORD", "ZOMBIE_SOLDIER_CUTLASS", "CONJURING_SWORD", "EARTH_SHARD",
				"ZOMBIE_KNIGHT_SWORD", "SILENT_DEATH", "ZOMBIE_COMMANDER_WHIP", "ICE_SPRAY_WAND"
			)
		),
		"REPLENISH" to FreeEnchant(1, setOf("ADVANCED_GARDENING_HOE", "ADVANCED_GARDENING_AXE"))
	)

	private val BOOK_BUNDLES: Map<String, Int> = mapOf(
		"BIG_BRAIN" to 5,
		"PRISTINE" to 1,
		"QUANTUM" to 1,
		"RAINBOW" to 1,
		"REFLECTION" to 3,
		"SMALL_BRAIN" to 1,
		"ULTIMATE_CHIMERA" to 1,
		"ULTIMATE_THE_ONE" to 1,
		"VICIOUS" to 5
	)

	private val CRIMSON_PRESTIGE: Map<String, Map<String, Int>> = mapOf(
		"HOT" to mapOf("ESSENCE_CRIMSON" to 150, "KUUDRA_TEETH" to 10, "SKYBLOCK_COIN" to 2_000_000),
		"BURNING" to mapOf("ESSENCE_CRIMSON" to 800, "KUUDRA_TEETH" to 20, "SKYBLOCK_COIN" to 5_000_000),
		"FIERY" to mapOf("ESSENCE_CRIMSON" to 4_500, "KUUDRA_TEETH" to 50, "SKYBLOCK_COIN" to 10_000_000),
		"INFERNAL" to mapOf("ESSENCE_CRIMSON" to 25_500, "KUUDRA_TEETH" to 80, "SKYBLOCK_COIN" to 20_000_000)
	)

	val stackingEnchants: Set<String> =
		setOf("ABSORB", "CHAMPION", "COMPACT", "CULTIVATING", "EXPERTISE", "HECATOMB", "TOXOPHILITE")
	val endcappedEnchants: Set<String> = ENDCAPS.keys

	fun endcaps(enchantment: String): List<EndcapEnchant> = ENDCAPS[enchantment].orEmpty()

	fun grantedFree(enchantment: String, level: Int, id: String): Boolean {
		val free = ALWAYS_ACTIVE[enchantment] ?: return false
		return free.level == level && id in free.items
	}

	fun bookBundleAmount(enchantment: String): Int = BOOK_BUNDLES[enchantment] ?: DEFAULT_BOOK_BUNDLE

	fun crimsonPrestigeCost(tier: String): Map<String, Int> = CRIMSON_PRESTIGE[tier].orEmpty()
}
