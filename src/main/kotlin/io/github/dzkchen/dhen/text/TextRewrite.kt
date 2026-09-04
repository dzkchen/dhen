package io.github.dzkchen.dhen.text

import io.github.dzkchen.dhen.Dhen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

private const val STRING_LIMIT = 1000
private const val SEQUENCE_LIMIT = 256
private const val COMPONENT_LIMIT = 256
private const val INITIAL_CACHE = 64
private const val LOAD_FACTOR = 0.75f

internal class Compiled(val generation: Int, val table: RewriteTable?)

internal object TextRewrite {
	@Volatile
	private var table: RewriteTable? = null
	@Volatile
	private var replacements: List<Rewrite> = emptyList()
	@Volatile
	private var names: List<Rewrite> = emptyList()

	private val compiles = AtomicInteger()

	private val strings = object : LinkedHashMap<String, String>(INITIAL_CACHE, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > STRING_LIMIT
	}

	private val sequences =
		object : LinkedHashMap<FormattedCharSequence, FormattedCharSequence>(INITIAL_CACHE, LOAD_FACTOR, true) {
			override fun removeEldestEntry(
				eldest: MutableMap.MutableEntry<FormattedCharSequence, FormattedCharSequence>
			): Boolean = size > SEQUENCE_LIMIT
		}

	private val components = object : LinkedHashMap<FormattedCharSequence, Component>(INITIAL_CACHE, LOAD_FACTOR, true) {
		override fun removeEldestEntry(
			eldest: MutableMap.MutableEntry<FormattedCharSequence, Component>
		): Boolean = size > COMPONENT_LIMIT
	}

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@JvmStatic
	fun rewriting(): Boolean = table != null

	@JvmStatic
	fun string(text: String): String {
		val built = table ?: return text
		if (text.isEmpty()) return text
		strings[text]?.let { return it }
		return guarded(text) { built.replace(text).also { strings[text] = it } }
	}

	@JvmStatic
	fun sequence(text: FormattedCharSequence): FormattedCharSequence {
		val built = table ?: return text
		sequences[text]?.let { return it }
		return guarded(text) {
			val replaced = built.replace(text)
			if (replaced !== text) sequences[text] = replaced
			replaced
		}
	}

	@JvmStatic
	fun component(text: Component): Component {
		val built = table ?: return text
		val visual = text.visualOrderText
		components[visual]?.let { return it }
		return guarded(text) {
			val replaced = built.replace(text)
			if (replaced !== text) components[visual] = replaced
			replaced
		}
	}

	@JvmStatic
	fun wrapping(text: FormattedText): FormattedText {
		if (Language.getInstance().isDefaultRightToLeft) return text
		if (text is Component) return component(text)
		val built = table ?: return text
		return guarded(text) { built.replace(text) }
	}

	fun compileReplacements(entries: List<Rewrite>): Compiled {
		replacements = entries
		return compile()
	}

	fun rebuildNames(entries: List<Rewrite>) {
		names = entries
		install(compile())
	}

	fun uninstall() {
		replacements = emptyList()
		names = emptyList()
		install(compile())
	}

	fun install(compiled: Compiled) {
		if (compiled.generation != compiles.get()) return
		table = compiled.table
		strings.clear()
		sequences.clear()
		components.clear()
	}

	private fun compile(): Compiled {
		val generation = compiles.incrementAndGet()
		val all = replacements + names
		return Compiled(generation, if (all.isEmpty()) null else RewriteTable(all))
	}

	private inline fun <T> guarded(fallback: T, block: () -> T): T =
		try {
			block()
		} catch (throwable: Throwable) {
			log.error("Text rewrite failed and stood down", throwable)
			uninstall()
			fallback
		}
}
