package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.features.dungeon.DungeonClass
import java.util.Locale

internal sealed interface HotkeyContext {
	fun test(): Boolean

	fun format(): String
}

internal data object Anywhere : HotkeyContext {
	override fun test(): Boolean = true

	override fun format(): String = ALWAYS
}

internal class OnIsland(val islands: Set<Island>) : HotkeyContext {
	override fun test(): Boolean = SkyBlockLocation.island in islands

	override fun format(): String = "$ISLAND:" + islands.joinToString(",") { wire(it.name) }
}

internal class AsClass(val classes: Set<DungeonClass>) : HotkeyContext {
	override fun test(): Boolean = Hotkeys.ownClass in classes

	override fun format(): String = "$DUNGEON_CLASS:" + classes.joinToString(",") { wire(it.name) }
}

internal class AllOf(val parts: List<HotkeyContext>) : HotkeyContext {
	override fun test(): Boolean = parts.all { it.test() }

	override fun format(): String = parts.joinToString(" & ") { nested(it) }
}

internal class AnyOf(val parts: List<HotkeyContext>) : HotkeyContext {
	override fun test(): Boolean = parts.any { it.test() }

	override fun format(): String = parts.joinToString(" | ") { nested(it) }
}

internal class Inverted(val child: HotkeyContext) : HotkeyContext {
	override fun test(): Boolean = !child.test()

	override fun format(): String = "!" + nested(child)
}

internal fun parseHotkeyContext(text: String): HotkeyContext? {
	val reader = ContextReader(text)
	val context = reader.readAny() ?: return null
	return if (reader.done()) context else null
}

private class ContextReader(private val text: String) {
	private var at = 0

	fun done(): Boolean {
		skipSpace()
		return at >= text.length
	}

	fun readAny(): HotkeyContext? {
		val parts = ArrayList<HotkeyContext>(1)
		parts += readAll() ?: return null
		while (accept('|')) parts += readAll() ?: return null
		return if (parts.size == 1) parts[0] else AnyOf(parts)
	}

	private fun readAll(): HotkeyContext? {
		val parts = ArrayList<HotkeyContext>(1)
		parts += readUnary() ?: return null
		while (accept('&')) parts += readUnary() ?: return null
		return if (parts.size == 1) parts[0] else AllOf(parts)
	}

	private fun readUnary(): HotkeyContext? {
		if (accept('!')) return Inverted(readUnary() ?: return null)
		if (accept('(')) {
			val inner = readAny() ?: return null
			return if (accept(')')) inner else null
		}
		return readAtom()
	}

	private fun readAtom(): HotkeyContext? {
		val word = readWord() ?: return null
		if (word == ALWAYS) return Anywhere
		if (!accept(':')) return null
		val names = readWord()?.split(',')?.filter { it.isNotEmpty() } ?: return null
		if (names.isEmpty()) return null
		return when (word) {
			ISLAND -> OnIsland(names.mapTo(LinkedHashSet()) { island(it) ?: return null })
			DUNGEON_CLASS -> AsClass(names.mapTo(LinkedHashSet()) { dungeonClass(it) ?: return null })
			else -> null
		}
	}

	private fun readWord(): String? {
		skipSpace()
		val start = at
		while (at < text.length && (text[at].isLetterOrDigit() || text[at] == '_' || text[at] == ',')) at++
		return if (at == start) null else text.substring(start, at).lowercase(Locale.ROOT)
	}

	private fun accept(symbol: Char): Boolean {
		skipSpace()
		if (at >= text.length || text[at] != symbol) return false
		at++
		return true
	}

	private fun skipSpace() {
		while (at < text.length && text[at].isWhitespace()) at++
	}
}

private fun island(name: String): Island? =
	Island.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

private fun dungeonClass(name: String): DungeonClass? =
	DungeonClass.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

private fun wire(name: String): String = name.lowercase(Locale.ROOT)

private fun nested(context: HotkeyContext): String =
	if (context is AllOf || context is AnyOf) "(${context.format()})" else context.format()

private const val ALWAYS = "always"
private const val ISLAND = "island"
private const val DUNGEON_CLASS = "class"
