package io.github.dzkchen.dhen.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object CommandBridge {
	fun register(runtime: DhenRuntime) {
		ClientCommandRegistrationCallback.EVENT.register(
			ClientCommandRegistrationCallback { dispatcher, _ ->
				dispatcher.register(
					ClientCommands.literal("dhen")
						.then(
							ClientCommands.literal("module")
								.then(ClientCommands.literal("list").executes { ctx -> listModules(ctx, runtime) })
								.then(
									ClientCommands.literal("enable").then(
										ClientCommands.argument("id", StringArgumentType.greedyString())
											.executes { ctx -> setModule(ctx, runtime, enable = true) },
									),
								)
								.then(
									ClientCommands.literal("disable").then(
										ClientCommands.argument("id", StringArgumentType.greedyString())
											.executes { ctx -> setModule(ctx, runtime, enable = false) },
									),
								),
						)
						.then(ClientCommands.literal("addons").executes { ctx -> listAddons(ctx, runtime) })
						.then(ClientCommands.literal("diagnostics").executes { ctx -> diagnostics(ctx, runtime) }),
				)
			},
		)
	}

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
			feedback(ctx, "  ${record.id.value} — ${record.state}")
		}
		return 1
	}

	private fun listAddons(ctx: CommandContext<FabricClientCommandSource>, runtime: DhenRuntime): Int {
		val addons = runtime.diagnostics().addons
		if (addons.isEmpty()) {
			feedback(ctx, "[Dhen] No addons discovered.")
			return 1
		}
		feedback(ctx, "[Dhen] Addons:")
		for (addon in addons) {
			feedback(ctx, "  ${addon.addonId} v${addon.version} (${addon.moduleCount} module(s))")
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
