package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.gui.SourceScan
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixinPackageTest {
	@Test
	fun `the mixin package holds exactly the classes the mixin config declares`() {
		val onDisk = SourceScan.files(PACKAGE, setOf("java"))
			.map { it.toRelativeString(PACKAGE).removeSuffix(".java").replace(File.separatorChar, '.') }
			.toSet()

		assertTrue(onDisk.isNotEmpty()) { "scan missed the mixins at ${PACKAGE.absolutePath}" }

		assertEquals(declared(), onDisk) {
			"Mixin owns every class in ${PACKAGE.path}, so one it does not declare throws " +
				"IllegalClassLoadError the moment ordinary code touches it. A shared interface belongs " +
				"outside the package; a real mixin belongs in ${CONFIG.name}"
		}
	}

	private fun declared(): Set<String> = ARRAY.findAll(CONFIG.readText())
		.flatMap { array -> ENTRY.findAll(array.groupValues[1]).map { it.groupValues[1] } }
		.toSet()

	private companion object {
		val PACKAGE = File("src/main/java/io/github/dzkchen/dhen/mixin")
		val CONFIG = File("src/main/resources/dhen.mixins.json")
		val ARRAY = Regex(""""(?:mixins|client|server)"\s*:\s*\[([^]]*)]""")
		val ENTRY = Regex(""""([^"]+)"""")
	}
}
