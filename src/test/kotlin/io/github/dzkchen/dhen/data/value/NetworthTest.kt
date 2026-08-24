package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.RepoBackedTest
import io.github.dzkchen.dhen.data.item.HeldItem
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoState
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

internal class NetworthTest : RepoBackedTest() {
	@BeforeEach
	fun install() {
		DataFixture.installRepo(
			scope,
			repoRoot,
			mapOf("ENCHANTED_DIAMOND" to """{"internalname":"ENCHANTED_DIAMOND","displayname":"§aEnchanted Diamond"}""")
		)
		DataFixture.installPrices(scope, LOWEST_BINS)
	}

	@Test
	fun `every category is totalled and keyed by the name each entry shows`() {
		val report = report(Profile)

		assertEquals(mapOf("Hyperion" to 1000L), report.categories[NetworthCategory.INVENTORY])
		assertEquals(mapOf("Spirit Boots" to 200L), report.categories[NetworthCategory.ARMOR])
		assertEquals(mapOf("Enchanted Diamond" to 2000L), report.categories[NetworthCategory.SACKS])
		assertEquals(mapOf("Legendary Golden Dragon" to 30L), report.categories[NetworthCategory.PETS])
		assertEquals(mapOf("Purse" to 5L, "Solo Bank" to 7L), report.categories[NetworthCategory.CURRENCY])
		assertEquals(3242L, report.total)
	}

	@Test
	fun `a category the source does not carry contributes nothing`() {
		val report = report(Profile)

		assertEquals(emptyMap<String, Long>(), report.categories[NetworthCategory.QUIVER_BAG])
		assertEquals(NetworthCategory.entries.size, report.categories.size)
	}

	@Test
	fun `two of the same item in one category are added together, not replaced`() {
		val report = report(twoHyperions)

		assertEquals(mapOf("Hyperion" to 2000L), report.categories[NetworthCategory.INVENTORY])
	}

	@Test
	fun `a stack is worth what it holds, not one of them`() {
		val report = report(stackOfEight)

		assertEquals(mapOf("Enchanted Diamond" to 8000L), report.categories[NetworthCategory.INVENTORY])
	}

