package io.github.dzkchen.dhen.privacy

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object PackControls {
	private const val HYPIXEL_PACK = "Hypixel SkyBlock"
	private const val SERVER_PACK_PREFIX = "server/"
	private const val PATH_SEPARATOR = '/'

	@Volatile
	var unpinning = false

	@Volatile
	var sinkingHypixelPack = false

	@Volatile
	var skippingMismatchScreen = false

	@Volatile
	var ignoringCompatibility = false

	@Volatile
	var serverPacksFirst = false

	private val optional = ConcurrentHashMap.newKeySet<UUID>()
	private val unselected = ConcurrentHashMap.newKeySet<UUID>()

	@JvmStatic
	fun unpinsPacks(): Boolean = unpinning

	@JvmStatic
	fun sinksHypixelPack(): Boolean = sinkingHypixelPack

	@JvmStatic
	fun isHypixelPack(description: String): Boolean = description == HYPIXEL_PACK

	@JvmStatic
	fun skipsMismatchScreen(): Boolean = skippingMismatchScreen

	@JvmStatic
	fun ignoresCompatibility(): Boolean = ignoringCompatibility

	@JvmStatic
	fun loadsServerPacksFirst(): Boolean = serverPacksFirst

	@JvmStatic
	fun isServerPack(packId: String): Boolean = packId.startsWith(SERVER_PACK_PREFIX)

	@JvmStatic
	fun offersUnselect(packId: String): Boolean {
		if (!unpinning) return false
		return optional.contains(packUuid(packId) ?: return false)
	}

	@JvmStatic
	fun staysUnselected(packId: String): Boolean {
		if (!unpinning) return false
		return unselected.contains(packUuid(packId) ?: return false)
	}

	@JvmStatic
	fun recordSelection(packId: String, selected: Boolean) {
		val id = packUuid(packId) ?: return
		if (id !in optional) return
		if (selected) unselected -= id else unselected += id
	}

	@JvmStatic
	fun pushed(id: UUID, required: Boolean) {
		unselected -= id
		if (required) optional -= id else optional += id
	}

	fun forgetSelections() {
		optional.clear()
		unselected.clear()
	}

	private fun packUuid(packId: String): UUID? {
		if (!isServerPack(packId)) return null
		val lastSlash = packId.lastIndexOf(PATH_SEPARATOR)
		if (lastSlash < SERVER_PACK_PREFIX.length - 1) return null
		return runCatching { UUID.fromString(packId.substring(lastSlash + 1)) }.getOrNull()
	}
}
