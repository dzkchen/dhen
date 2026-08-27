package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import io.github.dzkchen.dhen.json
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.joml.Matrix4f
import java.lang.reflect.Proxy

class DhenTypeTest {
	@BeforeEach
	@AfterEach
	fun resetSeam() {
		DhenFont.resetForTest()
		DhenType.invalidateMeasurements()
		DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false)
	}

	@Test
	fun `the text shadow stays one screen pixel however far the hud is scaled up`() {
		assertEquals(1f, DhenType.shadowOffset(1f))
		assertEquals(0.5f, DhenType.shadowOffset(2f))
		assertEquals(0.25f, DhenType.shadowOffset(4f))
		assertEquals(4f, DhenType.shadowOffset(0.25f))
	}

	@Test
	fun `a degenerate scale falls back to a single pixel instead of dividing by zero`() {
		assertEquals(1f, DhenType.shadowOffset(0f))
		assertEquals(1f, DhenType.shadowOffset(-2f))
		assertEquals(1f, DhenType.shadowOffset(Float.NaN))
	}

	@Test
	fun `world shadow submits the dim offset copy in an earlier order than the foreground`() {
		val submissions = mutableListOf<WorldTextSubmission>()
		val orderedCollectors = (0..1).associateWith { order ->
			Proxy.newProxyInstance(
				OrderedSubmitNodeCollector::class.java.classLoader,
				arrayOf(OrderedSubmitNodeCollector::class.java)
			) { _, method, arguments ->
				if (method.name != "submitText") throw UnsupportedOperationException(method.name)
				val submittedPose = arguments[0] as PoseStack
				submissions += WorldTextSubmission(order, arguments.copyOf(), Matrix4f(submittedPose.last().pose()))
				null
			} as OrderedSubmitNodeCollector
		}
		val requestedOrders = mutableListOf<Int>()
		val collector = Proxy.newProxyInstance(
			SubmitNodeCollector::class.java.classLoader,
			arrayOf(SubmitNodeCollector::class.java)
		) { _, method, arguments ->
			if (method.name != "order") throw UnsupportedOperationException(method.name)
			val order = arguments[0] as Int
			requestedOrders += order
			orderedCollectors.getValue(order)
		} as SubmitNodeCollector
		val pose = PoseStack()
		pose.translate(1.25f, -2.5f, 3.75f)
		val expectedPose = Matrix4f(pose.last().pose())
		val text = "collect submits"
		val expectedLabel = DhenType.styled(text).visualOrderText
		val color = 0x8080C0FF.toInt()
		val displayMode = Font.DisplayMode.POLYGON_OFFSET
		val lightCoords = 0x00120034

		DhenType.shadowedWorldText(
			collector,
			pose,
			text,
			10f,
			20f,
			color,
			displayMode,
			lightCoords
		)

		assertEquals(2, submissions.size)
		assertEquals(listOf(0, 1), requestedOrders)
		val shadow = submissions[0].arguments
		val foreground = submissions[1].arguments
		assertEquals(0, submissions[0].order)
		assertEquals(1, submissions[1].order)
		assertSame(pose, shadow[0])
		assertSame(pose, foreground[0])
		assertEquals(expectedPose, submissions[0].pose)
		assertEquals(expectedPose, submissions[1].pose)
		assertEquals(expectedPose, Matrix4f(pose.last().pose()))
		assertEquals(10.5f, shadow[1])
		assertEquals(20.5f, shadow[2])
		assertSame(expectedLabel, shadow[3])
		assertSame(expectedLabel, foreground[3])
		assertFalse(shadow[4] as Boolean)
		assertFalse(foreground[4] as Boolean)
		assertEquals(displayMode, shadow[5])
		assertEquals(displayMode, foreground[5])
		assertEquals(lightCoords, shadow[6])
		assertEquals(lightCoords, foreground[6])
		assertEquals(ARGB.scaleRGB(color, 0.25f), shadow[7])
		assertEquals(color, foreground[7])
		assertEquals(0, shadow[8])
		assertEquals(0, shadow[9])
		assertEquals(0, foreground[8])
		assertEquals(0, foreground[9])
		assertEquals(10f, foreground[1])
		assertEquals(20f, foreground[2])
	}

	@Test
	fun `styled text carries the bundled font`() {
		val font = DhenType.styled("Combat").style.font
		assertEquals(FontDescription.Resource(DhenType.fontId), font)
	}

	@Test
	fun `disabled Dhen text uses the empty vanilla style and enabled text uses Inter`() {
		DhenFont.synchronize(false)

		assertEquals(Style.EMPTY, DhenType.component("Combat").style)

		DhenFont.synchronize(true)

		assertEquals(FontDescription.Resource(DhenType.fontId), DhenType.component("Combat").style.font)
	}

	@Test
	fun `the enabled resolver replaces only Minecraft default font descriptions`() {
		val custom = FontDescription.Resource(Identifier.fromNamespaceAndPath("example", "custom"))
		val markerCollision = FontDescription.Resource(Identifier.fromNamespaceAndPath("dhen", "message"))
		val atlas = FontDescription.AtlasSprite(
			Identifier.fromNamespaceAndPath("example", "icons"),
			Identifier.fromNamespaceAndPath("example", "star")
		)

		assertEquals(FontDescription.Resource(DhenType.fontId), DhenFont.resolve(FontDescription.DEFAULT))
		assertSame(custom, DhenFont.resolve(custom))
		assertSame(markerCollision, DhenFont.resolve(markerCollision))
		assertSame(atlas, DhenFont.resolve(atlas))
	}

	@Test
	fun `the disabled resolver preserves Minecraft default`() {
		val explicitInter = FontDescription.Resource(DhenType.fontId)
		DhenFont.synchronize(false)

		assertSame(FontDescription.DEFAULT, DhenFont.resolve(FontDescription.DEFAULT))
		assertSame(explicitInter, DhenFont.resolve(explicitInter))
	}

	@Test
	fun `an existing Dhen chat component follows both font transitions`() {
		val message = DhenType.overWorld("Toggled Test Module")

		assertEquals(FontDescription.Resource(DhenType.fontId), DhenFont.resolve(message.style.font))

		DhenFont.synchronize(false)

		assertSame(FontDescription.DEFAULT, DhenFont.resolve(message.style.font))

		DhenFont.synchronize(true)

		assertEquals(FontDescription.Resource(DhenType.fontId), DhenFont.resolve(message.style.font))
	}

	@Test
	fun `latching the font off prevents settings from restoring it during the session`() {
		assertTrue(DhenFont.latchOff())
		val revision = DhenFont.revision

		assertFalse(DhenFont.synchronize(true))
		assertEquals(revision, DhenFont.revision)
		assertSame(FontDescription.DEFAULT, DhenFont.resolve(FontDescription.DEFAULT))
	}

	@Test
	fun `a font transition rebuilds shared styled components while the same state does nothing`() {
		val enabled = DhenType.styled("Combat")
		val revision = DhenFont.revision

		assertFalse(DhenFont.synchronize(true))
		assertEquals(revision, DhenFont.revision)
		assertSame(enabled, DhenType.styled("Combat"))

		assertTrue(DhenFont.synchronize(false))
		val disabled = DhenType.styled("Combat")
		assertNotSame(enabled, disabled)
		assertEquals(Style.EMPTY, disabled.style)
	}

	@Test
	fun `repeating a string reuses its styled component`() {
		val first = DhenType.styled("Combat")
		assertSame(first, DhenType.styled("Combat"))
		assertSame(first, DhenType.styled(StringBuilder("Combat").toString()))
	}

	@Test
	fun `chat feedback styling stays off the cached path`() {
		val cached = DhenType.styled("Toggled Test Module")
		val sent = DhenType.component("Toggled Test Module")

		assertNotSame(cached, sent)
		assertEquals(cached.style, sent.style)
	}

	@Test
	fun `a resource reload keeps the styled components it only re-measures`() {
		val before = DhenType.styled("Combat")
		DhenType.invalidateMeasurements()

		assertSame(before, DhenType.styled("Combat"))
	}

	@Test
	fun `the cache never grows past its limit`() {
		val cold = DhenType.styled("row 0")
		for (row in 1..DhenType.CACHE_LIMIT) DhenType.styled("row $row")

		assertNotSame(cold, DhenType.styled("row 0"))
	}

	@Test
	fun `a string still in use survives a flood of one-off strings`() {
		val label = DhenType.styled("Strength")
		for (value in 1..4 * DhenType.CACHE_LIMIT) {
			DhenType.styled("$value.25")
			DhenType.styled("Strength")
		}

		assertSame(label, DhenType.styled("Strength"))
	}

	@Test
	fun `a memo measures a string once and re-measures only when it changes`() {
		val font = StubFont()
		val memo = DhenType.memo()

		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(1, font.measurements)

		assertEquals(4 * STUB_GLYPH_WIDTH, memo.width(font, "abcd"))
		assertEquals(2, font.measurements)
	}

	@Test
	fun `invalidating a memo buys exactly one more measurement`() {
		val font = StubFont()
		val memo = DhenType.memo()
		memo.width(font, "abc")

		memo.invalidate()

		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(2, font.measurements)
	}

	@Test
	fun `a memo remeasures once for each font revision without joining the shared cache`() {
		val font = StubFont()
		val memo = DhenType.memo()
		memo.width(font, "abc")

		DhenFont.synchronize(false)

		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(2, font.measurements)
		assertFalse(DhenFont.synchronize(false))
		assertEquals(3 * STUB_GLYPH_WIDTH, memo.width(font, "abc"))
		assertEquals(2, font.measurements)
	}

	@Test
	fun `a readout that changes every frame never reaches the shared cache`() {
		val font = StubFont()
		val stable = DhenType.styled(MEMO_NEIGHBOUR)
		val memo = DhenType.memo()

		for (tick in 1..DhenType.CACHE_LIMIT + 1) memo.width(font, "$tick.25")

		assertSame(stable, DhenType.styled(MEMO_NEIGHBOUR))
	}

	@Test
	fun `a memo keeps its elision until the text or the room moves`() {
		val font = StubFont()
		val memo = DhenType.memo()

		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
		val settled = font.measurements
		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
		assertEquals(settled, font.measurements)

		assertEquals(FITTING, memo.fit(font, FITTING, 6 * STUB_GLYPH_WIDTH))
		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
	}

	@Test
	fun `a memoized elision rides out a room that moves inside the band it holds for`() {
		val font = StubFont()
		val memo = DhenType.memo()

		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
		val settled = font.measurements

		for (room in 4 * STUB_GLYPH_WIDTH until 5 * STUB_GLYPH_WIDTH) {
			assertEquals("abc…", memo.fit(font, FITTING, room))
		}

		assertEquals(settled, font.measurements)
	}

	@Test
	fun `an elision shortens and lengthens the moment the room leaves the band`() {
		val font = StubFont()
		val memo = DhenType.memo()
		memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH)

		assertEquals("ab…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH - 1))
		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
		assertEquals("abcd…", memo.fit(font, FITTING, 5 * STUB_GLYPH_WIDTH))
		assertEquals(FITTING, memo.fit(font, FITTING, 6 * STUB_GLYPH_WIDTH))
	}

	@Test
	fun `a memo handed another string in between still holds the elision it answers with`() {
		val font = StubFont()
		val memo = DhenType.memo()
		assertEquals("abc…", memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))

		memo.width(font, FITTING)

		val label = memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH)
		assertEquals("abc…", label)
		assertEquals(4 * STUB_GLYPH_WIDTH, memo.width(font, label))
	}

	@Test
	fun `elide reports the rooms its answer survives, in both directions`() {
		val band = RoomBand()

		assertEquals("abc…", elide(FITTING, 45, fromEnd = false, measure = ::tenPerCharacter, band = band))
		assertTrue(band.holds(40))
		assertTrue(band.holds(49))
		assertFalse(band.holds(39))
		assertFalse(band.holds(50))

		assertEquals("…def", elide(FITTING, 45, fromEnd = true, measure = ::tenPerCharacter, band = band))
		assertTrue(band.holds(40))
		assertFalse(band.holds(39))
		assertFalse(band.holds(50))
	}

	@Test
	fun `the answers at either end of the range report a band too`() {
		val band = RoomBand()

		assertSame(FITTING, elide(FITTING, 60, fromEnd = false, measure = ::tenPerCharacter, band = band))
		assertTrue(band.holds(60))
		assertTrue(band.holds(600))
		assertFalse(band.holds(59))

		assertEquals("…", elide(FITTING, 15, fromEnd = false, measure = ::tenPerCharacter, band = band))
		assertTrue(band.holds(10))
		assertFalse(band.holds(9))
		assertFalse(band.holds(20))

		assertEquals("", elide(FITTING, 5, fromEnd = false, measure = ::tenPerCharacter, band = band))
		assertTrue(band.holds(0))
		assertFalse(band.holds(10))
	}

	@Test
	fun `a settled label costs nothing to fit and measure again`() {
		val font = StubFont()
		val memo = DhenType.memo()
		memo.width(font, memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH))
		val settled = font.measurements

		assertEquals(4 * STUB_GLYPH_WIDTH, memo.width(font, memo.fit(font, FITTING, 4 * STUB_GLYPH_WIDTH)))

		assertEquals(settled, font.measurements)
	}

	@Test
	fun `flipping a vanilla font option asks for one invalidation`() {
		DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false)

		assertTrue(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = false))
		assertFalse(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = false))
		assertTrue(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = true))
		assertFalse(DhenType.fontOptionsChanged(forceUnicode = true, japaneseGlyphVariants = true))
		assertTrue(DhenType.fontOptionsChanged(forceUnicode = false, japaneseGlyphVariants = false))
	}

	@Test
	fun `text that fits its room is left exactly as it was`() {
		assertSame(FITTING, elide(FITTING, 60, fromEnd = false, measure = ::tenPerCharacter))
		assertSame(FITTING, elide(FITTING, 60, fromEnd = true, measure = ::tenPerCharacter))
	}

	@Test
	fun `text too wide for its room loses its tail to an ellipsis`() {
		assertEquals("abc…", elide(FITTING, 40, fromEnd = false, measure = ::tenPerCharacter))
		assertEquals("a…", elide(FITTING, 25, fromEnd = false, measure = ::tenPerCharacter))
	}

	@Test
	fun `text being typed into keeps its end so the caret stays honest`() {
		assertEquals("…def", elide(FITTING, 40, fromEnd = true, measure = ::tenPerCharacter))
		assertEquals("…f", elide(FITTING, 25, fromEnd = true, measure = ::tenPerCharacter))
	}

	@Test
	fun `a character built from two code units is dropped whole rather than halved`() {
		val kept = elide("a${ROCKET}bc", 35, fromEnd = false, measure = ::tenPerCharacter)
		val tail = elide("ab${ROCKET}c", 35, fromEnd = true, measure = ::tenPerCharacter)

		assertEquals("a…", kept)
		assertEquals("…c", tail)
		assertTrue(kept.none { it.isSurrogate() })
		assertTrue(tail.none { it.isSurrogate() })
	}

	@Test
	fun `room for nothing but the ellipsis draws the ellipsis, and less draws nothing`() {
		assertEquals("…", elide(FITTING, 10, fromEnd = false, measure = ::tenPerCharacter))
		assertEquals("", elide(FITTING, 5, fromEnd = false, measure = ::tenPerCharacter))
		assertEquals("", elide(FITTING, 0, fromEnd = false, measure = ::tenPerCharacter))
	}

	private fun tenPerCharacter(text: String): Int = text.length * 10

	@Test
	fun `no dhen code draws or measures text outside the seam`() {
		val scanned = SourceScan.files()
		assertTrue(scanned.any { it.name == SEAM }) { "scan missed the sources at ${SourceScan.MAIN.absolutePath}" }
		assertTrue(scanned.any { it.extension == "java" }) { "scan missed the mixins at ${SourceScan.MAIN.absolutePath}" }

		val offenders = SourceScan.offenders(scanned, SEAM, RAW_TEXT)

		assertTrue(offenders.isEmpty()) {
			"Dhen text must go through $SEAM, but these bypass it:\n${offenders.joinToString("\n")}"
		}
	}

	@Test
	fun `the font definition sits where the font id resolves it`() {
		assertEquals(DEFINITION, "assets/${DhenType.fontId.namespace}/font/${DhenType.fontId.path}.json")
	}

	@Test
	fun `the font definition declares the bundled truetype provider first`() {
		val providers = definition().getAsJsonArray("providers")
		assertTrue(providers.size() > 1, "the definition needs vanilla fallbacks behind the bundled face")

		val ttf = providers[0].asJsonObject
		assertEquals("ttf", ttf.get("type").asString)
		assertEquals("${DhenType.fontId.namespace}:$FACE", ttf.get("file").asString)
		assertTrue(ttf.get("size").asFloat > 0f, "a zero size bakes invisible glyphs")
		assertTrue(ttf.get("oversample").asFloat >= 1f, "oversample below 1 blurs the face")
	}

	@Test
	fun `the fallbacks behind the bundled face are vanilla references`() {
		val providers = definition().getAsJsonArray("providers")

		for (index in 1 until providers.size()) {
			val fallback = providers[index].asJsonObject
			assertEquals("reference", fallback.get("type").asString)
			assertTrue(fallback.get("id").asString.startsWith("minecraft:"))
		}
	}

	@Test
	fun `the bundled face is a truetype file the vanilla provider accepts`() {
		val face = resource("assets/${DhenType.fontId.namespace}/font/$FACE")

		assertTrue(face.size > MINIMUM_FACE_BYTES, "the bundled face is too small to be a real font")
		assertEquals(TRUETYPE_TAG.toList(), face.take(TRUETYPE_TAG.size))
	}

	private fun definition(): JsonObject =
		json(String(resource(DEFINITION), Charsets.UTF_8))

	private fun resource(path: String): ByteArray {
		val stream = javaClass.classLoader.getResourceAsStream(path)
		assertNotNull(stream, "missing bundled resource: $path")
		return stream!!.use { it.readBytes() }
	}

	private companion object {
		data class WorldTextSubmission(val order: Int, val arguments: Array<Any?>, val pose: Matrix4f)

		const val FITTING = "abcdef"
		const val MEMO_NEIGHBOUR = "Cooldown"
		const val ROCKET = "🚀"
		const val SEAM = "DhenType.kt"
		const val DEFINITION = "assets/dhen/font/inter.json"
		const val FACE = "inter.ttf"
		const val MINIMUM_FACE_BYTES = 1024
		val TRUETYPE_TAG = byteArrayOf(0x00, 0x01, 0x00, 0x00)
		val RAW_TEXT = Regex("""graphics\.text\(|font\.width\(|font\.lineHeight|submitText\(""")
	}
}
