package io.github.dzkchen.dhen.text

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val GRINNING = "😀"
private const val UNDEAD = "\uE084"
private const val SKELETAL = "\uE081"

private fun rewrite(find: String, replacement: String) = Rewrite(find, Component.literal(replacement))

private fun icon(find: String, word: String) = Rewrite(find, Component.literal(word), leadingRunOnly = true)

private fun plain(sequence: FormattedCharSequence): String {
	val builder = StringBuilder()
	sequence.accept { _, _, point ->
		builder.appendCodePoint(point)
		true
	}
	return builder.toString()
}

private fun styles(sequence: FormattedCharSequence): List<Style> {
	val collected = mutableListOf<Style>()
	sequence.accept { _, style, _ ->
		collected += style
		true
	}
	return collected
}

class RewriteTableTest {
	@BeforeEach
	fun resetSeam() {
		CustomNames.clear()
		TextRewrite.install(TextRewrite.compileReplacements(emptyList()))
	}

	@Test
	fun `a match restarts the scan at the root so overlapping keys do not chain`() {
		val table = RewriteTable(listOf(rewrite("abc", "X"), rewrite("cde", "Y")))

		assertEquals("Xde", table.replace("abcde"))
	}

	@Test
	fun `a longer key wins over the shorter key it begins with`() {
		val table = RewriteTable(listOf(rewrite("hello", "hi"), rewrite("hello world", "howdy")))

		assertEquals("howdy", table.replace("hello world"))
		assertEquals("hi there", table.replace("hello there"))
	}

	@Test
	fun `an unless-followed-by blocker leaves that one occurrence alone`() {
		val table = RewriteTable(
			listOf(Rewrite("Noamm", Component.literal("Dhen"), unlessFollowedBy = listOf("Addons")))
		)

		assertEquals("NoammAddons", table.replace("NoammAddons"))
		assertEquals("Dhen9", table.replace("Noamm9"))
	}

	@Test
	fun `a word bounded key skips matches glued to letters digits or underscores`() {
		val table = RewriteTable(listOf(Rewrite("Steve", Component.literal("Sam"), wordBounded = true)))

		assertEquals("Sam waves", table.replace("Steve waves"))
		assertEquals("[Sam]", table.replace("[Steve]"))
		assertEquals("Steve9", table.replace("Steve9"))
		assertEquals("xSteve", table.replace("xSteve"))
		assertEquals("Steve_", table.replace("Steve_"))
	}

	@Test
	fun `an astral key matches whole and leaves a lone pair alone`() {
		val table = RewriteTable(listOf(rewrite(GRINNING, "smile"), rewrite("b", "B")))

		assertEquals("a smile B", table.replace("a $GRINNING b"))
	}

	@Test
	fun `an astral character beside a key survives the rebuild`() {
		val table = RewriteTable(listOf(rewrite("cat", "dog")))

		assertEquals("$GRINNING dog $GRINNING", table.replace("$GRINNING cat $GRINNING"))
	}

	@Test
	fun `a replacement inherits the matched run's style and overrides it with its own`() {
		val red = Component.literal("dog").withStyle(ChatFormatting.RED)
		val table = RewriteTable(listOf(Rewrite("cat", red)))
		val source = Component.literal("cat").withStyle(ChatFormatting.GREEN).withStyle(ChatFormatting.BOLD)

		val replaced = styles(table.replace(source.visualOrderText))

		assertEquals(3, replaced.size)
		assertTrue(replaced[0].isBold)
		assertEquals(red.style.color, replaced[0].color)
	}

	@Test
	fun `the sequence path keeps the styles on either side of a match`() {
		val table = RewriteTable(listOf(Rewrite("cat", Component.literal("dog").withStyle(ChatFormatting.RED))))
		val source = Component.literal("a ").withStyle(ChatFormatting.BLUE)
			.append(Component.literal("cat").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(" b").withStyle(ChatFormatting.BLUE))

		val replaced = table.replace(source.visualOrderText)

		assertEquals("a dog b", plain(replaced))
		val blue = Component.literal("x").withStyle(ChatFormatting.BLUE).style.color
		assertEquals(blue, styles(replaced)[0].color)
		assertEquals(blue, styles(replaced)[6].color)
	}

