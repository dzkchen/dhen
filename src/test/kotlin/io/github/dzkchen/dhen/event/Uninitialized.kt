package io.github.dzkchen.dhen.event

import sun.reflect.ReflectionFactory

internal inline fun <reified T : Any> uninitialized(): T =
	ReflectionFactory.getReflectionFactory()
		.newConstructorForSerialization(T::class.java, Any::class.java.getDeclaredConstructor())
		.newInstance() as T
