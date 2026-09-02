package io.github.dzkchen.dhen.data.repo

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.github.dzkchen.dhen.Dhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class PackModelTable internal constructor(
	private val models: Map<String, Identifier>,
	private val textures: Map<String, String>
) {
	val size: Int get() = models.size

	fun model(id: String): Identifier? = models[id]

	fun texture(id: String): String? = textures[id]

	internal companion object {
		val EMPTY = PackModelTable(emptyMap(), emptyMap())
	}
}

object PackModelRepo {
	private const val TABLE = "packmodels/skyblock-items.json"
	private const val MODEL = "model"
	private const val TEXTURE = "texture"

	private val DHEN = RepoSource("dzkchen", "dhen-REPO", "main")
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val loading = AtomicBoolean(false)

	@Volatile
	private var host: Host? = null

	@Volatile
	var table: PackModelTable = PackModelTable.EMPTY
		internal set

	fun require() {
		val owner = host ?: return
		if (table.size > 0 || !loading.compareAndSet(false, true)) return
		owner.scope.launch {
			try {
				load(owner)
			} finally {
				loading.set(false)
			}
		}
	}

	internal fun install(scope: CoroutineScope, root: Path, sync: RepoSync = RepoSync(DHEN, root)) {
		uninstall()
		val owner = Host(scope, sync)
		sync.changeInstallation { host = owner }
	}

	internal fun uninstall() {
		val owner = host ?: return
		owner.sync.changeInstallation {
			if (host !== owner) return@changeInstallation
			host = null
			table = PackModelTable.EMPTY
		}
	}

	private fun load(owner: Host) {
		val result = owner.sync.sync { host === owner }.result
		if (result == SyncResult.ABANDONED) return
		if (result != SyncResult.UPDATED && result != SyncResult.UP_TO_DATE) {
			log.warn("Dhen could not refresh the pack model table ({}), reading the last complete copy on disk", result)
		}
		val read = try {
			owner.sync.readMarked { root, _ -> read(root.resolve(TABLE)) }
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the pack model table", throwable)
			null
		} ?: return
		if (host !== owner) return
		table = read
		log.info("Dhen pack model table READY with {} items", read.size)
	}

	internal fun read(file: Path): PackModelTable? {
		if (!Files.isRegularFile(file)) return null
		val models = HashMap<String, Identifier>()
		val textures = HashMap<String, String>()
		var dropped = 0
		JsonReader(Files.newBufferedReader(file)).use { reader ->
			reader.beginObject()
			while (reader.hasNext()) {
				val id = reader.nextName()
				var model: Identifier? = null
				var texture: String? = null
				reader.beginObject()
				while (reader.hasNext()) {
					when (reader.nextName()) {
						MODEL -> model = itemModel(reader.nextString())
						TEXTURE -> texture = reader.nextString()
						else -> reader.skipValue()
					}
				}
				reader.endObject()
				if (model == null) dropped++ else models[id] = model
				if (texture != null) textures[id] = texture
			}
			reader.endObject()
			if (reader.peek() != JsonToken.END_DOCUMENT) return null
		}
		if (dropped > 0) log.warn("Dhen skipped {} pack model entries naming an unknown item", dropped)
		return PackModelTable(models, textures)
	}

	fun itemModel(itemId: String): Identifier? {
		val parsed = Identifier.tryParse(itemId) ?: return null
		if (!BuiltInRegistries.ITEM.containsKey(parsed)) return null
		return BuiltInRegistries.ITEM.getValue(parsed).components().get(DataComponents.ITEM_MODEL)
	}

	private class Host(val scope: CoroutineScope, val sync: RepoSync)
}