	@Test
	fun `the component path rewrites and hands an untouched component straight back`() {
		val table = RewriteTable(listOf(rewrite("cat", "dog")))
		val untouched: Component = Component.literal("one hamster")

		assertEquals("one dog", table.replace(Component.literal("one cat") as Component).string)
		assertSame(untouched, table.replace(untouched))
	}

	@Test
	fun `a leading run replacement stops once one match is followed by a space`() {
		val table = RewriteTable(listOf(icon(UNDEAD, "Undead "), icon("$UNDEAD ", "Undead ")))

		assertEquals("Undead Zombie $UNDEAD", table.replace("$UNDEAD Zombie $UNDEAD"))
	}

	@Test
	fun `two icons in one run both read as words before the run closes`() {
		val table = RewriteTable(
			listOf(
				icon(UNDEAD, "Undead "),
				icon("$UNDEAD ", "Undead "),
				icon(SKELETAL, "Skeletal "),
				icon("$SKELETAL ", "Skeletal ")
			)
		)

		assertEquals("Undead Skeletal Zombie", table.replace("$UNDEAD$SKELETAL Zombie"))
	}

	@Test
	fun `a rebuilt table replaces the answers the old one cached`() {
		TextRewrite.install(TextRewrite.compileReplacements(listOf(rewrite("cat", "dog"))))
		assertEquals("one dog", TextRewrite.string("one cat"))

		TextRewrite.install(TextRewrite.compileReplacements(listOf(rewrite("cat", "fox"))))

		assertEquals("one fox", TextRewrite.string("one cat"))
	}

	@Test
	fun `a name table only rewrites a name standing on its own`() {
		CustomNames.rebuild(mapOf("Steve" to Component.literal("Sam")))

		assertEquals("Sam joined", TextRewrite.string("Steve joined"))
		assertEquals("Steven joined", TextRewrite.string("Steven joined"))

		CustomNames.clear()
		assertEquals("Steve joined", TextRewrite.string("Steve joined"))
	}

	@Test
	fun `clearing one source leaves the other source rewriting`() {
		TextRewrite.install(TextRewrite.compileReplacements(listOf(rewrite("cat", "dog"))))
		CustomNames.rebuild(mapOf("Steve" to Component.literal("Sam")))

		TextRewrite.install(TextRewrite.compileReplacements(emptyList()))

		assertEquals("one cat", TextRewrite.string("one cat"))
		assertEquals("Sam waves", TextRewrite.string("Steve waves"))
	}

	@Test
	fun `a longer key only shadows the shorter one where it would really fire`() {
		val table = RewriteTable(
			listOf(
				rewrite("Steve", "Bob"),
				Rewrite("Steven", Component.literal("Sam"), wordBounded = true)
			)
		)

		assertEquals("Bobns", table.replace("Stevens"))
		assertEquals("Sam waves", table.replace("Steven waves"))
	}

	@Test
	fun `a rebuild that finished late cannot overwrite the newer table`() {
		val stale = TextRewrite.compileReplacements(listOf(rewrite("cat", "dog")))
		CustomNames.rebuild(mapOf("Steve" to Component.literal("Sam")))

		TextRewrite.install(stale)

		assertEquals("Sam waves", TextRewrite.string("Steve waves"))
		assertEquals("one dog", TextRewrite.string("one cat"))
	}

	@Test
	fun `the seam stands down when nothing is registered`() {
		assertFalse(TextRewrite.rewriting())
	}

	@Test
	fun `the sequence cache hands the same replacement back for the same instance`() {
		TextRewrite.install(TextRewrite.compileReplacements(listOf(rewrite("cat", "dog"))))
		val source = Component.literal("one cat").visualOrderText

		val first = TextRewrite.sequence(source)
		val second = TextRewrite.sequence(source)

		assertSame(first, second)
		assertNotSame(source, first)
		assertEquals("one dog", plain(first))
	}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}
	}
}
