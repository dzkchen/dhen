package io.github.dzkchen.dhen.privacy

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents

interface PacketOrigin {
	fun fromPacket(): Boolean

	fun markFromPacket()
}

class PacketComponentCodec(private val wrapped: Codec<Component>) : Codec<Component> {
	override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<Component, T>> {
		val result = wrapped.decode(ops, input)
		if (!PacketContext.isProcessingPacket()) return result
		val minecraft = Minecraft.getInstance() as Minecraft?
		if (minecraft != null && minecraft.hasSingleplayerServer()) return result
		return result.map { pair ->
			markTree(pair.first)
			pair
		}
	}

	override fun <T> encode(input: Component, ops: DynamicOps<T>, prefix: T): DataResult<T> =
		wrapped.encode(input, ops, prefix)

	companion object {
		internal fun markTree(component: Component) {
			val contents = component.contents
			if (contents is PacketOrigin) contents.markFromPacket()
			if (contents is TranslatableContents) {
				val arguments = contents.args
				for (index in arguments.indices) {
					val argument = arguments[index]
					if (argument is Component) markTree(argument)
				}
			}
			val siblings = component.siblings
			for (index in siblings.indices) markTree(siblings[index])
		}
	}
}
