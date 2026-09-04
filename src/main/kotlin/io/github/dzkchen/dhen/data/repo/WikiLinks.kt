package io.github.dzkchen.dhen.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.util.Util
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object WikiLinks {
	const val ROOT = "https://hypixelskyblock.minecraft.wiki"

	private const val SEARCH = "$ROOT/index.php?search="
	private const val SCOPE = "&scope=internal"
	private const val UNDERSCORE = '_'
	private const val ENCODED_APOSTROPHE = "%27"
	private const val ENCODED_QUESTION = "%3F"

	fun search(term: String): String = SEARCH + URLEncoder.encode(term, StandardCharsets.UTF_8) + SCOPE

	fun page(title: String): String {
		val trimmed = title.trim()
		return if (trimmed.isEmpty()) ROOT else "$ROOT/${escaped(trimmed)}"
	}

	suspend fun open(url: String) = withContext(Dispatchers.IO) { Util.getPlatform().openUri(url) }

	fun article(id: String): String? {
		if (id.isEmpty()) return null
		return ItemRepo.item(id)?.info?.firstOrNull { it.startsWith(ROOT) }
	}

	private fun escaped(title: String): String {
		val built = StringBuilder(title.length)
		for (character in title) {
			when (character) {
				' ' -> built.append(UNDERSCORE)
				'\'' -> built.append(ENCODED_APOSTROPHE)
				'?' -> built.append(ENCODED_QUESTION)
				else -> built.append(character)
			}
		}
		return built.toString()
	}
}
