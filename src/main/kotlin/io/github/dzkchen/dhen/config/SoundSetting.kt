package io.github.dzkchen.dhen.config

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import java.util.Locale

class SoundSetting(
	name: String,
	override val default: SoundEvent,
	description: String = ""
) : Setting<SoundEvent>(name, description) {

	override var value: SoundEvent = default

	fun select(identifier: Identifier) {
		value = requireNotNull(BuiltInRegistries.SOUND_EVENT.getValue(identifier)) {
			"Unknown sound identifier: $identifier"
		}
	}

	companion object {
		val options: List<SoundOption> by lazy {
			BuiltInRegistries.SOUND_EVENT
				.map { sound -> SoundOption(sound, prettyName(sound.location())) }
				.sortedByDescending(SoundOption::name)
		}

		fun prettyName(identifier: Identifier): String {
			val parts = identifier.path.split('.', '_')
			val shown = if (parts.size <= PRETTY_PARTS) parts else parts.takeLast(PRETTY_PARTS)
			return shown.joinToString("_") { it.uppercase(Locale.ROOT) }
		}

		private const val PRETTY_PARTS = 3
	}
}

data class SoundOption(
	val sound: SoundEvent,
	val name: String
) {
	val identifier: Identifier
		get() = sound.location()
}
