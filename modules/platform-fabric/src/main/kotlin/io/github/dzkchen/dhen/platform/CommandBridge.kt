package io.github.dzkchen.dhen.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

object CommandBridge {
	fun register(runtime: DhenRuntime) {
		val moduleIds = SuggestionProvider<FabricClientCommandSource> { _, builder ->
			SharedSuggestionProvider.suggest(runtime.modules().map { it.id.value }, builder)
		}
		val addonIds = SuggestionProvider<FabricClientCommandSource> { _, builder ->
			SharedSuggestionProvider.suggest(runtime.addonIds(), builder)
		}

		ClientCommandRegistrationCallback.EVENT.register(
			ClientCommandRegistrationCallback { dispatcher, _ ->
				dispatcher.register(
					ClientCommands.literal("dhen")
						.then(
							ClientCommands.literal("module")
								.then(ClientCommands.literal("list").executes { ctx -> listModules(ctx, runtime) })
								.then(moduleArg("enable", moduleIds) { ctx -> setModule(ctx, runtime, enable = true) })
								.then(moduleArg("disable", moduleIds) { ctx -> setModule(ctx, runtime, enable = false) }),
						)
						.then(ClientCommands.literal("addons").executes { ctx -> listAddons(ctx, runtime) })
						.then(addonArg("add", addonIds) { ctx -> markPending(ctx, runtime, "add") })
						.then(addonArg("remove", addonIds) { ctx -> markPending(ctx, runtime, "remove") })
						.then(addonArg("update", addonIds) { ctx -> markPending(ctx, runtime, "update") })
						.then(ClientCommands.literal("diagnostics").executes { ctx -> diagnostics(ctx, runtime) }),
				)
			},
		)
	}

	private fun moduleArg(
		name: String,
		suggestions: SuggestionProvider<FabricClientCommandSource>,
		action: (CommandContext<FabricClientCommandSource>) -> Int,
	): LiteralArgumentBuilder<FabricClientCommandSource> =
		ClientCommands.literal(name).then(
			ClientCommands.argument("id", StringArgumentType.greedyString())
				.suggests(suggestions)
				.executes { action(it) },
		)

	private fun addonArg(
		name: String,
		suggestions: SuggestionProvider<FabricClientCommandSource>,
		action: (CommandContext<FabricClientCommandSource>) -> Int,
	): LiteralArgumentBuilder<FabricClientCommandSource> =
		ClientCommands.literal(name).then(
			ClientCommands.argument("addonId", StringArgumentType.word())
				.suggests(suggestions)
				.executes { action(it) },
		)

	private fun feedback(ctx: CommandContext<FabricClientCommandSource>, message: String) {
		ctx.source.sendFeedback(Component.literal(message))
	}

	private fun listModules(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime): Int {
		val modules = runtime.modules()
		if (modules.isEmpty()) {
			feedback(ctx, "[Dhen] No modules registered.")
			return 1
		}
		feedback(ctx, "[Dhen] Modules:")
		for (record in modules) {
			feedback(ctx, "  ${record.id.value} - ${record.module.metadata.category.displayName} - ${record.state}")
		}
		return 1
	}

	private fun listAddons(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime): Int {
		val addons = runtime.diagnostics().addons
		if (addons.isEmpty()) {
			feedback(ctx, "[Dhen] No addons discovered.")
		} else {
			feedback(ctx, "[Dhen] Addons:")
			for (addon in addons) {
				feedback(ctx, "  ${addon.addonId} v${addon.version} - ${addon.artifactType} - ${addon.displayAuthors} - ${addon.sourceType}: ${addon.displaySource} (${addon.moduleCount} module(s))")
			}
		}
		val pending = runtime.pendingRestartAddons()
		if (pending.isNotEmpty()) {
			feedback(ctx, "[Dhen] Pending restart:")
			for (state in pending.values) {
				feedback(ctx, "  ${state.addonId} - ${state.operation} (restart required)")
			}
		}
		return 1
	}

	private fun setModule(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime, enable: Boolean): Int {
		val raw = StringArgumentType.getString(ctx, "id")
		val moduleId = try {
			ModuleId(raw)
		} catch (t: IllegalArgumentException) {
			feedback(ctx, "[Dhen] Invalid module id: $raw")
			return 0
		}
		if (runtime.modules().none { it.id == moduleId }) {
			feedback(ctx, "[Dhen] Unknown module: $raw")
			return 0
		}
		if (enable) {
			val ok = runtime.enableModule(moduleId)
			feedback(ctx, if (ok) "[Dhen] Enabled $raw" else "[Dhen] Failed to enable $raw")
		} else {
			runtime.disableModule(moduleId)
			feedback(ctx, "[Dhen] Disabled $raw")
		}
		return 1
	}

	private fun markPending(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime, operation: String): Int {
		val raw = StringArgumentType.getString(ctx, "addonId")
		val addonId = try {
			AddonId(raw)
		} catch (t: IllegalArgumentException) {
			feedback(ctx, "[Dhen] Invalid addon id: $raw")
			return 0
		}
		if (operation != "add" && raw !in runtime.addonIds()) {
			feedback(ctx, "[Dhen] Unknown addon: $raw")
			return 0
		}
		runtime.markAddonPendingRestart(addonId, operation)
		feedback(ctx, "[Dhen] Marked '$raw' to $operation on restart. Native addons load only after Minecraft restarts.")
		return 1
	}

	private fun diagnostics(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime): Int {
		val snapshot = runtime.diagnostics()
		feedback(ctx, "[Dhen] Diagnostics — ${snapshot.addons.size} addon(s), ${snapshot.modules.size} module(s), ${snapshot.totalActiveHandles} active handle(s)")
		for (module in snapshot.modules) {
			val failure = module.failureReason?.let { " failure='$it'" } ?: ""
			feedback(ctx, "  ${module.moduleId}: ${module.state} handles=${module.activeHandles} last=${module.lastTransition}$failure")
		}
		if (snapshot.failedModules.isNotEmpty()) {
			feedback(ctx, "[Dhen] ${snapshot.failedModules.size} module(s) FAILED")
		}
		return 1
	}
}
