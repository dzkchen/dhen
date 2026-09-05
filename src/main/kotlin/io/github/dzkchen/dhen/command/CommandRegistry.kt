package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import com.mojang.brigadier.tree.CommandNode
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.Locale

class CommandRegistry<S>(
	private val manager: ModuleManager,
	private val persistCore: () -> Unit = {},
	private val themes: ThemeCommands = ThemeCommands.NONE,
	private val chatHider: ChatHiderCommands = ChatHiderCommands.NONE,
	private val commandAliases: CommandAliasCommands = CommandAliasCommands.NONE,
	private val hotkeys: HotkeyCommands = HotkeyCommands.NONE,
	private val textReplacer: TextReplacerCommands = TextReplacerCommands.NONE,
	private val utilities: CommandUtilities = CommandUtilities.NONE,
	private val hud: HudCommands = HudCommands.NONE,
	private val sounds: SoundCommands = SoundCommands.NONE,
	private val previews: PreviewCommands = PreviewCommands.NONE,
	private val waypoints: WaypointCommands = WaypointCommands.NONE,
	private val reminders: ReminderCommands = ReminderCommands.NONE,
	private val diagnostics: Diagnostics = Diagnostics(manager),
	private val available: () -> Boolean = { true },
	private val persistModules: () -> Unit = {},
	private val feedback: (S, Component) -> Unit
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
		for (name in CORE_NAMES) dispatcher.register(core(name).requires { available() })
		dispatcher.register(remindMeCommand().requires { available() })
		for (registration in registrations.values) {
			for (lit in registration.literals) {
				dispatcher.register(literal<S>(lit).apply(registration.build).requires { available() })
			}
		}
	}

	private fun core(name: String): LiteralArgumentBuilder<S> =
		literal<S>(name)
			.executes { context -> report(context.source, "Type /$name help for every Dhen command.") }
			.then(
				literal<S>("module").then(
					argument<S, String>("name", StringArgumentType.word())
						.suggests(suggesting { manager.modules.map { module -> wireName(module.name) } })
						.then(
							literal<S>("toggle").executes { context ->
								val raw = StringArgumentType.getString(context, "name")
								val module = manager.modules.firstOrNull { wireName(it.name).equals(raw, ignoreCase = true) }
								if (module == null) return@executes report(context.source, "No module named '$raw'.")
								manager.toggle(module)
								report(context.source, "Toggled ${module.name}: ${if (module.enabled) "enabled" else "disabled"}")
							}
						)
						.then(
							literal<S>("reset").executes { context ->
								val raw = StringArgumentType.getString(context, "name")
								val module = manager.modules.firstOrNull { wireName(it.name).equals(raw, ignoreCase = true) }
								if (module == null) return@executes report(context.source, "No module named '$raw'.")
								module.resetSettings()
								persistModules()
								report(context.source, "Reset ${module.name} settings to their defaults.")
							}
						)
				)
			)
			.then(literal<S>("edit").executes { context -> report(context.source, hud.openEditor()) })
			.then(literal<S>("sounds").executes { context -> report(context.source, sounds.openManager()) })
			.then(literal<S>("reset-all").executes { context -> report(context.source, hud.resetLayout()) })
			.then(helpCommand())
			.then(sendCoordsCommand())
			.then(sendPingCommand())
			.then(waypointCommand())
			.then(wikiCommand())
			.then(calcCommand())
			.then(literal<S>("wikithis").executes { context -> report(context.source, utilities.heldItemWiki()) })
			.then(literal<S>("lastopened").executes { context -> report(context.source, utilities.openLastStorage()) })
			.then(literal<S>("link").executes { context -> report(context.source, utilities.link()) })
			.then(effectsCommand())
			.then(themeCommand())
			.then(chatHiderCommand())
			.then(aliasCommand())
			.then(hotkeyCommand())
			.then(replaceCommand())
			.then(debugCommand())

	private fun chatHiderCommand(): LiteralArgumentBuilder<S> =
		literal<S>("chathider")
			.executes { context -> reportAll(context.source, chatHider.list()) }
			.then(literal<S>("list").executes { context -> reportAll(context.source, chatHider.list()) })
			.then(
				literal<S>("add").then(
					argument<S, String>("pattern", StringArgumentType.greedyString())
						.executes { context -> stored(context.source, chatHider.add(pattern(context))) }
				)
			)
			.then(
				literal<S>("remove").then(
					argument<S, String>("pattern", StringArgumentType.greedyString())
						.suggests(suggesting(chatHider::patterns))
						.executes { context -> stored(context.source, chatHider.remove(pattern(context))) }
				)
			)

	private fun replaceCommand(): LiteralArgumentBuilder<S> =
		literal<S>("replace")
			.executes { context -> reportAll(context.source, textReplacer.list()) }
			.then(literal<S>("list").executes { context -> reportAll(context.source, textReplacer.list()) })
			.then(
				literal<S>("add").then(
					argument<S, String>("find", StringArgumentType.string()).then(
						argument<S, String>("replacement", StringArgumentType.greedyString())
							.executes { context ->
								stored(
									context.source,
									textReplacer.add(
										StringArgumentType.getString(context, "find"),
										StringArgumentType.getString(context, "replacement")
									)
								)
							}
					)
				)
			)
			.then(
				literal<S>("remove").then(
					argument<S, String>("find", StringArgumentType.string())
						.suggests(suggesting(textReplacer::finds))
						.executes { context ->
							stored(context.source, textReplacer.remove(StringArgumentType.getString(context, "find")))
						}
				)
			)

	private fun aliasCommand(): LiteralArgumentBuilder<S> =
		literal<S>("alias")
			.executes { context -> reportAll(context.source, commandAliases.list()) }
			.then(literal<S>("list").executes { context -> reportAll(context.source, commandAliases.list()) })
			.then(
				literal<S>("add").then(
					argument<S, String>("alias", StringArgumentType.word()).then(
						argument<S, String>("command", StringArgumentType.greedyString())
							.executes { context ->
								stored(
									context.source,
									commandAliases.add(
										StringArgumentType.getString(context, "alias"),
										StringArgumentType.getString(context, "command")
									)
								)
							}
					)
				)
			)
			.then(
				literal<S>("remove").then(
					argument<S, String>("alias", StringArgumentType.word())
						.suggests(suggesting(commandAliases::aliases))
						.executes { context ->
							stored(context.source, commandAliases.remove(StringArgumentType.getString(context, "alias")))
						}
				)
			)

	private fun hotkeyCommand(): LiteralArgumentBuilder<S> =
		literal<S>("hotkey")
			.executes { context -> reportAll(context.source, hotkeys.list()) }
			.then(literal<S>("list").executes { context -> reportAll(context.source, hotkeys.list()) })
			.then(
				literal<S>("add").then(
					argument<S, String>("keys and command", StringArgumentType.greedyString())
						.executes { context -> stored(context.source, split(context, "keys and command", ADD_USAGE, hotkeys::add)) }
				)
			)
			.then(
				literal<S>("where").then(
					argument<S, String>("number and scope", StringArgumentType.greedyString())
						.suggests(suggesting(hotkeys::targets))
						.executes { context -> stored(context.source, split(context, "number and scope", WHERE_USAGE, hotkeys::scope)) }
				)
			)
			.then(
				literal<S>("remove").then(
					argument<S, String>("number", StringArgumentType.greedyString())
						.suggests(suggesting(hotkeys::targets))
						.executes { context ->
							stored(context.source, hotkeys.remove(StringArgumentType.getString(context, "number")))
						}
				)
			)

	private fun sendPingCommand(): LiteralArgumentBuilder<S> =
		literal<S>("sendping")
			.executes { context -> report(context.source, waypoints.sendPing("")) }
			.then(
				argument<S, String>("note", StringArgumentType.greedyString()).executes { context ->
					report(context.source, waypoints.sendPing(StringArgumentType.getString(context, "note")))
				}
			)

	private fun waypointCommand(): LiteralArgumentBuilder<S> =
		literal<S>("waypoint")
			.executes { context -> reportAll(context.source, waypoints.list()) }
			.then(literal<S>("list").executes { context -> reportAll(context.source, waypoints.list()) })
			.then(
				literal<S>("add").then(
					argument<S, String>("name", StringArgumentType.word()).executes { context ->
						stored(context.source, waypoints.save(StringArgumentType.getString(context, "name")))
					}
				)
			)
			.then(
				literal<S>("remove").then(
					argument<S, String>("name", StringArgumentType.word())
						.suggests(suggesting(waypoints::names))
						.executes { context ->
							stored(context.source, waypoints.forget(StringArgumentType.getString(context, "name")))
						}
				)
			)
			.then(atCoordinates("hide", labelled = false) { x, y, z, _ -> waypoints.hide(x, y, z) })
			.then(atCoordinates("redraw", labelled = true, action = waypoints::redraw))

	private fun atCoordinates(
		name: String,
		labelled: Boolean,
		action: (Int, Int, Int, String) -> String
	): LiteralArgumentBuilder<S> {
		val depth = argument<S, Int>("z", IntegerArgumentType.integer())
			.executes { context -> report(context.source, at(context, "", action)) }
		if (labelled) {
			depth.then(
				argument<S, String>("label", StringArgumentType.greedyString()).executes { context ->
					report(context.source, at(context, StringArgumentType.getString(context, "label"), action))
				}
			)
		}
		return literal<S>(name).then(
			argument<S, Int>("x", IntegerArgumentType.integer())
				.then(argument<S, Int>("y", IntegerArgumentType.integer()).then(depth))
		)
	}

	private fun at(context: CommandContext<S>, label: String, action: (Int, Int, Int, String) -> String): String =
		action(
			IntegerArgumentType.getInteger(context, "x"),
			IntegerArgumentType.getInteger(context, "y"),
			IntegerArgumentType.getInteger(context, "z"),
			label
		)

	private fun split(
		context: CommandContext<S>,
		name: String,
		usage: String,
		action: (String, String) -> String
	): String {
		val raw = StringArgumentType.getString(context, name).trim()
		val space = raw.indexOf(' ')
		if (space <= 0) return usage
		return action(raw.substring(0, space), raw.substring(space + 1).trim())
	}

	private fun pattern(context: CommandContext<S>): String = StringArgumentType.getString(context, "pattern")

	private fun stored(source: S, message: String): Int {
		persistModules()
		return report(source, message)
	}

	private fun reportAll(source: S, lines: List<String>): Int {
		for (line in lines) feedback(source, DhenType.overWorld(line))
		return Command.SINGLE_SUCCESS
	}

	private fun effectsCommand(): LiteralArgumentBuilder<S> =
		literal<S>("effects")
			.executes { context -> applyEffects(context.source, !Effects.reduced) }
			.then(literal<S>("on").executes { context -> applyEffects(context.source, reduced = false) })
			.then(literal<S>("off").executes { context -> applyEffects(context.source, reduced = true) })

	private fun applyEffects(source: S, reduced: Boolean): Int {
		Effects.reduced = reduced
		persistCore()
		return report(source, "Glass effects ${if (reduced) "disabled" else "enabled"}.")
	}

	private fun themeCommand(): LiteralArgumentBuilder<S> =
		literal<S>("theme")
			.executes { context -> report(context.source, themes.summary()) }
			.then(literal<S>("list").executes { context -> report(context.source, themes.summary()) })
			.then(
				literal<S>("use").then(
					argument<S, String>("name", StringArgumentType.word())
						.suggests(suggesting(themes::names))
						.executes { context -> report(context.source, themes.select(StringArgumentType.getString(context, "name"))) }
				)
			)
			.then(
				literal<S>("export")
					.executes { context -> answering(context.source) { notify -> themes.export(null, notify) } }
					.then(
						argument<S, String>("name", StringArgumentType.word())
							.executes { context ->
								val name = StringArgumentType.getString(context, "name")
								answering(context.source) { notify -> themes.export(name, notify) }
							}
					)
			)
			.then(literal<S>("reload").executes { context -> answering(context.source, themes::reload) })

	private fun suggesting(options: () -> List<String>): SuggestionProvider<S> =
		SuggestionProvider { _, builder ->
			for (option in options()) {
				if (option.startsWith(builder.remainingLowerCase, ignoreCase = true)) builder.suggest(option)
			}
			builder.buildFuture()
		}

	private fun answering(source: S, action: ((String) -> Unit) -> Unit): Int {
		action { message -> feedback(source, DhenType.overWorld(message)) }
		return Command.SINGLE_SUCCESS
	}

	private fun report(source: S, message: String): Int {
		feedback(source, DhenType.overWorld(message))
		return Command.SINGLE_SUCCESS
	}

	private fun calcCommand(): LiteralArgumentBuilder<S> =
		literal<S>("calc")
			.executes { context -> report(context.source, utilities.calculate("")) }
			.then(
				argument<S, String>("expression", StringArgumentType.greedyString()).executes { context ->
					report(context.source, utilities.calculate(StringArgumentType.getString(context, "expression")))
				}
			)

	private fun wikiCommand(): LiteralArgumentBuilder<S> =
		literal<S>("wiki")
			.executes { context -> report(context.source, utilities.wiki("")) }
			.then(
				argument<S, String>("search", StringArgumentType.greedyString()).executes { context ->
					report(context.source, utilities.wiki(StringArgumentType.getString(context, "search")))
				}
			)

	private fun sendCoordsCommand(): LiteralArgumentBuilder<S> =
		literal<S>("sendcoords")
			.executes { context -> report(context.source, utilities.sendCoordinates("")) }
			.then(
				argument<S, String>("message", StringArgumentType.greedyString()).executes { context ->
					report(context.source, utilities.sendCoordinates(StringArgumentType.getString(context, "message")))
				}
			)

	private fun helpCommand(): LiteralArgumentBuilder<S> =
		literal<S>("help")
			.executes { context -> help(context, FIRST_PAGE, "") }
			.then(
				literal<S>("-p").then(
					argument<S, Int>("page", IntegerArgumentType.integer(FIRST_PAGE))
						.executes { context -> help(context, IntegerArgumentType.getInteger(context, "page"), "") }
						.then(
							argument<S, String>("search", StringArgumentType.greedyString()).executes { context ->
								help(
									context,
									IntegerArgumentType.getInteger(context, "page"),
									StringArgumentType.getString(context, "search")
								)
							}
						)
				)
			)
			.then(
				argument<S, String>("search", excluding("-p", StringArgumentType.greedyString())).executes { context ->
					help(context, FIRST_PAGE, StringArgumentType.getString(context, "search"))
				}
			)

	private fun help(context: CommandContext<S>, page: Int, search: String): Int {
		val source = context.source
		val paths = commandPaths(context.rootNode).filter { search.isEmpty() || it.contains(search, ignoreCase = true) }
		if (paths.isEmpty()) return report(source, "No Dhen command matches '$search'.")
		val pages = (paths.size + PAGE_SIZE - 1) / PAGE_SIZE
		val shown = page.coerceIn(FIRST_PAGE, pages)
		report(source, "Dhen commands — page $shown of $pages" + if (search.isEmpty()) "" else ", matching '$search'")
		for (path in paths.drop((shown - 1) * PAGE_SIZE).take(PAGE_SIZE)) feedback(source, suggestion(path))
		if (pages > 1) report(source, "Type /dhen help -p <page> to read the rest.")
		return Command.SINGLE_SUCCESS
	}

	private fun suggestion(path: String): Component =
		DhenType.suggestedOverWorld(path, path.substringBefore(ARGUMENT_MARK), hover(path))

	private fun hover(path: String): String {
		val registration = registrations[path.substringBefore(' ').removePrefix("/")] ?: return CLICK_HINT
		if (registration.aliases.isEmpty()) return CLICK_HINT
		return "Also " + registration.aliases.joinToString(", ") { "/$it" }
	}

	private fun commandPaths(root: CommandNode<S>): List<String> {
		val paths = ArrayList<String>()
		for (name in listOf(CORE_COMMAND, REMIND_COMMAND) + registrations.values.map { it.literals.first() }) {
			val node = root.getChild(name) ?: continue
			collectPaths(node, "/$name", paths)
		}
		return paths
	}

	private fun collectPaths(node: CommandNode<S>, path: String, into: MutableList<String>) {
		if (node.command != null) into += path
		for (child in node.children) collectPaths(child, "$path ${child.usageText}", into)
	}

	private fun debugCommand(): LiteralArgumentBuilder<S> =
		literal<S>("debug")
			.executes { context ->
				reportAll(context.source, diagnostics.lines() + REMIND_OWNER + registrations.values.map(::ownerLine))
			}
			.then(
				literal<S>("deep")
					.then(deepMode("on", true))
					.then(deepMode("off", false))
			)
			.then(literal<S>("arc").executes { context -> report(context.source, previews.openArcPreview()) })
			.then(
				literal<S>("names")
					.executes { context -> report(context.source, textReplacer.clearNames()) }
					.then(
						argument<S, String>("name", StringArgumentType.word()).then(
							argument<S, String>("replacement", StringArgumentType.greedyString())
								.executes { context ->
									report(
										context.source,
										textReplacer.name(
											StringArgumentType.getString(context, "name"),
											StringArgumentType.getString(context, "replacement")
										)
									)
								}
						)
					)
			)
			.then(literal<S>("alert").executes { context -> report(context.source, previews.showAlert()) })
			.then(literal<S>("notify").executes { context -> report(context.source, previews.showNotice()) })
			.then(literal<S>("worldrender").executes { context -> report(context.source, previews.toggleWorldRender()) })
			.then(literal<S>("highlight").executes { context -> report(context.source, previews.toggleHighlight()) })
			.then(soundCommand())
			.then(
				literal<S>("party").executes { context -> reportAll(context.source, diagnostics.partyLines()) }
			)
			.then(
				literal<S>("scoreboard").executes { context -> reportAll(context.source, diagnostics.scoreboardLines()) }
			)
			.then(
				literal<S>("tablist").executes { context -> reportAll(context.source, diagnostics.tablistWidgetLines()) }
			)
			.then(
				literal<S>("stats").executes { context -> reportAll(context.source, diagnostics.statsLines()) }
			)
			.then(
				literal<S>("item").executes { context -> reportAll(context.source, diagnostics.heldItemLines()) }
			)
			.then(
				literal<S>("value").executes { context -> reportAll(context.source, diagnostics.valueLines()) }
			)
			.then(
				literal<S>("prices")
					.executes { context -> reportAll(context.source, diagnostics.priceLines(toggle = false)) }
					.then(
						literal<S>("download").executes { context -> reportAll(context.source, diagnostics.priceLines(toggle = true)) }
					)
					.then(
						argument<S, String>(
							"item",
							excluding("download", StringArgumentType.greedyString())
						).executes { context ->
							reportAll(context.source, diagnostics.marketLines(StringArgumentType.getString(context, "item")))
						}
					)
			)
			.then(
				literal<S>("mayor")
					.executes { context -> reportAll(context.source, diagnostics.mayorLines(toggle = false)) }
					.then(
						literal<S>("download").executes { context -> reportAll(context.source, diagnostics.mayorLines(toggle = true)) }
					)
			)
			.then(
				literal<S>("repo")
					.executes { context -> reportAll(context.source, diagnostics.repoLines(toggle = false)) }
					.then(
						literal<S>("download").executes { context -> reportAll(context.source, diagnostics.repoLines(toggle = true)) }
					)
					.then(
						argument<S, String>(
							"item",
							excluding("download", StringArgumentType.greedyString())
						).executes { context ->
							reportAll(context.source, diagnostics.itemLines(StringArgumentType.getString(context, "item")))
						}
					)
			)
			.then(
				literal<S>("profile")
					.executes { context -> reportAll(context.source, diagnostics.profileLines()) }
					.then(
						literal<S>("url")
							.executes { context -> report(context.source, diagnostics.profileProxyShown()) }
							.then(
								literal<S>("clear")
									.executes { context -> persisted(context.source, diagnostics.profileProxyCleared()) }
							)
							.then(
								argument<S, String>(
									"address",
									excluding("clear", StringArgumentType.greedyString())
								).executes { context ->
									val address = StringArgumentType.getString(context, "address")
									report(context.source, diagnostics.profileProxy(address, persistCore))
								}
							)
					)
					.then(
						argument<S, String>(
							"name",
							excluding("url", StringArgumentType.word())
						).executes { context ->
							val name = StringArgumentType.getString(context, "name")
							report(context.source, "Looking up $name…")
							answering(context.source) { notify -> diagnostics.profileLookup(name, notify) }
						}
					)
			)

	private fun remindMeCommand(): LiteralArgumentBuilder<S> =
		literal<S>(REMIND_COMMAND)
			.executes { context -> reportComponents(context.source, reminders.list()) }
			.then(literal<S>("list").executes { context -> reportComponents(context.source, reminders.list()) })
			.then(literal<S>("gui").executes { context -> report(context.source, reminders.openManager()) })
			.then(literal<S>("help").executes { context -> reportAll(context.source, reminderHelp()) })
			.then(createReminderCommand())
			.then(
				literal<S>("todo").then(
					argument<S, String>("text", StringArgumentType.greedyString())
						.executes { context -> stored(context.source, reminders.addTodo(text(context))) }
				)
			)
			.then(reminderTarget("done", reminders::complete))
			.then(reminderTarget("toggle", reminders::toggle))
			.then(
				literal<S>("remove")
					.then(literal<S>("all").executes { context -> reportComponents(context.source, reminders.removeAll(false)) })
					.then(
						literal<S>("all_confirmed").executes { context ->
							reportComponents(context.source, reminders.removeAll(true))
						}
					)
					.then(
						argument<S, Int>("id", IntegerArgumentType.integer(1))
							.suggests(suggesting(reminders::ids))
							.executes { context -> stored(context.source, reminders.remove(target(context))) }
					)
			)
			.then(
				literal<S>("rename").then(
					argument<S, Int>("id", IntegerArgumentType.integer(1))
						.suggests(suggesting(reminders::ids))
						.then(
							argument<S, String>("name", StringArgumentType.greedyString()).executes { context ->
								stored(context.source, reminders.rename(target(context), StringArgumentType.getString(context, "name")))
							}
						)
				)
			)
			.then(
				literal<S>("snooze").then(
					argument<S, Int>("id", IntegerArgumentType.integer(1))
						.suggests(suggesting(reminders::ids))
						.then(
							argument<S, Int>("amount", IntegerArgumentType.integer(1)).then(
								argument<S, String>("unit", StringArgumentType.word())
									.suggests(suggesting(::reminderUnits))
									.executes { context ->
										stored(
											context.source,
											reminders.snooze(
												target(context),
												IntegerArgumentType.getInteger(context, "amount"),
												StringArgumentType.getString(context, "unit")
											)
										)
									}
							)
						)
				)
			)
			.then(literal<S>("export").executes { context -> report(context.source, reminders.exportTodos()) })
			.then(literal<S>("import").executes { context -> stored(context.source, reminders.importTodos()) })

	private fun createReminderCommand(): LiteralArgumentBuilder<S> =
		literal<S>("create").then(
			argument<S, Int>("amount", IntegerArgumentType.integer(1)).then(
				argument<S, String>("unit", StringArgumentType.word())
					.suggests(suggesting(::reminderUnits))
					.then(
						argument<S, String>("trigger", StringArgumentType.word())
							.suggests(suggesting(::reminderTriggers))
							.then(
								argument<S, String>("output", StringArgumentType.word())
									.suggests(suggesting(::reminderOutputs))
									.then(
										literal<S>("message").then(
											argument<S, String>("message", StringArgumentType.greedyString())
												.executes { context -> created(context, null, null) }
										)
									)
									.then(
										literal<S>("repeat").then(
											argument<S, String>("times", StringArgumentType.word())
												.suggests(suggesting(::reminderRepeats))
												.then(
													argument<S, String>("message", StringArgumentType.greedyString())
														.executes { context ->
															created(context, StringArgumentType.getString(context, "times"), null)
														}
												)
										)
									)
									.then(
										literal<S>("name").then(
											argument<S, String>("label", StringArgumentType.word()).then(
												argument<S, String>("message", StringArgumentType.greedyString())
													.executes { context ->
														created(context, null, StringArgumentType.getString(context, "label"))
													}
											)
										)
									)
							)
					)
			)
		)

	private fun reminderTarget(name: String, action: (Int) -> String): LiteralArgumentBuilder<S> =
		literal<S>(name).then(
			argument<S, Int>("id", IntegerArgumentType.integer(1))
				.suggests(suggesting(reminders::ids))
				.executes { context -> stored(context.source, action(target(context))) }
		)

	private fun created(context: CommandContext<S>, repeat: String?, label: String?): Int = stored(
		context.source,
		reminders.create(
			IntegerArgumentType.getInteger(context, "amount"),
			StringArgumentType.getString(context, "unit"),
			StringArgumentType.getString(context, "trigger"),
			StringArgumentType.getString(context, "output"),
			repeat,
			label,
			StringArgumentType.getString(context, "message")
		)
	)

	private fun target(context: CommandContext<S>): Int = IntegerArgumentType.getInteger(context, "id")

	private fun text(context: CommandContext<S>): String = StringArgumentType.getString(context, "text")

	private fun reportComponents(source: S, lines: List<Component>): Int {
		for (line in lines) feedback(source, line)
		return Command.SINGLE_SUCCESS
	}

	private fun reminderUnits(): List<String> = REMINDER_UNITS

	private fun reminderTriggers(): List<String> = REMINDER_TRIGGERS

	private fun reminderOutputs(): List<String> = REMINDER_OUTPUTS

	private fun reminderRepeats(): List<String> = REMINDER_REPEATS

	private fun reminderHelp(): List<String> = REMINDER_HELP

	private fun soundCommand(): LiteralArgumentBuilder<S> =
		literal<S>("sound").then(
			argument<S, Identifier>("identifier", IdentifierArgument.id()).then(
				argument<S, Int>("percent", IntegerArgumentType.integer()).executes { context ->
					val identifier = context.getArgument("identifier", Identifier::class.java)
					val percent = IntegerArgumentType.getInteger(context, "percent")
					report(context.source, sounds.setVolume(identifier, percent))
				}
			)
		)

	private fun persisted(source: S, message: String): Int {
		persistCore()
		return report(source, message)
	}

	private fun deepMode(name: String, enabled: Boolean): LiteralArgumentBuilder<S> =
		literal<S>(name).executes { context ->
			diagnostics.deepMode = enabled
			report(context.source, "Deep profiling ${if (enabled) "enabled" else "disabled"}.")
		}

	private fun ownerLine(registration: RegisteredCommand<S>): String =
		(listOf(registration.name) + registration.aliases).joinToString(", ") { "/$it" } + " — ${registration.owner}"

	private fun wireName(name: String): String = name.replace(' ', '_')

	private fun excluding(literal: String, delegate: StringArgumentType): ArgumentType<String> =
		LiteralExcludingStringArgument(literal, delegate)

	internal companion object {
		internal const val CORE_COMMAND = "dhen"
		internal const val REMIND_COMMAND = "remindme"

		internal val CORE_NAMES = listOf(CORE_COMMAND, "dh")

		internal val RESERVED = CORE_NAMES + REMIND_COMMAND

		private const val PAGE_SIZE = 15
		private const val FIRST_PAGE = 1
		private const val CLICK_HINT = "Click to put this in the chat box."
		private const val ARGUMENT_MARK = " <"
		private const val ADD_USAGE =
			"Give the keys and then the command, like /dhen hotkey add G,H /warp crypts."
		private const val WHERE_USAGE =
			"Give the number from /dhen hotkey list and then the scope, like /dhen hotkey where 1 island:hub."

		private val REMIND_OWNER = listOf("/$REMIND_COMMAND — dhen")

		private val REMINDER_UNITS = listOf("seconds", "minutes", "hours", "days", "sec", "min", "hour", "day")
		private val REMINDER_TRIGGERS = listOf("while_playing", "real_time")
		private val REMINDER_OUTPUTS = listOf("chat", "title_box", "chat_and_title", "sound_only")
		private val REMINDER_REPEATS = listOf("until_removed", "2", "3", "5", "10")
		private val REMINDER_HELP = listOf(
			"§6§lRemindMe",
			"§e/remindme create <amount> <unit> <trigger> <output> message <message>",
			"  §7Units: seconds, minutes, hours, days",
			"  §7Triggers: while_playing (pauses when you log out) or real_time",
			"  §7Output: chat, title_box, chat_and_title, sound_only",
			"§e/remindme create ... repeat <times> <message> §7— times is until_removed, or 2 and up",
			"§e/remindme create ... name <label> <message> §7— gives the reminder a display name",
			"§e/remindme todo <text> §7— a reminder with no time, listed on the HUD",
			"§e/remindme done <id> §7— ticks a todo off",
			"§e/remindme gui §7— opens the reminder manager",
			"§e/remindme rename <id> <name>",
			"§e/remindme toggle <id>",
			"§e/remindme snooze <id> <amount> <unit>",
			"§e/remindme remove <id> §7or §e/remindme remove all",
			"§e/remindme list",
			"§e/remindme export §7and §e/remindme import §7— todos through the clipboard"
		)
	}
}

private class LiteralExcludingStringArgument(
	private val literal: String,
	private val delegate: StringArgumentType
) : ArgumentType<String> {
	override fun parse(reader: StringReader): String {
		val start = reader.cursor
		val value = delegate.parse(reader)
		if (value != literal) return value
		reader.cursor = start
		throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
	}

	override fun getExamples(): Collection<String> = delegate.examples
}

class RegisteredCommand<S> internal constructor(
	val name: String,
	val aliases: List<String>,
	val owner: String,
	internal val literals: List<String>,
	internal val build: LiteralArgumentBuilder<S>.() -> Unit
)
