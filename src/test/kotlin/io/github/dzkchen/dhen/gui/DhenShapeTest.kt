package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DhenShapeTest {
	@Test
	fun `no dhen surface draws a rectangle outside the sharp seam`() {
		val scanned = SourceScan.files()
		assertTrue(scanned.any { it.name == SEAM }) { "scan missed the sources at ${SourceScan.MAIN.absolutePath}" }
		assertTrue(scanned.any { it.extension == "java" }) { "scan missed the mixins at ${SourceScan.MAIN.absolutePath}" }

		val seam = scanned.first { it.name == SEAM }
		assertTrue(VANILLA_RECTANGLE.containsMatchIn(seam.readText())) {
			"$SEAM no longer calls the vanilla rectangle this scan looks for, so the scan guards nothing"
		}

		val offenders = SourceScan.offenders(scanned, SEAM, VANILLA_RECTANGLE)

		assertTrue(offenders.isEmpty()) {
			"A corner belongs to RoundedGui and a sharp edge to $SEAM, but these draw their own:\n${offenders.joinToString("\n")}"
		}
	}

	private companion object {
		const val SEAM = "SharpGui.kt"
		val VANILLA_RECTANGLE =
			Regex("""graphics\.(fill|fillGradient|outline|horizontalLine|verticalLine)\(""")
	}
}
