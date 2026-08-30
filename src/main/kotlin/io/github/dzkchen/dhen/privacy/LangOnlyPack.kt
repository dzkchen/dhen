package io.github.dzkchen.dhen.privacy

import java.io.InputStream
import java.util.UUID
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.resources.IoSupplier

internal class LangOnlyPack(
	private val delegate: PackResources,
	private val id: UUID
) : PackResources {
	private val trackerToken = ShaderStripTracker.token()

	override fun getRootResource(vararg elements: String): IoSupplier<InputStream>? =
		delegate.getRootResource(*elements)

	override fun getResource(type: PackType, location: Identifier): IoSupplier<InputStream>? {
		if (stripsShader(type, location.namespace, location.path)) {
			ShaderStripTracker.onStripped(trackerToken, location.namespace, location.path)
			return null
		}
		if (ServerPacks.stripsContent(id) && !isLanguage(type, location.path)) return null
		return delegate.getResource(type, location)
	}

	override fun listResources(
		type: PackType,
		namespace: String,
		prefix: String,
		output: PackResources.ResourceOutput
	) {
		val stripsShaders = stripsShaderNamespace(type, namespace)
		if (ServerPacks.stripsContent(id) && !isLanguageDirectory(type, prefix)) {
			if (stripsShaders) delegate.listResources(type, namespace, prefix) { location, _ ->
				if (location.path.startsWith(SHADERS)) {
					ShaderStripTracker.onStripped(trackerToken, location.namespace, location.path)
				}
			}
			return
		}
		if (!stripsShaders) {
			delegate.listResources(type, namespace, prefix, output)
			return
		}
		delegate.listResources(type, namespace, prefix) { location, supplier ->
			if (!location.path.startsWith(SHADERS)) output.accept(location, supplier)
			else ShaderStripTracker.onStripped(trackerToken, location.namespace, location.path)
		}
	}

	override fun getNamespaces(type: PackType): Set<String> = delegate.getNamespaces(type)

	override fun <T : Any> getMetadataSection(type: MetadataSectionType<T>): T? =
		delegate.getMetadataSection(type)

	override fun location(): PackLocationInfo = delegate.location()

	override fun close() = delegate.close()

	private fun stripsShader(type: PackType, namespace: String, path: String): Boolean =
		path.startsWith(SHADERS) && stripsShaderNamespace(type, namespace)

	private fun stripsShaderNamespace(type: PackType, namespace: String): Boolean =
		type == PackType.CLIENT_RESOURCES &&
			ServerPacks.mode != ServerPacks.Mode.OFF &&
			namespace != VANILLA_NAMESPACE &&
			!ModRegistry.allowsShaderOverride(namespace)

	companion object {
		private const val LANGUAGE = "lang"
		private const val LANGUAGE_PREFIX = "lang/"
		private const val LANGUAGE_SUFFIX = ".json"
		private const val SHADERS = "shaders/"
		private const val VANILLA_NAMESPACE = "minecraft"

		@JvmStatic
		fun over(delegate: PackResources, id: UUID): PackResources = LangOnlyPack(delegate, id)

		private fun isLanguage(type: PackType, path: String): Boolean =
			type == PackType.CLIENT_RESOURCES && path.startsWith(LANGUAGE_PREFIX) && path.endsWith(LANGUAGE_SUFFIX)

		private fun isLanguageDirectory(type: PackType, path: String): Boolean =
			type == PackType.CLIENT_RESOURCES && (path == LANGUAGE || path.startsWith(LANGUAGE_PREFIX))
	}
}
