package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent

class SoundSettings internal constructor(
	name: String,
	default: SoundEvent,
	preview: (SoundEvent, Float, Float) -> Unit
) {
	val sound = SoundSetting(name, default, "The internal Minecraft sound key to play.")
	val volume = NumberSetting("Volume", 0.5, 0.0, 1.0, 0.1, "The loudness of the sound.")
	val pitch = NumberSetting("Pitch", 1.0, 0.0, 2.0, 0.1, "The pitch and frequency of the sound.")
	val play = ActionSetting(
		"Play Sound",
		default = { preview(sound.value, volume.amount.toFloat(), pitch.amount.toFloat()) },
		description = "Plays the current sound configuration."
	)
}

fun Module.createSoundSettings(name: String, default: SoundEvent): SoundSettings =
	createSoundSettings(name, default, SoundPreview::play)

internal fun Module.createSoundSettings(
	name: String,
	default: SoundEvent,
	preview: (SoundEvent, Float, Float) -> Unit
): SoundSettings = SoundSettings(name, default, preview).also { settings ->
	registerSetting(settings.sound)
	registerSetting(settings.volume)
	registerSetting(settings.pitch)
	registerSetting(settings.play)
}

internal object SoundPreview {
	fun play(sound: SoundEvent, volume: Float, pitch: Float) {
		val client = Minecraft.getInstance()
		client.execute {
			previewInstances(sound, volume, pitch, client.soundManager::play)
		}
	}

	internal const val PREVIEW_COUNT = 5
}

internal fun previewInstances(
	sound: SoundEvent,
	volume: Float,
	pitch: Float,
	play: (SimpleSoundInstance) -> Unit
) {
	repeat(SoundPreview.PREVIEW_COUNT) {
		play(SimpleSoundInstance.forUI(sound, pitch, volume))
	}
}
