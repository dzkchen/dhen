package io.github.dzkchen.dhen.privacy

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object KeybindDefaults {
	private val lock = Any()

	@Volatile
	private var defaults: Map<String, InputConstants.Key> = emptyMap()

	@Volatile
	private var ready = false

	@Volatile
	private var generation = 0L

	@JvmStatic
	fun defaultKey(name: String): InputConstants.Key? {
		if (!ready) initialize(Minecraft.getInstance().options.keyMappings)
		return defaults[name]
	}

	@JvmStatic
	fun generation(): Long = generation

	@JvmStatic
	fun reset() {
		synchronized(lock) {
			defaults = emptyMap()
			ready = false
			generation++
		}
	}

	internal fun initialize(keyMappings: Array<KeyMapping>) {
		if (ready) return
		synchronized(lock) {
			if (ready) return
			val discovered = HashMap<String, InputConstants.Key>()
			for (mapping in keyMappings) {
				val name = mapping.name
				if (LanguageKeys.isVanillaKey(name)) discovered[name] = mapping.defaultKey
			}
			defaults = discovered.toMap()
			ready = true
		}
	}
}