	@Test
	fun `the async form values away from the caller and hands the report back to it`() {
		val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, CLIENT_THREAD) }
		val client = executor.asCoroutineDispatcher()
		var valuedOn = ""
		var deliveredOn = ""
		var total = 0L
		val source = object : NetworthSource {
			override fun items(category: NetworthCategory): List<HeldItem> {
				valuedOn = threadName()
				return if (category == NetworthCategory.INVENTORY) held(named("HYPERION", "§6Hyperion")) else emptyList()
			}
		}

		try {
			runBlocking {
				launch(client) {
					total = Networth.ofAsync(source, PriceSource.LOWEST_BIN)!!.total
					deliveredOn = threadName()
				}.join()
			}
		} finally {
			executor.shutdown()
		}

		assertEquals(1000L, total)
		assertEquals(CLIENT_THREAD, deliveredOn)
		assertNotEquals(CLIENT_THREAD, valuedOn)
		assertTrue(valuedOn.isNotEmpty())
	}

	@Test
	fun `an unready item catalog is worth no report at all rather than an understated one`() {
		DataFixture.uninstall()
		DataFixture.installPrices(scope, LOWEST_BINS)

		assertNull(Networth.of(Profile, PriceSource.LOWEST_BIN))
		assertEquals(RepoState.IDLE, ItemRepo.state)
	}

	@Test
	fun `a repo that failed to load is worth no report either`() {
		DataFixture.uninstall()
		DataFixture.installRepo(scope, home.resolve("unreachable"))
		DataFixture.installPrices(scope, LOWEST_BINS)

		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertNull(Networth.of(Profile, PriceSource.LOWEST_BIN))
	}

	@Test
	fun `a levelled pet is worth the same whether it is held or listed on the profile`() {
		installPetConstants()
		val dragon = PetInfo("GOLDEN_DRAGON", "LEGENDARY", 299.0, null, 0, null)

		val listed = report(petsOf(dragon)).categories.getValue(NetworthCategory.PETS)
		val held = report(inventoryOf(petStack(dragon))).categories.getValue(NetworthCategory.INVENTORY)

		assertEquals(1000000L, listed.values.single())
		assertEquals(1000000L, held.values.single())
	}

	@Test
	fun `a pet carries its held item and its skin through either door`() {
		installPetConstants()
		val dragon = PetInfo("GOLDEN_DRAGON", "LEGENDARY", 299.0, TIER_BOOST, 0, "PET_SKIN_GOLDEN_DRAGON")

		val listed = report(petsOf(dragon)).categories.getValue(NetworthCategory.PETS)
		val held = report(inventoryOf(petStack(dragon))).categories.getValue(NetworthCategory.INVENTORY)

		assertEquals(1000075L, listed.values.single())
		assertEquals(1000075L, held.values.single())
	}

	@Test
	fun `a tier boosted pet is one rarity, not one per door`() {
		val dragon = PetInfo("GOLDEN_DRAGON", "LEGENDARY", 299.0, TIER_BOOST, 0, null)

		assertEquals(ItemRarity.LEGENDARY, ItemRarity.of(dragon))
		assertEquals(ItemRarity.LEGENDARY, HeldItem.of(petStack(dragon)).rarity)
	}

	private fun installPetConstants() {
		DataFixture.uninstall()
		DataFixture.installRepo(
			scope,
			home.resolve("pets"),
			mapOf("AOTE" to DataFixture.ANY_ITEM),
			mapOf("pets" to ConstantsFixture.PETS)
		)
		DataFixture.installPrices(scope, LOWEST_BINS)
	}

	private fun report(source: NetworthSource): NetworthReport = Networth.of(source, PriceSource.LOWEST_BIN)!!

	private fun petsOf(pet: PetInfo) = object : NetworthSource {
		override fun pets(): List<PetInfo> = listOf(pet)
	}

	private fun inventoryOf(stack: ItemStack) = object : NetworthSource {
		override fun items(category: NetworthCategory): List<HeldItem> =
			if (category == NetworthCategory.INVENTORY) held(stack) else emptyList()
	}

	private object Profile : NetworthSource {
		override fun items(category: NetworthCategory): List<HeldItem> = when (category) {
			NetworthCategory.INVENTORY -> held(named("HYPERION", "§6Hyperion"), ItemFixture.vanilla())
			NetworthCategory.ARMOR -> held(named("SPIRIT_BOOTS", "§5Spirit Boots"))
			else -> emptyList()
		}

		override fun sacks(): Map<String, Long> = mapOf("ENCHANTED_DIAMOND" to 2L, "NOBODY_BUYS_THIS" to 40L)

		override fun pets(): List<PetInfo> = listOf(PetInfo("GOLDEN_DRAGON", "LEGENDARY", 0.0, TIER_BOOST, 0, null))

		override fun currency(): Map<String, Long> = mapOf("Purse" to 5L, "Solo Bank" to 7L)
	}

	private val twoHyperions = object : NetworthSource {
		override fun items(category: NetworthCategory): List<HeldItem> =
			if (category == NetworthCategory.INVENTORY) {
				held(named("HYPERION", "§6Hyperion"), named("HYPERION", "§6Hyperion"))
			} else {
				emptyList()
			}
	}

	private val stackOfEight = object : NetworthSource {
		override fun items(category: NetworthCategory): List<HeldItem> =
			if (category == NetworthCategory.INVENTORY) {
				held(named("ENCHANTED_DIAMOND", "§aEnchanted Diamond").also { it.count = 8 })
			} else {
				emptyList()
			}
	}

	private companion object {
		private const val CLIENT_THREAD = "dhen-fake-client-thread"
		private const val TIER_BOOST = "PET_ITEM_TIER_BOOST"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private fun threadName(): String = Thread.currentThread().name.substringBefore(" @")

		private fun held(vararg stacks: ItemStack): List<HeldItem> = stacks.map(HeldItem::of)

		private fun petStack(pet: PetInfo): ItemStack = ItemFixture.stack {
			putString("id", "PET")
			putString(
				"petInfo",
				"""{"type":"${pet.type}","tier":"${pet.tier}","exp":${pet.exp}""" +
					pet.heldItem?.let { ""","heldItem":"$it"""" }.orEmpty() +
					pet.skin?.let { ""","skin":"$it"""" }.orEmpty() + "}"
			)
		}.also { it.set(DataComponents.CUSTOM_NAME, Component.literal(petName(pet))) }

		private fun petName(pet: PetInfo): String =
			"§7[Lvl 200] " + if (pet.heldItem == TIER_BOOST) "§dGolden Dragon" else "§6Golden Dragon"

		private fun named(id: String, name: String): ItemStack =
			ItemFixture.stack { putString("id", id) }
				.also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

		private val LOWEST_BINS = """
			{
			  "HYPERION": 1000.0,
			  "SPIRIT_BOOTS": 200.0,
			  "ENCHANTED_DIAMOND": 1000.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY": 10.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY-200": 1000000.0,
			  "PET_SKIN_GOLDEN_DRAGON": 55.0,
			  "PET_ITEM_TIER_BOOST": 20.0
			}
		""".trimIndent()
	}
}
