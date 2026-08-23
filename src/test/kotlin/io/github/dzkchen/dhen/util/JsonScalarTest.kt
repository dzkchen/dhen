package io.github.dzkchen.dhen.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonScalarTest {
	@Test
	fun `a whole number reads back as itself`() {
		val json = read("""{"amount": 42, "total": 9007199254740991}""")
		assertEquals(42, json.int("amount"))
		assertEquals(9007199254740991L, json.long("total"))
	}

	@Test
	fun `a fractional number truncates towards zero`() {
		val json = read("""{"up": 7.9, "down": -7.9}""")
		assertEquals(7, json.int("up"))
		assertEquals(-7L, json.long("down"))
	}

	@Test
	fun `an absent member reads as the fallback`() {
		val json = read("""{"present": 1}""")
		assertEquals(0, json.int("missing"))
		assertEquals(0L, json.long("missing"))
		assertEquals(100, json.int("missing", 100))
		assertEquals(100L, json.long("missing", 100L))
	}

	@Test
	fun `a member that is not a finite number reads as absent`() {
		val json = read("""{"text": "5", "flag": true, "object": {}, "array": [], "nothing": null}""")
		for (member in listOf("text", "flag", "object", "array", "nothing")) {
			assertEquals(-1, json.int(member, -1), member)
			assertEquals(-1L, json.long(member, -1L), member)
		}
	}

	@Test
	fun `a null object reads as the fallback`() {
		val absent: JsonObject? = null
		assertEquals(0, absent.int("anything"))
		assertEquals(3L, absent.long("anything", 3L))
	}

	private fun read(body: String): JsonObject = JsonParser.parseString(body).asJsonObject
}
