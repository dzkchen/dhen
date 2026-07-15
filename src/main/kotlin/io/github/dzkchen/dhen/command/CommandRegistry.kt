package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.module.ModuleManager
import java.util.Locale

class CommandRegistry<S>(
	private val manager: ModuleManager,
	private val feedback: (S, String) -> Unit
) {
	private val registrations = linkedMapOf<String, RegisteredCommand<S>>()

	val commands: List<RegisteredCommand<S>>
		get() = registrations.values.toList()

	fun register(
		name: String,
		vararg aliases: String,
		owner: String,
		build: LiteralArgumentBuilder<S>.() -> Unit
	): Handle {
		val literals = (listOf(name) + aliases).map { it.lowercase(Locale.ROOT) }
		for (lit in literals) {
			require(lit !in RESERVED) { "Command '$lit' is reserved by the core command tree." }
			require(registrations.values.none { lit in it.literals }) { "Command '$lit' is already registered." }
		}
		val registration = RegisteredCommand(name, aliases.toList(), owner, literals, build)
		val key = literals.first()
		registrations[key] = registration
		return Handle { registrations.remove(key, registration) }
	}

	fun install(dispatcher: CommandDispatcher<S>) {
		dispatcher.register(core("dhen"))
		dispatcher.register(core("dh"))
		for (registration in registrations.values) {
			for (lit in registration.literals) {
				dispatcher.register(literal<S>(lit).apply(registration.build))
			}
		}
	}

	private fun core(name: String): LiteralArgumentBuilder<S> =
		literal<S>(name)
			.executes { context ->
				feedback(context.source, "Dhen commands: /$name module <name> toggle")
				Command.SINGLE_SUCCESS
			}
			.then(
				literal<S>("module").then(
					argument<S, String>("name", StringArgumentType.word())
						.suggests { _, builder ->
							for (module in manager.modules) {
								val encoded = module.name.replace(' ', '_')
								if (encoded.lowercase(Locale.ROOT).startsWith(builder.remainingLowerCase)) builder.suggest(encoded)
							}
							builder.buildFuture()
						}
						.then(
							literal<S>("toggle").executes { context ->
								val raw = StringArgumentType.getString(context, "name")
								val module = manager.modules.firstOrNull { it.name.replace(' ', '_').equals(raw, ignoreCase = true) }
								if (module == null) {
									feedback(context.source, "No module named '$raw'.")
								} else {
									manager.toggle(module)
									feedback(context.source, "Toggled ${module.name}: ${if (module.enabled) "enabled" else "disabled"}")
								}
								Command.SINGLE_SUCCESS
							}
						)
				)
			)

	private companion object {
		private val RESERVED = setOf("dhen", "dh")
	}
}

class RegisteredCommand<S> internal constructor(
	val name: String,
	val aliases: List<String>,
	val owner: String,
	internal val literals: List<String>,
	internal val build: LiteralArgumentBuilder<S>.() -> Unit
)
