package io.github.dzkchen.dhen.gui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DhenShapeTest {
	@Test
	fun `no dhen surface draws a rectangle outside the sharp seam`() {
		assertOnly(setOf(SEAM), VANILLA_RECTANGLE, "A corner belongs to RoundedGui and a sharp edge to $SEAM")
	}

	@Test
	fun `the other two ways of putting a pixel on screen stay where the inventory says they are`() {
		assertOnly(PRIMITIVE_SEAMS, RENDER_ELEMENT, "A new drawing tier belongs beside the ones already there")
		assertOnly(setOf(SPLASH_SEAM), TEXTURE, "Only the boot overlay draws a texture, and only its baked mark")
	}

	private fun assertOnly(seams: Set<String>, pattern: Regex, complaint: String) {
		val scanned = SourceScan.files()
		assertTrue(scanned.any { it.extension == "java" }) { "scan missed the mixins at ${SourceScan.MAIN.absolutePath}" }

		for (name in seams) {
			val seam = scanned.firstOrNull { it.name == name }
			assertTrue(seam != null && pattern.containsMatchIn(seam.readText())) {
				"$name no longer makes the call this scan looks for, so the scan guards nothing"
			}
		}

		val offenders = SourceScan.offenders(scanned, seams, pattern)

		assertTrue(offenders.isEmpty()) {
			"$complaint, but these bypass ${seams.joinToString(" / ")}:\n${offenders.joinToString("\n")}"
		}
	}

	private companion object {
		const val SEAM = "SharpGui.kt"
		const val SPLASH_SEAM = "LoadingSplash.kt"
		val PRIMITIVE_SEAMS = setOf("RoundedGui.kt", "GradientGui.kt")
		val VANILLA_RECTANGLE =
			Regex("""graphics\.(fill|fillGradient|outline|horizontalLine|verticalLine)\(""")
		val RENDER_ELEMENT = Regex("""addGuiElement\(""")
		val TEXTURE = Regex("""graphics\.blit\(""")
	}
}
