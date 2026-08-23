package io.github.dzkchen.dhen.data.value

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.price.PriceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

class NetworthTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@BeforeEach
	fun install() {
		DataFixture.installRepo(
			scope,
			home.resolve("repo"),
			mapOf("ENCHANTED_DIAMOND" to """{"internalname":"ENCHANTED_DIAMOND","displayname":"§aEnchanted Diamond"}""")
		)
		DataFixture.installPrices(scope, LOWEST_BINS)
	}

	@AfterEach
	fun uninstall() {
		DataFixture.uninstall()
	}

	@Test
	fun `every category is totalled and keyed by the name each entry shows`() {
		val report = Networth.of(Profile, PriceSource.LOWEST_BIN)

		assertEquals(mapOf("Hyperion" to 1000L), report.categories[NetworthCategory.INVENTORY])
		assertEquals(mapOf("Spirit Boots" to 200L), report.categories[NetworthCategory.ARMOR])
		assertEquals(mapOf("Enchanted Diamond" to 2000L), report.categories[NetworthCategory.SACKS])
		assertEquals(mapOf("Legendary Golden Dragon" to 30L), report.categories[NetworthCategory.PETS])
		assertEquals(mapOf("Purse" to 5L, "Solo Bank" to 7L), report.categories[NetworthCategory.CURRENCY])
		assertEquals(3242L, report.total)
	}

	@Test
	fun `a category the source does not carry contributes nothing`() {
		val report = Networth.of(Profile, PriceSource.LOWEST_BIN)

		assertEquals(emptyMap<String, Long>(), report.categories[NetworthCategory.QUIVER_BAG])
		assertEquals(NetworthCategory.entries.size, report.categories.size)
	}

	@Test
	fun `two of the same item in one category are added together, not replaced`() {
		val report = Networth.of(twoHyperions, PriceSource.LOWEST_BIN)

		assertEquals(mapOf("Hyperion" to 2000L), report.categories[NetworthCategory.INVENTORY])
	}

	@Test
	fun `a stack is worth what it holds, not one of them`() {
		val report = Networth.of(stackOfEight, PriceSource.LOWEST_BIN)

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
			override fun items(category: NetworthCategory): List<ItemStack> {
				valuedOn = threadName()
				return if (category == NetworthCategory.INVENTORY) listOf(named("HYPERION", "§6Hyperion")) else emptyList()
			}
		}

		try {
			runBlocking {
				launch(client) {
					total = Networth.ofAsync(source, PriceSource.LOWEST_BIN).total
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

	private object Profile : NetworthSource {
		override fun items(category: NetworthCategory): List<ItemStack> = when (category) {
			NetworthCategory.INVENTORY -> listOf(named("HYPERION", "§6Hyperion"), ItemFixture.vanilla())
			NetworthCategory.ARMOR -> listOf(named("SPIRIT_BOOTS", "§5Spirit Boots"))
			else -> emptyList()
		}

		override fun sacks(): Map<String, Long> = mapOf("ENCHANTED_DIAMOND" to 2L, "NOBODY_BUYS_THIS" to 40L)

		override fun pets(): List<PetInfo> = listOf(PetInfo("GOLDEN_DRAGON", "LEGENDARY", 0.0, "TIER_BOOST", 0, null))

		override fun currency(): Map<String, Long> = mapOf("Purse" to 5L, "Solo Bank" to 7L)
	}

	private val twoHyperions = object : NetworthSource {
		override fun items(category: NetworthCategory): List<ItemStack> =
			if (category == NetworthCategory.INVENTORY) {
				listOf(named("HYPERION", "§6Hyperion"), named("HYPERION", "§6Hyperion"))
			} else {
				emptyList()
			}
	}

	private val stackOfEight = object : NetworthSource {
		override fun items(category: NetworthCategory): List<ItemStack> =
			if (category == NetworthCategory.INVENTORY) {
				listOf(named("ENCHANTED_DIAMOND", "§aEnchanted Diamond").also { it.count = 8 })
			} else {
				emptyList()
			}
	}

	private companion object {
		private const val CLIENT_THREAD = "dhen-fake-client-thread"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()

		private fun threadName(): String = Thread.currentThread().name.substringBefore(" @")

		private fun named(id: String, name: String): ItemStack =
			ItemFixture.stack { putString("id", id) }
				.also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

		private val LOWEST_BINS = """
			{
			  "HYPERION": 1000.0,
			  "SPIRIT_BOOTS": 200.0,
			  "ENCHANTED_DIAMOND": 1000.0,
			  "PET-GOLDEN_DRAGON-LEGENDARY": 10.0,
			  "TIER_BOOST": 20.0
			}
		""".trimIndent()
	}
}
