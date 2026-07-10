package io.github.dzkchen.dhen.config

import io.github.dzkchen.dhen.module.Module
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Setting<T>(
	val name: String,
	val description: String = ""
) : ReadWriteProperty<Module, T>, PropertyDelegateProvider<Module, ReadWriteProperty<Module, T>> {

	abstract val default: T
	abstract var value: T

	private var hidden = false
	private var visibilityDependency: (() -> Boolean)? = null

	val isVisible: Boolean
		get() = (visibilityDependency?.invoke() ?: true) && !hidden

	open fun reset() {
		value = default
	}

	override operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ReadWriteProperty<Module, T> =
		thisRef.registerSetting(this)

	override operator fun getValue(thisRef: Module, property: KProperty<*>): T = value

	override operator fun setValue(thisRef: Module, property: KProperty<*>, value: T) {
		this.value = value
	}

	companion object {
		fun <S : Setting<T>, T> S.withDependency(dependency: () -> Boolean): S {
			visibilityDependency = dependency
			return this
		}

		fun <S : Setting<T>, T> S.hide(): S {
			hidden = true
			return this
		}
	}
}
