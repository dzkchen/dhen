package io.github.dzkchen.dhen

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.command.CommandRegistry
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.CorePersistence
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.HypixelModApi
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.profile.PlayerProfiles
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.diagnostic.WorldRenderProbe
import io.github.dzkchen.dhen.event.ContainerHooks
import io.github.dzkchen.dhen.event.Hooks
import io.github.dzkchen.dhen.event.InputHooks
import io.github.dzkchen.dhen.event.InteractionHooks
import io.github.dzkchen.dhen.event.NetworkHooks
import io.github.dzkchen.dhen.event.RenderHooks
import io.github.dzkchen.dhen.event.ScreenHooks
import io.github.dzkchen.dhen.event.TickHooks
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldHooks
import io.github.dzkchen.dhen.event.WorldRenderHooks
import io.github.dzkchen.dhen.gui.ClickGuiShellScreen
import io.github.dzkchen.dhen.gui.ClickGuiState
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenFont
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.module.ModuleNotifier
import io.github.dzkchen.dhen.module.PlaceholderModule
import io.github.dzkchen.dhen.render.WorldRenderTypes
import io.github.dzkchen.dhen.theme.ThemeRuntime
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudEditorScreen
import io.github.dzkchen.dhen.ui.hud.HudRuntime
import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.TickClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.Util
import net.minecraft.world.InteractionResult
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	private val clientThread = ClientThreadDispatcher()
	private val announcements = AnnouncementBuffer(ANNOUNCEMENT_CAPACITY)
	internal val firstRunExperience = FirstRunExperience(::persistCore, ::announceComponent)

	val modules: ModuleManager = ModuleManager(
		notifier = ModuleNotifier.chatBacked({ Minecraft.getInstance().execute(it) }, ::announceComponent),
		clientDispatcher = clientThread,
		currentScreen = { Minecraft.getInstance().gui.screen() }
	)
	private val hudRuntime = HudRuntime(modules, DhenAlert.elements)

	internal val hooks: List<Hooks> by lazy {
		listOf(
			NetworkHooks,
			ScreenHooks,
			ContainerHooks,
			InputHooks,
			WorldHooks,
			RenderHooks,
			InteractionHooks,
			WorldRenderHooks,
			TickHooks,
			HypixelLocationHooks,
			ScoreboardHooks,
			TablistHooks,
			TabWidgetHooks,
			PartyHooks,
			PlayerStatsHooks,
			firstRunExperience,
			HypixelModApi
		)
	}

	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val stores = mutableListOf<ConfigStore>()
	private val failsafe = Failsafe()
	private lateinit var coreStore: ConfigStore
	private lateinit var moduleStore: ConfigStore
	private lateinit var clickGuiView: ClickGuiState

	override fun onInitializeClient() {
		failsafe.guard("initialization", ::initialize)
		if (failsafe.failed) latchOff()
	}

	private fun initialize() {
		val configRoot = FabricLoader.getInstance().configDir.resolve(MOD_ID)
		coreStore = flushedOnStop(configRoot.resolve("core.json"), CorePersistence.migrations)
		moduleStore = flushedOnStop(configRoot.resolve("modules.json"), ModulePersistence.migrations)
		val coreState = CorePersistence.apply(coreStore.load(), hudRuntime.coreElements)
		clickGuiView = coreState.clickGui
		val themes = ThemeRuntime(configRoot, ioScope, clientThread, ::persistCore, ::announce) {
			Util.getPlatform().openPath(it)
		}
		val commands = CommandRegistry<FabricClientCommandSource>(
			modules,
			openHudEditor = ::openHudEditor,
			persistCore = ::persistCore,
			resetHudLayout = ::resetHudLayout,
			themes = themes,
			toggleWorldRender = WorldRenderProbe::toggle,
			showAlert = { DhenAlert.show("Dhen Alert", "Title and subtitle preview") },
			available = { !failsafe.failed },
			persistModules = ::persistModules
		) { source, message ->
			source.sendFeedback(DhenType.overWorld(message))
		}
		themes.reload()
		modules.registerAll(
			PlaceholderModule(),
			PlaceholderModule(
				name = "Test Overlay",
				category = Category.VISUAL,
				description = "Second placeholder for search and keyboard navigation.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN,
				hudAnchor = HudAnchor.TOP_RIGHT,
				hudBackground = true
			),
			PlaceholderModule(
				name = "Sample Timer",
				category = Category.COMBAT,
				description = "Third placeholder; matches a search on its description only.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN,
				hudAnchor = HudAnchor.BOTTOM_RIGHT
			)
		)
		ModulePersistence.apply(modules, moduleStore.load())
		modules.stateListener = { Minecraft.getInstance().execute(::persistModules) }
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			failsafe.guard("command registration") { commands.install(dispatcher) }
		}
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, id("hud")) { graphics, _ ->
			failsafe.guard("HUD render") { hudRuntime.render(graphics, Minecraft.getInstance().font) }
		}
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			id("text_measurements"),
			ResourceManagerReloadListener { failsafe.guard("resource reload") { invalidateTextMeasurements() } }
		)

		val openGuiKey = KeyMappingHelper.registerKeyMapping(
			KeyMapping(
				"key.dhen.open_gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.MISC
			)
		)
		ClientTickEvents.START_CLIENT_TICK.register { client ->
			if (failsafe.failed) latchOff()
			else {
				DhenAlert.tick()
				if (client.level != null) failsafe.guard("client tick start") { TickHooks.clientTickStarted() }
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			if (failsafe.failed) latchOff()
			else failsafe.guard("client tick") { tick(client, openGuiKey) }
		}
		UseBlockCallback.EVENT.register { player, _, hand, hit ->
			interacted("use block") { InteractionHooks.useBlock(player, hand, hit) }
		}
		UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
			interacted("use entity") { InteractionHooks.useEntity(player, hand, entity) }
		}
		UseItemCallback.EVENT.register { player, _, hand ->
			interacted("use item") { InteractionHooks.useItem(player, hand) }
		}
		AttackBlockCallback.EVENT.register { player, _, _, pos, _ ->
			interacted("attack block") { InteractionHooks.attackBlock(player, pos) }
		}
		AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
			interacted("attack entity") { InteractionHooks.attackEntity(player, entity) }
		}
		ClientPreAttackCallback.EVENT.register { _, _, _ ->
			failsafe.guard("attack") { InteractionHooks.attack() } ?: false
		}
		LevelRenderEvents.COLLECT_SUBMITS.register { context ->
			failsafe.guard("world render") { WorldRenderHooks.render(context) }
		}
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register { context ->
			failsafe.guard("world render probe after translucent terrain") { WorldRenderProbe.afterTranslucentTerrain(context) }
		}
		ClientPlayConnectionEvents.INIT.register { _, _ -> worldChanged(WorldChange.INIT) }
		ClientPlayConnectionEvents.JOIN.register { _, _, _ -> worldChanged(WorldChange.JOIN) }
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> worldChanged(WorldChange.DISCONNECT) }
		ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
			failsafe.guard("entity unload") { WorldHooks.entityUnloaded(entity) }
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			stores.forEach { it.flush() }
			ioScope.cancel()
		}
		NetworkHooks.install(modules.eventBus)
		ScreenHooks.install(modules.eventBus)
		ContainerHooks.install(modules.eventBus)
		InputHooks.install(modules.eventBus)
		WorldHooks.install(modules.eventBus)
		RenderHooks.install(modules.eventBus)
		InteractionHooks.install(modules.eventBus)
		WorldRenderHooks.install(modules.eventBus)
		TickHooks.install(modules.eventBus)
		HypixelLocationHooks.install(modules.eventBus)
		ScoreboardHooks.install(modules.eventBus)
		TablistHooks.install(modules.eventBus)
		TabWidgetHooks.install(modules.eventBus)
		PartyHooks.install(modules.eventBus)
		PlayerStatsHooks.install(modules.eventBus)
		WorldRenderTypes.initialize()
		firstRunExperience.install(modules.eventBus, coreState.welcomeShown)
		ItemRepo.install(ioScope, configRoot.resolve("repo"))
		Prices.install(ioScope, modules.eventBus, clientThread)
		MayorService.install(ioScope, modules.eventBus, clientThread)
		PlayerProfiles.install(ioScope, clientThread, baseUrl = { ClientPrefs.profileProxy.value })
		HypixelModApi.install()
		WorldRenderProbe.install(modules.eventBus)
		LOGGER.info("Dhen initialized")
	}

	private var latched = false

	internal fun latchOff() {
		if (latched) return
		latched = true
		DhenAlert.clear()
		if (DhenFont.latchOff()) contained("font caches", ::fontChanged)
		contained("client thread", clientThread::shutdown)
		contained("tick clock", TickClock::shutdown)
		contained("hook registry") { hooks.forEach { contained(it.feed, it::uninstall) } }
		contained("world render probe", WorldRenderProbe::uninstall)
		contained("item repository", ItemRepo::uninstall)
		contained("price feed", Prices::uninstall)
		contained("mayor feed", MayorService::uninstall)
		contained("player profiles", PlayerProfiles::uninstall)
	}

	internal fun contained(label: String, teardown: () -> Unit) {
		try {
			teardown()
		} catch (throwable: Throwable) {
			LOGGER.error("Dhen could not shut {} down and it stays installed until restart", label, throwable)
		}
	}

	private fun interacted(label: String, interaction: () -> InteractionResult): InteractionResult =
		failsafe.guard(label, interaction) ?: InteractionResult.PASS

	private fun worldChanged(phase: WorldChange) {
		val client = Minecraft.getInstance()
		if (client.isSameThread) failsafe.guard("world change") {
			DhenAlert.clear()
			if (phase == WorldChange.JOIN) flushAnnouncements(client)
			WorldHooks.worldChanged(phase)
		}
		else client.execute { worldChanged(phase) }
	}

	private fun tick(client: Minecraft, openGuiKey: KeyMapping) {
		TickHooks.clientTicked(client.level != null)
		clientThread.drainQueue()
		ContainerHooks.tick()
		val options = client.options
		if (DhenType.fontOptionsChanged(options.forceUnicodeFont().get(), options.japaneseGlyphVariants().get())) {
			invalidateTextMeasurements()
		}
		if (openGuiKey.consumeClick() && client.level != null) clickGuiScreen()?.let(client.gui::setScreen)
	}

	private fun openHudEditor() = clientThread.dispatch(EmptyCoroutineContext) {
		Minecraft.getInstance().gui.setScreen(
			HudEditorScreen(
				modules,
				::persistHudLayouts,
				hudRuntime.coreElements,
				DhenAlert::beginPreview,
				DhenAlert::endPreview
			)
		)
	}

	private fun announce(message: String) {
		announceComponent(DhenType.overWorld(message))
	}

	private fun announceComponent(message: Component) {
		val player = Minecraft.getInstance().player
		if (player == null) announcements.add(message)
		else player.sendSystemMessage(message)
	}

	private fun flushAnnouncements(client: Minecraft) {
		val player = client.player ?: return
		announcements.flush(player::sendSystemMessage)
	}

	private fun invalidateTextMeasurements() {
		DhenType.invalidateMeasurements()
		hudRuntime.invalidateMeasurements()
		(Minecraft.getInstance().gui.screen() as? ClickGuiShellScreen)?.invalidateMeasurements()
	}

	private fun fontChanged() {
		invalidateTextMeasurements()
		Minecraft.getInstance().gui.hud.chat.rescaleChat()
	}

	private fun resetHudLayout(): Int {
		val reset = hudRuntime.resetLayouts()
		if (reset > 0) persistHudLayouts()
		return reset
	}

	private fun flushedOnStop(path: Path, migrations: List<(JsonObject) -> Unit>): ConfigStore =
		ConfigStore(path, ioScope, migrations).also { stores += it }

	private fun persistModules() {
		moduleStore.save(ModulePersistence.snapshot(modules))
	}

	private fun persistHudLayouts() {
		persistModules()
		persistCore()
	}

	private fun persistCore() {
		coreStore.save(CorePersistence.snapshot(clickGuiView, firstRunExperience.shown, hudRuntime.coreElements))
	}

	internal fun clickGuiScreen(parent: Screen? = null): Screen? = failsafe.guard("click GUI open") {
		ClickGuiShellScreen(
			Category.entries.toList(),
			modules,
			clickGuiView,
			persistCore = ::persistCore,
			persistModules = ::persistModules,
			fontChanged = ::fontChanged,
			parent = parent
		)
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)

	private const val ANNOUNCEMENT_CAPACITY = 32
}
