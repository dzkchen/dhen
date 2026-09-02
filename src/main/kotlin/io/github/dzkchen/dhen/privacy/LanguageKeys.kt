package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.Handle
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.VanillaPackResources
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.function.BiConsumer

internal interface CompositePackAccess {
	fun dhenPrimaryPack(): PackResources
}

object LanguageKeys {
	private data class Snapshot(
		val vanilla: Set<String>,
		val owners: Map<String, String>,
		val server: Map<String, String>
	) {
		companion object {
			val EMPTY = Snapshot(emptySet(), emptyMap(), emptyMap())
		}
	}

	private class Staging(val generation: Long) {
		val vanilla = HashSet<String>()
		val owners = HashMap<String, String>()
		val server = HashMap<String, String>()
	}

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val staging = ThreadLocal<Staging>()
	private val classOwners by lazy(::loadClassOwners)
	private val stateLock = Any()

	@Volatile
	private var snapshot = Snapshot.EMPTY
	@Volatile
	private var generation = 0L
	@Volatile
	private var mountedServerPack: String? = null

	private var subscription: Handle? = null

	fun install(bus: EventBus) {
		uninstall()
		subscription = bus.subscribe<GuiOpenEvent> {
			if (it.screen is ConnectScreen) clearServerPack()
		}
	}

	fun uninstall() {
		subscription?.unsubscribe()
		subscription = null
		clearCache()
	}

	@JvmStatic
	fun beginReload() {
		staging.set(Staging(generation))
	}

	@JvmStatic
	fun commitReload() {
		val completed = staging.get() ?: return
		staging.remove()
		val next = Snapshot(
			completed.vanilla.toSet(),
			completed.owners.toMap(),
			completed.server.toMap()
		)
		synchronized(stateLock) {
			if (completed.generation != generation) return
			snapshot = next
		}
		log.info(
			"Language keys reloaded: {} vanilla, {} mod, {} server pack",
			next.vanilla.size,
			next.owners.size,
			next.server.size
		)
	}

	@JvmStatic
	fun abortReload() {
		staging.remove()
	}

	@JvmStatic
	fun trackingConsumer(pack: PackResources, output: BiConsumer<String, String>): BiConsumer<String, String> {
		val target = staging.get() ?: return output
		if (pack is VanillaPackResources) {
			return BiConsumer { key, value ->
				target.vanilla += key
				output.accept(key, value)
			}
		}
		val owner = modOwner(pack)
		if (owner != null) {
			return BiConsumer { key, value ->
				target.owners[key] = owner
				output.accept(key, value)
			}
		}
		if (isServerPackId(pack.packId())) {
			return BiConsumer { key, value ->
				target.server[key] = value
				output.accept(key, value)
			}
		}
		return output
	}

	@JvmStatic
	fun isVanillaKey(key: String): Boolean = key in snapshot.vanilla

	@JvmStatic
	fun isModKey(key: String): Boolean = key in snapshot.owners

	@JvmStatic
	fun ownerOf(key: String): String? = snapshot.owners[key]

	@JvmStatic
	fun serverPackValue(key: String): String? = snapshot.server[key]

	@JvmStatic
	fun isWhitelistedKey(key: String): Boolean = snapshot.owners[key]?.let(ModRegistry::allowsMod) == true

	@JvmStatic
	fun clearCache() {
		staging.remove()
		synchronized(stateLock) {
			generation++
			snapshot = Snapshot.EMPTY
		}
	}

	internal fun markMountedServerPack(packId: String?) {
		mountedServerPack = packId
	}

	private fun isServerPackId(packId: String): Boolean =
		packId.startsWith(SERVER_PACK_PREFIX) || packId == mountedServerPack

	internal fun clearServerPack() {
		staging.remove()
		synchronized(stateLock) {
			generation++
			snapshot = snapshot.copy(server = emptyMap())
		}
	}

	internal fun recordVanilla(key: String) {
		staging.get()?.vanilla?.add(key)
	}

	internal fun recordMod(owner: String, key: String) {
		staging.get()?.owners?.set(key, owner)
	}

	internal fun recordServer(key: String, value: String) {
		staging.get()?.server?.set(key, value)
	}

	private fun modOwner(pack: PackResources): String? {
		if (pack is CompositePackAccess) return modOwner(pack.dhenPrimaryPack())
		if (pack is ModPackResources) return pack.fabricModMetadata.id
		val packId = pack.packId()
		if (FabricLoader.getInstance().isModLoaded(packId)) return packId
		if (isServerPackId(packId)) return null
		return codeSource(pack.javaClass)?.let(classOwners::get)
	}

	private fun loadClassOwners(): Map<Path, String> {
		val owners = HashMap<Path, String>()
		for (mod in FabricLoader.getInstance().allMods) {
			val id = mod.metadata.id
			if (ModGraph.platform(id)) continue
			try {
				for (path in mod.origin.paths) owners[path.toAbsolutePath().normalize()] = id
			} catch (_: UnsupportedOperationException) {
			}
		}
		return owners
	}

	private fun codeSource(type: Class<*>): Path? = try {
		Path.of(type.protectionDomain?.codeSource?.location?.toURI()).toAbsolutePath().normalize()
	} catch (_: Exception) {
		null
	}

	private const val SERVER_PACK_PREFIX = "server/"
}
