package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

internal object DhenFont {
	val id: Identifier = Identifier.fromNamespaceAndPath(Dhen.MOD_ID, "inter")

	private val inter = FontDescription.Resource(id)
	private val interStyle = Style.EMPTY.withFont(inter)
	private val message = FontDescription.Resource(Identifier.fromNamespaceAndPath(Dhen.MOD_ID, "message"))
	private val messageStyle = Style.EMPTY.withFont(message)

	@Volatile
	private var enabled = true

	@Volatile
	private var lockedOff = false

	@Volatile
	internal var revision = 0
		private set

	@JvmStatic
	fun resolve(description: FontDescription): FontDescription = when {
		description === message -> if (enabled) inter else FontDescription.DEFAULT
		enabled && description == FontDescription.DEFAULT -> inter
		else -> description
	}

	internal fun synchronize(enabled: Boolean): Boolean {
		return change(enabled && !lockedOff)
	}

	internal fun latchOff(): Boolean {
		lockedOff = true
		return change(false)
	}

	internal fun resetForTest() {
		lockedOff = false
		change(true)
	}

	private fun change(enabled: Boolean): Boolean {
		if (this.enabled == enabled) return false
		this.enabled = enabled
		revision++
		DhenType.fontChanged()
		return true
	}

	internal fun style() = if (enabled) interStyle else Style.EMPTY

	internal fun messageStyle() = messageStyle
}
