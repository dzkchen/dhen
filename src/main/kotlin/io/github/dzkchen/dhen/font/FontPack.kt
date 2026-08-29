package io.github.dzkchen.dhen.font

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackCompatibility
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.world.flag.FeatureFlagSet
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.function.Consumer

internal object DhenFontPack {
	const val PACK_ID = "dhen_fonts"
	const val USER = "user"
	const val DEFINITION = ".json"

	private const val FONT_ROOT = "font/"

	private const val TITLE = "Dhen Fonts"
	private const val DESCRIPTION = "Faces dropped into config/dhen/fonts"
	private const val TYPE_KEY = "type"
	private const val TTF = "ttf"
	private const val REFERENCE = "reference"
	private const val PROVIDERS_KEY = "providers"
	private const val FILE_KEY = "file"
	private const val ID_KEY = "id"
	private const val FILTER_KEY = "filter"
	private const val UNIFORM_KEY = "uniform"
	private const val DEFAULT_FALLBACK = "minecraft:include/default"
	private const val UNIFONT_FALLBACK = "minecraft:include/unifont"

	private val location =
		PackLocationInfo(PACK_ID, Component.literal(TITLE), PackSource.BUILT_IN, Optional.empty())

	private val metadata =
		Pack.Metadata(Component.literal(DESCRIPTION), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), emptyList())

	private val selection = PackSelectionConfig(true, Pack.Position.TOP, true)

	private val namespaces = setOf(Dhen.MOD_ID)

	@JvmStatic
	fun contribute(packs: Consumer<Pack>) {
		packs.accept(Pack(location, Resources, metadata, selection))
	}

	fun font(face: FontFace): Identifier = Dhen.id(served(face))

	fun definitionPath(face: FontFace): String = FONT_ROOT + served(face) + DEFINITION

	fun facePath(face: FontFace): String = FONT_ROOT + served(face) + FontStore.EXTENSION

	private fun served(face: FontFace): String = "$USER/${face.id}"

	fun definition(face: FontFace): ByteArray {
		val ttf = JsonObject()
		ttf.addProperty(TYPE_KEY, TTF)
		ttf.addProperty(FILE_KEY, "${Dhen.MOD_ID}:${served(face)}${FontStore.EXTENSION}")
		ttf.addProperty(FontStore.SIZE_KEY, face.size)
		ttf.addProperty(FontStore.OVERSAMPLE_KEY, face.oversample)
		ttf.add(FontStore.SHIFT_KEY, JsonArray().apply { add(face.shiftX); add(face.shiftY) })
		val providers = JsonArray()
		providers.add(ttf)
		providers.add(reference(DEFAULT_FALLBACK, uniform = false))
		providers.add(reference(UNIFONT_FALLBACK, uniform = null))
		val document = JsonObject()
		document.add(PROVIDERS_KEY, providers)
		return document.toString().toByteArray(StandardCharsets.UTF_8)
	}

	private fun reference(id: String, uniform: Boolean?): JsonObject {
		val provider = JsonObject()
		provider.addProperty(TYPE_KEY, REFERENCE)
		provider.addProperty(ID_KEY, id)
		if (uniform != null) {
			provider.add(FILTER_KEY, JsonObject().apply { addProperty(UNIFORM_KEY, uniform) })
		}
		return provider
	}

	private object Resources : Pack.ResourcesSupplier {
		override fun openPrimary(location: PackLocationInfo): PackResources = FontPackResources(location)

		override fun openFull(location: PackLocationInfo, metadata: Pack.Metadata): PackResources =
			FontPackResources(location)
	}

	private class FontPackResources(private val info: PackLocationInfo) : PackResources {
		override fun getRootResource(vararg elements: String): IoSupplier<InputStream>? = null

		override fun getResource(type: PackType, id: Identifier): IoSupplier<InputStream>? {
			if (type != PackType.CLIENT_RESOURCES || id.namespace != Dhen.MOD_ID) return null
			for (face in FontStore.faces()) {
				if (id.path == definitionPath(face)) return IoSupplier { ByteArrayInputStream(definition(face)) }
				if (id.path == facePath(face)) return IoSupplier.create(face.file)
			}
			return null
		}

		override fun listResources(type: PackType, namespace: String, prefix: String, output: PackResources.ResourceOutput) {
			if (type != PackType.CLIENT_RESOURCES || namespace != Dhen.MOD_ID) return
			for (face in FontStore.faces()) {
				val path = definitionPath(face)
				if (path != prefix && !path.startsWith("$prefix/")) continue
				output.accept(Identifier.fromNamespaceAndPath(Dhen.MOD_ID, path)) { ByteArrayInputStream(definition(face)) }
			}
		}

		override fun getNamespaces(type: PackType): Set<String> =
			if (type == PackType.CLIENT_RESOURCES) namespaces else emptySet()

		override fun <T : Any> getMetadataSection(type: MetadataSectionType<T>): T? = null

		override fun location(): PackLocationInfo = info

		override fun close() = Unit
	}
}
