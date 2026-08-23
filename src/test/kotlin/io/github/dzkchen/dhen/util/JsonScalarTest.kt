package io.github.dzkchen.dhen.util

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonScalarTest {
	@Test
	fun `a whole number reads back as itself`() {
		val document = json("""{"amount": 42, "total": 9007199254740991}""")
		assertEquals(42, document.int("amount"))
		assertEquals(9007199254740991L, document.long("total"))
	}

	@Test
	fun `a fractional number truncates towards zero`() {
		val document = json("""{"up": 7.9, "down": -7.9}""")
		assertEquals(7, document.int("up"))
		assertEquals(-7L, document.long("down"))
	}

	@Test
	fun `an absent member reads as the fallback`() {
		val document = json("""{"present": 1}""")
		assertEquals(0, document.int("missing"))
		assertEquals(0L, document.long("missing"))
		assertEquals(100, document.int("missing", 100))
		assertEquals(100L, document.long("missing", 100L))
	}

	@Test
	fun `a member that is not a finite number reads as absent`() {
		val document = json("""{"text": "5", "flag": true, "object": {}, "array": [], "nothing": null}""")
		for (member in listOf("text", "flag", "object", "array", "nothing")) {
			assertEquals(-1, document.int(member, -1), member)
			assertEquals(-1L, document.long(member, -1L), member)
		}
	}

	@Test
	fun `a null object reads as the fallback`() {
		val absent: JsonObject? = null
		assertEquals(0, absent.int("anything"))
		assertEquals(3L, absent.long("anything", 3L))
	}
}
