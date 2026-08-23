package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileAttributesTest {
	@Test
	fun `how far each attribute has been syphoned comes back under the id the API sends`() {
		val syphoned = decode(MEMBER).syphoned

		assertEquals(120, syphoned.getValue("veteran"))
		assertEquals(40, syphoned.getValue("undead"))
	}

	@Test
	fun `the shards still held are kept as their own reading, not folded into the syphoned one`() {
		val profile = decode(MEMBER)

		assertEquals(5, profile.shardsOwned.getValue("veteran").amount)
		assertEquals(1717000000000L, profile.shardsOwned.getValue("veteran").capturedAt)
		assertEquals(setOf("veteran", "breeze"), profile.shardsOwned.keys)
		assertTrue("breeze" !in profile.syphoned)
	}

	@Test
	fun `a shard type the API sends in mixed case is lowered to match the stacks beside it`() {
		val member = """{"attributes":{"stacks":{"veteran":3}},"shards":{"owned":[{"type":"VETERAN","amount_owned":9}]}}"""

		assertEquals(9, decode(member).shardsOwned.getValue("veteran").amount)
	}

	@Test
	fun `an owned entry with no type is dropped rather than carried as a blank holding`() {
		val member = """{"shards":{"owned":[{"amount_owned":4},{"type":"veteran","amount_owned":1}]}}"""

		assertEquals(setOf("veteran"), decode(member).shardsOwned.keys)
	}

	@Test
	fun `the fusions and the traps come off the same shard section`() {
		val profile = decode(MEMBER)
		val trap = profile.traps.single()

		assertEquals(2, profile.fusions)
		assertEquals("SHARD_TRAP", trap.trapItem)
		assertEquals("hunt", trap.mode)
		assertEquals("crystal_hollows", trap.location)
		assertEquals("veteran", trap.shard)
		assertTrue(trap.captured)
		assertEquals(1717000000000L, trap.placedAt)
		assertEquals(1717000009000L, trap.capturedAt)
		assertEquals(0, trap.toolkitIndex)
	}

	@Test
	fun `a trap that names no toolkit slot reports none rather than the first one`() {
		val member = """{"shards":{"traps":{"active_traps":[{"shard":"veteran"}]}}}"""
		val trap = decode(member).traps.single()

		assertNull(trap.toolkitIndex)
		assertFalse(trap.captured)
		assertEquals(0L, trap.placedAt)
	}

	@Test
	fun `a player who has never touched a shard decodes to nothing`() {
		assertNull(AttributesProfiles.of(DataFixture.json("""{"player_stats":{"deaths":4}}""")))
	}

	@Test
	fun `a player with stacks but no shard section still decodes what they syphoned`() {
		val profile = decode("""{"attributes":{"stacks":{"veteran":7}}}""")

		assertEquals(7, profile.syphoned.getValue("veteran"))
		assertTrue(profile.shardsOwned.isEmpty())
		assertEquals(0, profile.fusions)
		assertTrue(profile.traps.isEmpty())
	}

	private fun decode(member: String): AttributesProfile = AttributesProfiles.of(DataFixture.json(member))!!

	private companion object {
		private const val MEMBER = """{
			"attributes": {"stacks": {"veteran": 120, "undead": 40}},
			"shards": {
				"owned": [
					{"type": "veteran", "amount_owned": 5, "captured": 1717000000000},
					{"type": "breeze", "amount_owned": 2, "captured": 1717000005000}
				],
				"fused": 2,
				"traps": {"active_traps": [{
					"trap_item": "SHARD_TRAP",
					"mode": "hunt",
					"location": "crystal_hollows",
					"shard": "veteran",
					"placed_at": 1717000000000,
					"capture_time": 1717000009000,
					"captured": true,
					"hunting_toolkit_index": 0
				}]}
			}
		}"""
	}
}
