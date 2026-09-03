package io.github.dzkchen.dhen.command

import net.minecraft.resources.Identifier

interface SoundCommands {
	fun openManager(): String

	fun setVolume(sound: Identifier, percent: Int): String

	companion object {
		const val UNAVAILABLE = "The sound manager is not available."

		val NONE: SoundCommands = object : SoundCommands {
			override fun openManager(): String = UNAVAILABLE

			override fun setVolume(sound: Identifier, percent: Int): String = UNAVAILABLE
		}
	}
}
