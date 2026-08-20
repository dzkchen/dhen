package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoSource
import io.github.dzkchen.dhen.data.repo.RepoSync
import io.github.dzkchen.dhen.data.repo.RepoTransport
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ItemValueTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@BeforeEach
	fun install() {
		val root = home.resolve("repo")
		Files.createDirectories(root.resolve("items"))
		Files.createDirectories(root.resolve("constants"))
		Files.writeString(root.resolve("items/HYPERION.json"), "{\"internalname\":\"HYPERION\",\"displayname\":\"§6Hyperion\"}")
		Files.writeString(root.resolve("constants/reforgestones.json"), ConstantsFixture.REFORGE_STONES)
		Files.writeString(root.resolve("constants/essencecosts.json"), ConstantsFixture.ESSENCE_COSTS)
		Files.writeString(root.resolve("constants/gemstonecosts.json"), ConstantsFixture.GEMSTONE_COSTS)
		Files.writeString(root.resolve("constants/pets.json"), ConstantsFixture.PETS)
		ItemRepo.install(scope, root, RepoSync(SOURCE, root, OfflineTransport))
		ItemRepo.require()
		Prices.install(scope, EventBus(), Dispatchers.Unconfined, FakeSource, { 0L }) { true }
		Prices.require()
	}

	@AfterEach
	fun uninstall() {
		ItemRepo.uninstall()
		Prices.uninstall()
	}

	@Test
	fun `an item with no extras is worth its own price and nothing more`() {
		val valuation = value(item { putString("id", "HYPERION") })

		assertEquals(1000.0, valuation.total)
		assertEquals(1000.0, valuation.base)
		assertFalse(valuation.modified)
		assertEquals(listOf("Hyperion"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `an item nobody sells contributes nothing and says so`() {
		val valuation = value(item { putString("id", "MANDRAA") })

		assertEquals(0.0, valuation.total)
		assertEquals(listOf("MANDRAA"), valuation.breakdown.map { it.label })
		assertFalse(valuation.breakdown.single().priced)
	}

	@Test
	fun `an enchanted book is worth its enchantment, counted once`() {
		val valuation = value(
			item {
				putString("id", "ENCHANTED_BOOK")
				put("enchantments", CompoundTag().apply { putInt("ultimate_wise", 5) })
			}
		)

		assertEquals(3000.0, valuation.total)
		assertEquals(3000.0, valuation.base)
		assertEquals(listOf("ENCHANTED_BOOK-ULTIMATE_WISE-5"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `a breakdown always adds up to the total it was handed with`() {
		val items = listOf(
			item { putString("id", "HYPERION"); putInt("upgrade_level", 7); putInt("hot_potato_count", 13) },
			item { putString("id", "HYPERION"); putString("modifier", "spiritual"); put("gems", gems()) },
			item { putString("id", "ENCHANTED_BOOK"); put("enchantments", CompoundTag().apply { putInt("ultimate_wise", 5) }) },
			item { putString("id", "MANDRAA") },
			pet("GOLDEN_DRAGON", 299.0),
			item {
				putString("id", "HYPERION")
				putBoolean("artOfPeaceApplied", true)
				putInt("polarvoid", 3)
				putString("skin", "SORROW_HELMET_SKIN")
				putString("power_ability_scroll", "NOBODY_SELLS_THIS")
				put("boosters", ListTag().apply { add(StringTag.valueOf("MITHRIL")) })
			}
		)

		for (item in items) {
			val valuation = value(item)
			assertEquals(valuation.total, valuation.breakdown.sumOf { it.amount })
		}
	}

	@Test
	fun `a divine item pays the reforge apply cost the repo lists for divine`() {
		val valuation = value(item { putString("id", "HYPERION"); putString("modifier", "spiritual") }, ItemRarity.DIVINE)

		assertEquals(1000.0 + 200.0 + 200000.0, valuation.total)
	}

	@Test
	fun `a rarity the repo prices for nobody falls back to the legendary apply cost`() {
		val valuation = value(item { putString("id", "HYPERION"); putString("modifier", "spiritual") }, ItemRarity.VERY_SPECIAL)

		assertEquals(1000.0 + 200.0 + 100000.0, valuation.total)
	}

	@Test
	fun `the price source the caller names is the one the fold reads`() {
		val diamond = item { putString("id", "ENCHANTED_DIAMOND") }

		assertEquals(1349.2, value(diamond, PriceSource.BAZAAR_INSTANT_BUY).total)
		assertEquals(1262.8, value(diamond, PriceSource.BAZAAR_INSTANT_SELL).total)
		assertEquals(0.0, value(diamond, PriceSource.LOWEST_BIN).total)
	}

	@Test
	fun `a recombobulated item adds the recombobulator`() {
		val valuation = value(item { putString("id", "HYPERION"); putInt("rarity_upgrades", 1) })

		assertEquals(6000.0, valuation.total)
		assertTrue(valuation.modified)
	}

	@Test
	fun `a reforged item adds the stone and the apply cost for its rarity`() {
		val valuation = value(item { putString("id", "HYPERION"); putString("modifier", "spiritual") }, ItemRarity.EPIC)

		assertEquals(1000.0 + 200.0 + 75000.0, valuation.total)
		assertEquals(listOf("Hyperion", "Reforge: Spiritual", "Reforge apply cost"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `a recombobulated item pays the reforge apply cost of the rarity below`() {
		val valuation = value(
			item { putString("id", "HYPERION"); putString("modifier", "spiritual"); putInt("rarity_upgrades", 1) },
			ItemRarity.EPIC
		)

		assertEquals(1000.0 + 5000.0 + 200.0 + 50000.0, valuation.total)
	}

	@Test
	fun `a reforge stone the repo gives no apply cost is left out entirely`() {
		val valuation = value(item { putString("id", "HYPERION"); putString("modifier", "nameless") }, ItemRarity.EPIC)

		assertEquals(1000.0, valuation.total)
		assertEquals(listOf("Hyperion"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `a starred dungeon weapon adds its essence, its coins and its master stars`() {
		val valuation = value(item { putString("id", "HYPERION"); putInt("upgrade_level", 7) })

		assertEquals(1000.0 + (150 + 300 + 500 + 900 + 1500) * 2.0 + 25000.0 + 1000.0 + 2000.0, valuation.total)
		assertTrue(valuation.breakdown.any { it.label == "Stars 5 of 5" })
	}

	@Test
	fun `an unstarred item is not charged for stars it never took`() {
		assertEquals(1000.0, value(item { putString("id", "HYPERION") }).total)
	}

	@Test
	fun `a kuudra piece counts the stars of every tier below its own`() {
		val valuation = value(item { putString("id", "HOT_TERROR_CHESTPLATE") })

		assertEquals((10 + 20 + 30 + 40) * 3.0, valuation.total)
		assertTrue(valuation.breakdown.any { it.label == "Stars 4 of 4" })
		assertFalse(valuation.breakdown.any { it.label.contains("MASTER_STAR") })
	}

	@Test
	fun `hot potato books above ten become fuming potato books`() {
		val valuation = value(item { putString("id", "HYPERION"); putInt("hot_potato_count", 13) })

		assertEquals(1000.0 + 10 * 10.0 + 3 * 100.0, valuation.total)
		assertEquals(listOf("Hyperion", "HOT_POTATO_BOOK x10", "FUMING_POTATO_BOOK x3"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `a potato count beyond what the game allows is charged at the game's cap`() {
		val valuation = value(item { putString("id", "HYPERION"); putInt("hot_potato_count", 9999) })

		assertEquals(1000.0 + 10 * 10.0 + 5 * 100.0, valuation.total)
		assertEquals(listOf("Hyperion", "HOT_POTATO_BOOK x10", "FUMING_POTATO_BOOK x5"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `the art of war and an etherwarp merge are each counted once`() {
		val valuation = value(
			item { putString("id", "HYPERION"); putInt("art_of_war_count", 1); putBoolean("ethermerge", true) }
		)

		assertEquals(1000.0 + 400.0 + 600.0 + 700.0, valuation.total)
	}

	@Test
	fun `gemstones are read from both the slot key and its companion tag`() {
		val valuation = value(item { putString("id", "HYPERION"); put("gems", gems()) })

		assertEquals(1000.0 + 50.0 + 25.0 + 9000.0 + 20 * 50.0 + 250000.0 + 20 * 25.0, valuation.total)
		assertTrue(valuation.breakdown.any { it.label == "Unlocked gemstone slots: 2" })
	}

	@Test
	fun `an enchantment sold at its own level is priced one book at a time`() {
		val valuation = value(
			item { putString("id", "HYPERION"); put("enchantments", CompoundTag().apply { putInt("ultimate_wise", 5) }) }
		)

		assertEquals(1000.0 + 3000.0, valuation.total)
	}

	@Test
	fun `an enchantment only sold lower down is priced by the books it took to combine`() {
		val valuation = value(
			item { putString("id", "HYPERION"); put("enchantments", CompoundTag().apply { putInt("toxophilite", 3) }) }
		)

		assertEquals(1000.0 + 4 * 64.0, valuation.total)
		assertEquals("ENCHANTED_BOOK-TOXOPHILITE-1 x4", valuation.breakdown.last().label)
	}

	@Test
	fun `efficiency becomes silex rather than books, and an enchantment nobody sells is left out`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				put("enchantments", CompoundTag().apply { putInt("efficiency", 10); putInt("mandraa", 1) })
			}
		)

		assertEquals(1000.0 + 5 * 30.0, valuation.total)
		assertEquals(listOf("Hyperion", "SIL_EX x5"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `the once-only upgrades are each counted a single time`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				putBoolean("artOfPeaceApplied", true)
				putInt("wood_singularity_count", 1)
				putInt("jalapeno_count", 3)
				putInt("stats_book", 0)
				putBoolean("divan_powder_coating", true)
				putByte("mithril_infusion", 1)
				putByte("free_will", 1)
			}
		)

		assertEquals(1000.0 + 11.0 + 12.0 + 13.0 + 14.0 + 15.0 + 16.0 + 17.0, valuation.total)
		assertEquals(
			listOf(
				"Hyperion", "THE_ART_OF_PEACE", "WOOD_SINGULARITY", "JALAPENO_BOOK",
				"BOOK_OF_STATS", "DIVAN_POWDER_COATING", "MITHRIL_INFUSION", "FREE_WILL"
			),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `an item with none of the once-only upgrades pays for none of them`() {
		assertEquals(listOf("Hyperion"), value(item { putString("id", "HYPERION") }).breakdown.map { it.label })
	}

	@Test
	fun `the counted upgrades are priced by how many were applied`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				putInt("wet_book_count", 2)
				putInt("farming_for_dummies_count", 3)
				putInt("levelable_overclocks", 4)
				putInt("tuned_transmission", 2)
				putInt("mana_disintegrator_count", 5)
				putInt("polarvoid", 3)
				putInt("bookworm_books", 1)
				putInt("sack_pss", 2)
			}
		)

		assertEquals(
			1000.0 + 2 * 20.0 + 3 * 21.0 + 4 * 22.0 + 2 * 23.0 + 5 * 24.0 + 3 * 25.0 + 26.0 + 2 * 27.0,
			valuation.total
		)
		assertEquals(
			listOf(
				"Hyperion", "WET_BOOK x2", "FARMING_FOR_DUMMIES x3", "OVERCLOCKER_3000 x4",
				"TRANSMISSION_TUNER x2", "MANA_DISINTEGRATOR x5", "POLARVOID_BOOK x3",
				"BOOKWORM_BOOK", "POCKET_SACK_IN_A_SACK x2"
			),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `silex is the efficiency levels above the five an item comes with`() {
		assertEquals(1000.0 + 3 * 30.0, value(efficient("HYPERION", 8)).total)
		assertEquals(1000.0, value(efficient("HYPERION", 5)).total)
	}

	@Test
	fun `a pickaxe that comes with its own efficiency is not charged for it`() {
		assertEquals(2 * 30.0, value(efficient("STONK_PICKAXE", 8)).total)
		assertEquals(0.0, value(efficient("PROMISING_SPADE", 10)).total)
	}

	@Test
	fun `a skin, a dye and a rune are each named by what they are`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				putString("skin", "SORROW_HELMET_SKIN")
				putString("dye_item", "DYE_ARCHFIEND")
				put("runes", CompoundTag().apply { putInt("ZOMBIE_SLAYER", 3) })
			}
		)

		assertEquals(1000.0 + 40.0 + 41.0 + 42.0, valuation.total)
		assertEquals(
			listOf("Hyperion", "Skin: SORROW_HELMET_SKIN", "Dye: DYE_ARCHFIEND", "Rune: RUNE-ZOMBIE_SLAYER-3"),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `a rune item is not charged again for the rune it already is`() {
		val valuation = value(
			item {
				putString("id", "RUNE")
				put("runes", CompoundTag().apply { putInt("ZOMBIE_SLAYER", 3) })
			}
		)

		assertEquals(42.0, valuation.total)
		assertEquals(listOf("RUNE-ZOMBIE_SLAYER-3"), valuation.breakdown.map { it.label })
	}

	@Test
	fun `the ultimate wither scroll is priced as its three parts, and never twice`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				put(
					"ability_scroll",
					ListTag().apply {
						add(StringTag.valueOf("ULTIMATE_WITHER_SCROLL"))
						add(StringTag.valueOf("implosion_scroll"))
					}
				)
			}
		)

		assertEquals(1000.0 + 50.0 + 51.0 + 52.0, valuation.total)
		assertEquals(
			listOf("Hyperion", "IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL"),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `boosters, drill parts and rod parts are each priced`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				put("boosters", ListTag().apply { add(StringTag.valueOf("MITHRIL")) })
				putString("drill_part_engine", "amber_polished_drill_engine")
				putString("drill_part_fuel_tank", "PERFECTLY_CUT_FUEL_TANK")
				put("hook", CompoundTag().apply { putString("part", "TREASURE_HOOK") })
				put("sinker", CompoundTag().apply { putString("part", "TITANIUM_SINKER") })
			}
		)

		assertEquals(1000.0 + 60.0 + 61.0 + 62.0 + 63.0 + 64.0, valuation.total)
		assertEquals(
			listOf(
				"Hyperion", "MITHRIL_BOOSTER", "AMBER_POLISHED_DRILL_ENGINE",
				"PERFECTLY_CUT_FUEL_TANK", "TREASURE_HOOK", "TITANIUM_SINKER"
			),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `a power scroll and an enrichment are priced by the item they name`() {
		val valuation = value(
			item {
				putString("id", "HYPERION")
				putString("power_ability_scroll", "WITHER_SHIELD_SCROLL")
				putString("talisman_enrichment", "STRENGTH")
			}
		)

		assertEquals(1000.0 + 51.0 + 70.0, valuation.total)
		assertEquals(
			listOf("Hyperion", "WITHER_SHIELD_SCROLL", "TALISMAN_ENRICHMENT_STRENGTH"),
			valuation.breakdown.map { it.label }
		)
	}

	@Test
	fun `a maxed pet is priced at the level it reached`() {
		assertEquals(1000000.0, value(pet("GOLDEN_DRAGON", 299.0)).total)
		assertEquals(100.0, value(pet("GOLDEN_DRAGON", 99.0)).total)
		assertEquals(10.0, value(pet("GOLDEN_DRAGON", 0.0)).total)
	}

	@Test
	fun `a maxed pet nobody lists at that level falls back to its unlevelled price`() {
		val valuation = value(pet("AMMONITE", 99.0))

		assertEquals(20.0, valuation.total)
		assertEquals("PET-AMMONITE-LEGENDARY level 100", valuation.breakdown.single().label)
	}

	private fun value(
		item: SkyBlockItem,
		source: PriceSource = PriceSource.BAZAAR_INSTANT_SELL
	): Valuation = ItemValue.of(item, ItemRarity.LEGENDARY, source)

	private fun value(item: SkyBlockItem, rarity: ItemRarity): Valuation =
		ItemValue.of(item, rarity, PriceSource.BAZAAR_INSTANT_SELL)

	private fun item(build: CompoundTag.() -> Unit): SkyBlockItem = SkyBlockItems.of(ItemFixture.customData(build))

	private fun efficient(id: String, level: Int): SkyBlockItem = item {
		putString("id", id)
		put("enchantments", CompoundTag().apply { putInt("efficiency", level) })
	}

	private fun pet(type: String, exp: Double): SkyBlockItem = item {
		putString("id", "PET")
		putString("petInfo", "{\"type\":\"$type\",\"tier\":\"LEGENDARY\",\"exp\":$exp}")
	}

	private fun gems(): CompoundTag = CompoundTag().apply {
		putString("COMBAT_0", "FINE")
		putString("COMBAT_0_gem", "AMBER")
		put("COMBAT_1", CompoundTag().apply { putString("quality", "FLAWED") })
		putString("COMBAT_1_gem", "JASPER")
		putString("JASPER_0", "PERFECT")
		put("unlocked_slots", ListTag().apply { add(StringTag.valueOf("COMBAT_0")); add(StringTag.valueOf("COMBAT_1")) })
	}

	private object OfflineTransport : RepoTransport {
		override fun text(url: String): String? = null

		override fun download(url: String, destination: Path): Boolean = false
	}

	private object FakeSource : WebSource {
		override fun text(url: String): String = when {
			url.contains("bazaar") -> BAZAAR
			url.contains("tricked") -> LOWEST_BINS
			url.contains("eliteskyblock") -> "{\"SPARE_ONLY\":1}"
			else -> "{\"items\":[{\"id\":\"MANDRAA_NPC\",\"npc_sell_price\":1}]}"
		}
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private val SOURCE = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")

		private val LOWEST_BINS = """
			{
			  "HYPERION": 1000.0,
			  "SPIRIT_STONE": 200.0,
			  "RECOMBOBULATOR_3000": 5000.0,
			  "THE_ART_OF_WAR": 400.0,
			  "ETHERWARP_CONDUIT": 600.0,
			  "ETHERWARP_MERGER": 700.0,
			  "HOT_POTATO_BOOK": 10.0,
			  "FUMING_POTATO_BOOK": 100.0,
			  "ESSENCE_WITHER": 2.0,
			  "ESSENCE_CRIMSON": 3.0,
			  "FIRST_MASTER_STAR": 1000.0,
			  "SECOND_MASTER_STAR": 2000.0,
			  "FINE_AMBER_GEM": 50.0,
			  "FLAWED_JASPER_GEM": 25.0,
			  "PERFECT_JASPER_GEM": 9000.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY": 10.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY-100": 100.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY-200": 1000000.0,
			  "PET-AMMONITE-LEGENDARY": 20.0,
			  "ENCHANTED_BOOK-ULTIMATE_WISE-5": 3000.0,
			  "ENCHANTED_BOOK-TOXOPHILITE-1": 64.0,
			  "THE_ART_OF_PEACE": 11.0,
			  "WOOD_SINGULARITY": 12.0,
			  "JALAPENO_BOOK": 13.0,
			  "BOOK_OF_STATS": 14.0,
			  "DIVAN_POWDER_COATING": 15.0,
			  "MITHRIL_INFUSION": 16.0,
			  "FREE_WILL": 17.0,
			  "WET_BOOK": 20.0,
			  "FARMING_FOR_DUMMIES": 21.0,
			  "OVERCLOCKER_3000": 22.0,
			  "TRANSMISSION_TUNER": 23.0,
			  "MANA_DISINTEGRATOR": 24.0,
			  "POLARVOID_BOOK": 25.0,
			  "BOOKWORM_BOOK": 26.0,
			  "POCKET_SACK_IN_A_SACK": 27.0,
			  "SIL_EX": 30.0,
			  "STONK_PICKAXE": 0.0,
			  "SORROW_HELMET_SKIN": 40.0,
			  "DYE_ARCHFIEND": 41.0,
			  "RUNE-ZOMBIE_SLAYER-3": 42.0,
			  "IMPLOSION_SCROLL": 50.0,
			  "WITHER_SHIELD_SCROLL": 51.0,
			  "SHADOW_WARP_SCROLL": 52.0,
			  "AMBER_POLISHED_DRILL_ENGINE": 60.0,
			  "PERFECTLY_CUT_FUEL_TANK": 61.0,
			  "TREASURE_HOOK": 62.0,
			  "TITANIUM_SINKER": 63.0,
			  "MITHRIL_BOOSTER": 64.0,
			  "TALISMAN_ENRICHMENT_STRENGTH": 70.0
			}
		""".trimIndent()

		private val BAZAAR = """
			{
			  "success": true,
			  "lastUpdated": 1787171886917,
			  "products": {
			    "ENCHANTED_DIAMOND": {
			      "product_id": "ENCHANTED_DIAMOND",
			      "sell_summary": [{"amount": 7386, "pricePerUnit": 1262.8, "orders": 1}],
			      "buy_summary": [{"amount": 100, "pricePerUnit": 1349.2, "orders": 1}],
			      "quick_status": {"sellPrice": 1262.7, "buyPrice": 1349.2, "buyOrders": 110}
			    }
			  }
			}
		""".trimIndent()
	}
}
