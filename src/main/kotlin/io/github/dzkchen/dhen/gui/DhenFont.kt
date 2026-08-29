package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.font.DhenFontPack
import io.github.dzkchen.dhen.font.FontStore
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

internal object DhenFont {
	val id: Identifier = Identifier.fromNamespaceAndPath(Dhen.MOD_ID, "inter")

	private val inter = FontDescription.Resource(id)
	private val message = FontDescription.Resource(Identifier.fromNamespaceAndPath(Dhen.MOD_ID, "message"))
	private val messageStyle = Style.EMPTY.withFont(message)

	@Volatile
	private var enabled = true

	@Volatile
	private var lockedOff = false

	@Volatile
	private var selected = FontStore.INTER

	@Volatile
	private var face: FontDescription.Resource = inter

	@Volatile
	private var faceStyle: Style = Style.EMPTY.withFont(inter)

	@Volatile
	internal var revision = 0
		private set

	@JvmStatic
	fun resolve(description: FontDescription): FontDescription = when {
		description === message -> if (enabled) face else FontDescription.DEFAULT
		enabled && description == FontDescription.DEFAULT -> face
		else -> description
	}

	internal fun synchronize(enabled: Boolean, selected: String): Boolean {
		if (lockedOff || (enabled == this.enabled && selected == this.selected)) return false
		this.selected = selected
		return change(enabled, describe(selected))
	}

	internal fun latchOff(): Boolean {
		lockedOff = true
		return change(false, face)
	}

	internal fun resetForTest() {
		lockedOff = false
		selected = FontStore.INTER
		change(true, inter)
	}

	private fun describe(selected: String): FontDescription.Resource =
		FontStore.find(selected)?.let { FontDescription.Resource(DhenFontPack.font(it)) } ?: inter

	private fun change(enabled: Boolean, face: FontDescription.Resource): Boolean {
		if (this.enabled == enabled && this.face == face) return false
		val drawn = this.enabled || enabled
		this.enabled = enabled
		this.face = face
		faceStyle = Style.EMPTY.withFont(face)
		if (!drawn) return false
		revision++
		DhenType.fontChanged()
		return true
	}

	internal fun style() = if (enabled) faceStyle else Style.EMPTY

	internal fun messageStyle() = messageStyle
}
