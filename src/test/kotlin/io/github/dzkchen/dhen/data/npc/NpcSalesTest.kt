package io.github.dzkchen.dhen.data.npc

import io.github.dzkchen.dhen.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NpcSalesTest {
	private var account = "first"
	private var day = 100L

	@AfterEach
	fun teardown() = NpcSales.uninstall()

	@Test
	fun `sales add up across the day`(@TempDir dir: Path) {
		install(dir)

		NpcSales.record(9_600_000L)
		NpcSales.record(50_000L)

		assertEquals(9_650_000L, NpcSales.soldToday)
	}

	@Test
	fun `the count rolls to zero the first time it is read on a new day`(@TempDir dir: Path) {
		install(dir)
		NpcSales.record(1_000L)

		day = 101L

		assertEquals(0L, NpcSales.soldToday)
	}

	@Test
	fun `each account keeps its own running total`(@TempDir dir: Path) {
		install(dir)
		NpcSales.record(1_000L)

		account = "second"
		NpcSales.record(500L)

		assertEquals(500L, NpcSales.soldToday)

		account = "first"

		assertEquals(1_000L, NpcSales.soldToday)
	}

	@Test
	fun `a zero or negative sale is ignored`(@TempDir dir: Path) {
		install(dir)

		NpcSales.record(0L)
		NpcSales.record(-5L)

		assertEquals(0L, NpcSales.soldToday)
	}

	private fun install(dir: Path) = NpcSales.install(
		ConfigStore(dir.resolve("npcsales.json"), CoroutineScope(Dispatchers.Unconfined)),
		{ account },
		{ day }
	)
}
