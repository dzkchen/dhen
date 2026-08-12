package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.module.ModuleManager
import java.util.Locale

class CommandRegistry<S>(
	private val manager: ModuleManager,
	private val openHudEditor: () -> Unit = {},
	private val persistEffects: () -> Unit = {},
	private val resetHudLayout: () -> Int = { 0 },
	private val diagnostics: Diagnostics = Diagnostics(manager),
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
				feedback(
					context.source,
					"Dhen commands: /$name module <name> toggle | debug | edit | reset-all | effects"
				)
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
			.then(
				literal<S>("edit").executes { context ->
					openHudEditor()
					feedback(context.source, "Opening the HUD editor.")
					Command.SINGLE_SUCCESS
				}
			)
			.then(resetAllCommand())
			.then(effectsCommand())
			.then(debugCommand())

	private fun resetAllCommand(): LiteralArgumentBuilder<S> =
		literal<S>("reset-all")
			.executes { context ->
				feedback(context.source, resetSummary(resetHudLayout()))
				Command.SINGLE_SUCCESS
			}

	private fun resetSummary(reset: Int): String = when (reset) {
		0 -> "Every HUD element is already at its declared layout."
		1 -> "Reset 1 HUD element to the declared layout."
		else -> "Reset $reset HUD elements to the declared layout."
	}

	private fun effectsCommand(): LiteralArgumentBuilder<S> =
		literal<S>("effects")
			.executes { context -> applyEffects(context.source, !Effects.reduced) }
			.then(literal<S>("on").executes { context -> applyEffects(context.source, reduced = false) })
			.then(literal<S>("off").executes { context -> applyEffects(context.source, reduced = true) })

	private fun applyEffects(source: S, reduced: Boolean): Int {
		Effects.reduced = reduced
		persistEffects()
		feedback(source, "Glass effects ${if (reduced) "disabled" else "enabled"}.")
		return Command.SINGLE_SUCCESS
	}

	private fun debugCommand(): LiteralArgumentBuilder<S> =
		literal<S>("debug")
			.executes { context ->
				for (line in diagnostics.lines()) feedback(context.source, line)
				Command.SINGLE_SUCCESS
			}
			.then(
				literal<S>("deep")
					.then(deepMode("on", true))
					.then(deepMode("off", false))
			)

	private fun deepMode(name: String, enabled: Boolean): LiteralArgumentBuilder<S> =
		literal<S>(name).executes { context ->
			diagnostics.deepMode = enabled
			feedback(context.source, "Deep profiling ${if (enabled) "enabled" else "disabled"}.")
			Command.SINGLE_SUCCESS
		}

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
