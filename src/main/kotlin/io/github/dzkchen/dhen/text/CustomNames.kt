package io.github.dzkchen.dhen.text

import net.minecraft.network.chat.Component

internal object CustomNames {
	fun rebuild(names: Map<String, Component>) {
		TextRewrite.rebuildNames(names.map { (name, replacement) -> Rewrite(name, replacement, wordBounded = true) })
	}

	fun clear() = rebuild(emptyMap())
}
