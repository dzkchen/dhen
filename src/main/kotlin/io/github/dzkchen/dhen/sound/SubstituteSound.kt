package io.github.dzkchen.dhen.sound

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource

class SubstituteSound internal constructor(
	replacement: Identifier,
	volume: Float,
	pitch: Float
) : SimpleSoundInstance(
	replacement,
	SoundSource.UI,
	volume,
	pitch,
	SoundInstance.createUnseededRandom(),
	false,
	0,
	SoundInstance.Attenuation.NONE,
	0.0,
	0.0,
	0.0,
	true
)
