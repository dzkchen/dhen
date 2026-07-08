package io.github.dzkchen.dhen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DhenTest {
	@Test
	fun `id builds a namespaced identifier`() {
		val id = Dhen.id("auto_sprint")
		assertEquals("dhen", id.namespace)
		assertEquals("auto_sprint", id.path)
		assertEquals("dhen:auto_sprint", id.toString())
	}
}
