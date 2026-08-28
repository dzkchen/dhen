package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.createSoundSettings
import io.github.dzkchen.dhen.config.previewInstances
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object ArrowHitSound : Module(
	name = "Arrow Hit Sound",
	category = Category.QOL,
	description = "Replaces the sound played when an arrow hits a player."
) {
	internal val soundSettings = createSoundSettings(
		"Sound",
		SoundEvents.NOTE_BLOCK_HARP.value(),
		::dispatchReplacement
	)
	private val replacementLock = Any()
	@Volatile
	private var replacing = false

	@JvmStatic
	fun onSoundPlay(sound: SoundInstance): Boolean {
		if (!enabled || replacing || sound.identifier != SoundEvents.ARROW_HIT_PLAYER.location()) return false
		soundSettings.play.value()
		return true
	}

	private fun dispatchReplacement(sound: SoundEvent, volume: Float, pitch: Float) {
		val client = Minecraft.getInstance()
		client.execute {
			playReplacement(sound, volume, pitch, client.soundManager::play)
		}
	}

	internal fun playReplacement(
		sound: SoundEvent,
		volume: Float,
		pitch: Float,
		play: (SimpleSoundInstance) -> Unit
	) = synchronized(replacementLock) {
		if (replacing) return@synchronized
		replacing = true
		try {
			previewInstances(sound, volume, pitch, play)
		} finally {
			replacing = false
		}
	}
}
