package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import java.nio.file.Path

internal fun soundStore(path: Path): ConfigStore = ConfigStore(
	path,
	CoroutineScope(Dispatchers.Unconfined),
	SoundManager.migrations,
	SoundManager.authoritative,
	debounce = {}
)

internal fun requestedField(instance: SoundInstance, member: String): Float =
	AbstractSoundInstance::class.java.getDeclaredField(member).apply { isAccessible = true }.getFloat(instance)
