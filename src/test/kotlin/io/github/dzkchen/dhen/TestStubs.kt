package io.github.dzkchen.dhen

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import sun.reflect.ReflectionFactory
import java.lang.reflect.Proxy

internal fun bootstrapMinecraft() {
	SharedConstants.tryDetectVersion()
	Bootstrap.bootStrap()
}

internal inline fun <reified T : Any> uninitialized(): T =
	ReflectionFactory.getReflectionFactory()
		.newConstructorForSerialization(T::class.java, Any::class.java.getDeclaredConstructor())
		.newInstance() as T

@Suppress("UNCHECKED_CAST")
internal fun <T> absent(): T = null as T

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> unsupported(): T = Proxy.newProxyInstance(
	T::class.java.classLoader,
	arrayOf(T::class.java)
) { _, _, _ -> throw UnsupportedOperationException() } as T
