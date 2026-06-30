package io.github.dzkchen.dhen.platform

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.dzkchen.dhen.runtime.JsonCodec

// Gson is already on the Minecraft classpath, so the rest of the build does not need a
// serialization dependency. Decoding to Object yields maps, lists, strings, booleans, and doubles.
object GsonJsonCodec : JsonCodec {
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

	override fun encode(value: Any?): String = gson.toJson(value)

	override fun decode(text: String): Any? = gson.fromJson(text, Any::class.java)
}
