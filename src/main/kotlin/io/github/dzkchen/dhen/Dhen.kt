package io.github.dzkchen.dhen

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.command.CommandRegistry
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.CorePersistence
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.HypixelModApi
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.cookie.CookieHooks
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.maxwell.MaxwellHooks
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.pet.PetHooks
import io.github.dzkchen.dhen.data.pickup.PickupHooks
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.profile.PlayerProfiles
import io.github.dzkchen.dhen.data.quiver.QuiverHooks
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.PackModelRepo
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
import io.github.dzkchen.dhen.features.privacy.ChannelSpoofing
import io.github.dzkchen.dhen.command.HudCommands
import io.github.dzkchen.dhen.command.PreviewCommands
import io.github.dzkchen.dhen.command.SoundCommands
import io.github.dzkchen.dhen.event.TabCompleteHooks
import io.github.dzkchen.dhen.features.chat.ChatMacros
import io.github.dzkchen.dhen.features.chat.Commands
import io.github.dzkchen.dhen.features.chat.ChatTweaks
import io.github.dzkchen.dhen.features.chat.PartyHelper
import io.github.dzkchen.dhen.features.chat.SkyBlockKick
import io.github.dzkchen.dhen.features.privacy.ModWhitelist
import io.github.dzkchen.dhen.features.privacy.ServerPackBypass
import io.github.dzkchen.dhen.features.privacy.SpoofAsVanilla
import io.github.dzkchen.dhen.features.qol.ArrowFix
import io.github.dzkchen.dhen.features.qol.AutoSprint
import io.github.dzkchen.dhen.features.qol.NoItemPlace
import io.github.dzkchen.dhen.features.qol.PickupLog
import io.github.dzkchen.dhen.features.inventory.InventorySearch
import io.github.dzkchen.dhen.features.inventory.StorageOverlay
import io.github.dzkchen.dhen.features.inventory.StorageSnapshots
import io.github.dzkchen.dhen.features.inventory.AnvilHelper
import io.github.dzkchen.dhen.features.inventory.AuctionPriceInput
import io.github.dzkchen.dhen.features.inventory.ChickenHeadTimer
import io.github.dzkchen.dhen.features.inventory.CrownOfAvarice
import io.github.dzkchen.dhen.features.inventory.FireFreeze
import io.github.dzkchen.dhen.features.inventory.FireVeilWand
import io.github.dzkchen.dhen.features.inventory.ItemAbilities
import io.github.dzkchen.dhen.features.inventory.ContainerState
import io.github.dzkchen.dhen.features.inventory.ItemRarityOverlay
import io.github.dzkchen.dhen.features.inventory.ProtectItem
import io.github.dzkchen.dhen.features.inventory.SlotBinding
import io.github.dzkchen.dhen.features.inventory.WardrobeKeybinds
import io.github.dzkchen.dhen.features.inventory.PetKeybinds
import io.github.dzkchen.dhen.features.inventory.LoadoutKeybinds
import io.github.dzkchen.dhen.features.inventory.ItemTooltip
import io.github.dzkchen.dhen.features.qol.Tweaks
import io.github.dzkchen.dhen.features.visual.Animations
import io.github.dzkchen.dhen.features.visual.Camera
import io.github.dzkchen.dhen.features.visual.CustomScoreboard
import io.github.dzkchen.dhen.features.dungeon.ClassColors
import io.github.dzkchen.dhen.features.visual.Box3D
import io.github.dzkchen.dhen.features.visual.DamageSplash
import io.github.dzkchen.dhen.features.visual.EntityHighlight
import io.github.dzkchen.dhen.features.visual.HidePlayers
import io.github.dzkchen.dhen.features.visual.MaskTimers
import io.github.dzkchen.dhen.features.visual.MobHighlight
import io.github.dzkchen.dhen.features.visual.NametagTweaks
import io.github.dzkchen.dhen.features.visual.RenderOptimizer
import io.github.dzkchen.dhen.features.visual.MoveableVanillaHud
import io.github.dzkchen.dhen.features.visual.PetDisplay
import io.github.dzkchen.dhen.features.visual.PlayerStatsHud
import io.github.dzkchen.dhen.features.visual.QuiverDisplay
import io.github.dzkchen.dhen.features.visual.RevertAxes
import io.github.dzkchen.dhen.features.visual.TabWidgetDisplay
import io.github.dzkchen.dhen.features.visual.TimeChanger
import io.github.dzkchen.dhen.features.visual.UtilityHuds
import io.github.dzkchen.dhen.features.visual.VisualTweaks
import io.github.dzkchen.dhen.features.visual.Waypoints
import io.github.dzkchen.dhen.features.visual.VanillaHudLayer
import io.github.dzkchen.dhen.font.FontRuntime
import io.github.dzkchen.dhen.gui.ArcPreviewScreen
import io.github.dzkchen.dhen.gui.ClickGuiShellScreen
import io.github.dzkchen.dhen.gui.ClickGuiState
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenFont
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.sound.SoundManagerScreen
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.privacy.LocalUrls
import io.github.dzkchen.dhen.privacy.LanguageKeys
import io.github.dzkchen.dhen.privacy.JarIntegrity
import io.github.dzkchen.dhen.privacy.TranslationProtection
import io.github.dzkchen.dhen.privacy.ModRegistry
import io.github.dzkchen.dhen.privacy.PrivacyLog
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.module.ModuleNotifier
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.WorldRenderTypes
import io.github.dzkchen.dhen.sound.SoundManager
import io.github.dzkchen.dhen.theme.ThemeRuntime
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.ui.hud.HudEditorScreen
import io.github.dzkchen.dhen.ui.hud.HudRuntime
import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.TickClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents
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
import java.util.concurrent.Executor
import kotlin.coroutines.EmptyCoroutineContext

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	private val clientThread = ClientThreadDispatcher()
	private val clientExecutor = Executor { Minecraft.getInstance().execute(it) }
	private val announcements = AnnouncementBuffer(ANNOUNCEMENT_CAPACITY)
	internal val firstRunExperience = FirstRunExperience(::persistCore, ::announceComponent)
	internal val automationNotice = AutomationNotice(::persistCore, ::announce)

	val modules: ModuleManager = ModuleManager(
		notifier = ModuleNotifier.chatBacked(clientExecutor, ::announceComponent),
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
			TabCompleteHooks,
			HypixelLocationHooks,
			ScoreboardHooks,
			TablistHooks,
			TabWidgetHooks,
			PartyHooks,
			PickupHooks,
			PlayerStatsHooks,
			PetHooks,
			QuiverHooks,
			MaxwellHooks,
			CookieHooks,
			ProfileHooks,
			firstRunExperience,
			HypixelModApi
		)
	}

	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val stores = mutableListOf<ConfigStore>()
	private val failsafe = Failsafe()
	private val textMeasurements = Failsafe("Dhen {} failed, its text measurements are frozen until restart")
	private lateinit var coreStore: ConfigStore
	private lateinit var moduleStore: ConfigStore
	private lateinit var clickGuiView: ClickGuiState

	override fun onInitializeClient() {
		failsafe.guard("initialization", ::initialize)
		if (failsafe.failed) latchOff()
	}

	private fun initialize() {
		val configRoot = FabricLoader.getInstance().configDir.resolve(MOD_ID)
		ioScope.launch { ModRegistry.primeShaderOwners() }
		coreStore = flushedOnStop(configRoot.resolve("core.json"), CorePersistence.migrations, CorePersistence.authoritative)
		moduleStore = flushedOnStop(configRoot.resolve("modules.json"), ModulePersistence.migrations, ModulePersistence.authoritative)
		SoundManager.install(
			flushedOnStop(configRoot.resolve("sounds.json"), SoundManager.migrations, SoundManager.authoritative)
		)
		val coreState = CorePersistence.apply(coreStore.load(), hudRuntime.coreElements)
		clickGuiView = coreState.clickGui
		val loader = FabricLoader.getInstance()
		val dhenContainer = loader.getModContainer(MOD_ID).orElse(null)
		JarIntegrity.install(
			ioScope,
			dhenContainer?.origin?.paths?.firstOrNull(),
			loader.getModContainer("minecraft").orElse(null)?.metadata?.version?.friendlyString,
			dhenContainer?.metadata?.version?.friendlyString ?: "unknown",
			coreState.tamperWarningDismissed,
			::persistCore
		) { url -> Util.getPlatform().openUri(url) }
		val themes = ThemeRuntime(configRoot, ioScope, clientThread, ::persistCore, ::fontChanged, ::announce) {
			Util.getPlatform().openPath(it)
		}
		val fonts = FontRuntime(configRoot, ioScope, clientThread, ::persistCore, ::fontChanged, ::announce) {
			Util.getPlatform().openPath(it)
		}
		val commands = CommandRegistry<FabricClientCommandSource>(
			modules,
			persistCore = ::persistCore,
			themes = themes,
			chatHider = ChatTweaks,
			commandAliases = ChatMacros,
			utilities = Commands,
			hud = hudCommands,
			sounds = soundCommands,
			previews = previewCommands,
			waypoints = Waypoints,
			available = { !failsafe.failed },
			persistModules = ::persistModules
		) { source, line ->
			source.sendFeedback(line)
		}
		themes.reload()
		fonts.prime()
		ClientPrefs.openSoundManager.value = ::openSoundManager
		modules.registerAll(
			AutoSprint,
			ArrowFix,
			NoItemPlace,
			PickupLog,
			ChatTweaks,
			ChatMacros,
			Commands,
			PartyHelper,
			SkyBlockKick,
			Tweaks,
			ItemRarityOverlay,
			ItemAbilities,
			AnvilHelper,
			ChickenHeadTimer,
			CrownOfAvarice,
			FireVeilWand,
			FireFreeze,
			InventorySearch,
			ItemTooltip,
			ProtectItem,
			SlotBinding,
			WardrobeKeybinds,
			PetKeybinds,
			LoadoutKeybinds,
			AuctionPriceInput,
			StorageOverlay,
			Animations,
			Box3D,
			Camera,
			ClassColors,
			CustomScoreboard,
			DamageSplash,
			EntityHighlight,
			HidePlayers,
			MaskTimers,
			MobHighlight,
			NametagTweaks,
			MoveableVanillaHud,
			PetDisplay,
			PlayerStatsHud,
			QuiverDisplay,
			RenderOptimizer,
			RevertAxes,
			TabWidgetDisplay,
			TimeChanger,
			UtilityHuds,
			VisualTweaks,
			Waypoints,
			SpoofAsVanilla,
			ChannelSpoofing,
			ModWhitelist,
			ServerPackBypass
		)
		modules.enable(ChatTweaks)
		ModulePersistence.apply(modules, moduleStore.load())
		modules.stateListener = { Minecraft.getInstance().execute(::persistModules) }
		HudElementRegistry.replaceElement(
			VanillaHudElements.HOTBAR,
			MoveableVanillaHud.replacement(VanillaHudLayer.HOTBAR, failsafe)
		)
		HudElementRegistry.replaceElement(
			VanillaHudElements.INFO_BAR,
			MoveableVanillaHud.replacement(VanillaHudLayer.EXPERIENCE_BAR, failsafe)
		)
		HudElementRegistry.replaceElement(
			VanillaHudElements.EXPERIENCE_LEVEL,
			MoveableVanillaHud.replacement(VanillaHudLayer.EXPERIENCE_LEVEL, failsafe)
		)
		HudElementRegistry.replaceElement(
			VanillaHudElements.HELD_ITEM_TOOLTIP,
			MoveableVanillaHud.replacement(VanillaHudLayer.HELD_ITEM, failsafe)
		)
		HudElementRegistry.replaceElement(
			VanillaHudElements.OVERLAY_MESSAGE,
			MoveableVanillaHud.replacement(VanillaHudLayer.ACTION_BAR, failsafe)
		)
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			failsafe.guard("command registration") { commands.install(dispatcher) }
		}
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, id("hud")) { graphics, _ ->
			failsafe.guard("HUD render") {
				val font = Minecraft.getInstance().font
				hudRuntime.render(graphics, font)
				Notifications.renderBehindNoScreen(graphics, font)
			}
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
		ClientConfigurationConnectionEvents.DISCONNECT.register { _, _ -> worldChanged(WorldChange.DISCONNECT) }
		ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
			failsafe.guard("entity unload") { WorldHooks.entityUnloaded(entity) }
		}
		ClientLifecycleEvents.CLIENT_STARTED.register {
			failsafe.guard("mod registry") { ModRegistry.prime() }
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
		EntityHighlights.install(modules.eventBus)
		TickHooks.install(modules.eventBus)
		TabCompleteHooks.install(modules.eventBus)
		HypixelLocationHooks.install(modules.eventBus)
		ScoreboardHooks.install(modules.eventBus)
		TablistHooks.install(modules.eventBus)
		TabWidgetHooks.install(modules.eventBus)
		PartyHooks.install(modules.eventBus)
		PickupHooks.install(modules.eventBus)
		PlayerStatsHooks.install(modules.eventBus)
		PetHooks.install(modules.eventBus)
		QuiverHooks.install(modules.eventBus)
		MaxwellHooks.install(modules.eventBus)
		CookieHooks.install(modules.eventBus)
		ProfileHooks.install(
			modules.eventBus,
			flushedOnStop(configRoot.resolve("profiles.json"), emptyList(), ProfileHooks.authoritative)
		)
		ContainerState.install(
			flushedOnStop(configRoot.resolve("containers.json"), emptyList(), ContainerState.authoritative)
		)
		StorageSnapshots.install(
			flushedOnStop(configRoot.resolve("storage.json"), emptyList(), StorageSnapshots.authoritative),
			ioScope
		)
		WorldRenderTypes.initialize()
		firstRunExperience.install(modules.eventBus, coreState.welcomeShown)
		automationNotice.install(coreState.hypixelNoticeShown)
		ItemRepo.install(ioScope, configRoot.resolve("repo"))
		PackModelRepo.install(ioScope, configRoot.resolve("packmodels"))
		Prices.install(ioScope, modules.eventBus, clientThread)
		MayorService.install(ioScope, modules.eventBus, clientThread)
		PlayerProfiles.install(ioScope, clientThread, baseUrl = { ClientPrefs.profileProxy.value })
		HypixelModApi.install(onHello = automationNotice::hypixelConnected)
		WorldRenderProbe.install(modules.eventBus)
		Notifications.install(modules.eventBus)
		PrivacyLog.install(modules.eventBus, clientExecutor, ::announceComponent)
		LocalUrls.install(modules.eventBus, clientThread)
		LanguageKeys.install(modules.eventBus)
		TranslationProtection.install(modules.eventBus, ::persistCore)
		LOGGER.info("Dhen initialized")
	}

	private var latched = false

	internal fun latchOff() {
		if (latched) return
		latched = true
		DhenAlert.clear()
		contained("translation protection", TranslationProtection::uninstall)
		contained("language keys", LanguageKeys::uninstall)
		contained("local url guard", LocalUrls::uninstall)
		contained("privacy log", PrivacyLog::uninstall)
		contained("notifications", Notifications::uninstall)
		if (DhenFont.latchOff()) contained("font caches", ::fontChanged)
		contained("client thread", clientThread::shutdown)
		contained("tick clock", TickClock::cancelWaits)
		contained("hook registry") { hooks.forEach { contained(it.feed, it::uninstall) } }
		contained("world render probe", WorldRenderProbe::uninstall)
		contained("item repository", ItemRepo::uninstall)
		contained("pack model table", PackModelRepo::uninstall)
		contained("price feed", Prices::uninstall)
		contained("mayor feed", MayorService::uninstall)
		contained("player profiles", PlayerProfiles::uninstall)
		contained("sound manager", SoundManager::uninstall)
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
			if (phase == WorldChange.DISCONNECT) SoundManager.forgetPlayback()
			if (phase == WorldChange.JOIN) flushAnnouncements(client)
			WorldHooks.worldChanged(phase)
		}
		else client.execute { worldChanged(phase) }
	}

	private fun tick(client: Minecraft, openGuiKey: KeyMapping) {
		TickHooks.clientTicked(client.level != null)
		clientThread.drainQueue()
		JarIntegrity.tick(client)
		Notifications.tick()
		ContainerHooks.tick()
		val options = client.options
		if (DhenType.fontOptionsChanged(options.forceUnicodeFont().get(), options.japaneseGlyphVariants().get())) {
			textMeasurements.guard("font option change") { invalidateTextMeasurements() }
		}
		if (openGuiKey.consumeClick() && client.level != null) clickGuiScreen()?.let(client.gui::setScreen)
	}

	private val hudCommands = object : HudCommands {
		override fun openEditor(): String {
			openHudEditor()
			return "Opening the HUD editor."
		}

		override fun resetLayout(): String = HudCommands.resetSummary(resetHudLayout())
	}

	private val soundCommands = object : SoundCommands {
		override fun openManager(): String {
			openSoundManager()
			return "Opening the Sound Manager."
		}

		override fun setVolume(sound: Identifier, percent: Int): String =
			"Set $sound to ${SoundManager.setVolumePercent(sound, percent)}% volume."
	}

	private val previewCommands = object : PreviewCommands {
		override fun toggleWorldRender(): String =
			"World-render probe ${if (WorldRenderProbe.toggle()) "on" else "off"}."

		override fun toggleHighlight(): String {
			val on = EntityHighlights.toggleDebugRule(EntityHighlight::boxStyle, EntityHighlight::debugColor)
			return "Zombie highlight ${if (on) "on" else "off"}."
		}

		override fun openArcPreview(): String {
			Dhen.openArcPreview()
			return "Opening the annular-segment preview."
		}

		override fun showAlert(): String {
			DhenAlert.show("Dhen Alert", "Title and subtitle preview")
			return "Showing the Dhen alert preview."
		}

		override fun showNotice(): String {
			previewPrivacyNotice()
			return "Raising a privacy alert preview."
		}
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

	private fun openArcPreview() = clientThread.dispatch(EmptyCoroutineContext) {
		val client = Minecraft.getInstance()
		client.gui.setScreen(ArcPreviewScreen(client.gui.screen()))
	}

	private fun previewPrivacyNotice() {
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, PRIVACY_NOTICE_TITLE)
		PrivacyLog.toast(PrivacyLog.Alert.DANGER, PRIVACY_NOTICE_TITLE, PRIVACY_NOTICE_DETAIL)
		PrivacyLog.logDetection(PRIVACY_NOTICE_CATEGORY, PRIVACY_NOTICE_DETAIL)
		if (PrivacyLog.debugging) PrivacyLog.detail(PRIVACY_NOTICE_DETAIL)
	}

	internal fun announce(message: String) {
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
		Notifications.invalidateMeasurements()
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

	private fun flushedOnStop(
		path: Path,
		migrations: List<(JsonObject) -> Unit>,
		authoritative: Set<String> = emptySet()
	): ConfigStore = ConfigStore(path, ioScope, migrations, authoritative).also { stores += it }

	private fun persistModules() {
		moduleStore.save(ModulePersistence.snapshot(modules))
	}

	private fun persistHudLayouts() {
		persistModules()
		persistCore()
	}

	private fun persistCore() {
		coreStore.save(
			CorePersistence.snapshot(
				clickGuiView,
				firstRunExperience.shown,
				automationNotice.hypixelNoticeShown,
				JarIntegrity.warningDismissed,
				hudRuntime.coreElements
			)
		)
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

	private fun openSoundManager() {
		val client = Minecraft.getInstance()
		val parent = client.gui.screen() as? ClickGuiShellScreen ?: clickGuiScreen() ?: return
		client.gui.setScreen(SoundManagerScreen(parent))
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)

	private const val ANNOUNCEMENT_CAPACITY = 32
	private const val PRIVACY_NOTICE_TITLE = "Privacy alert preview"
	private const val PRIVACY_NOTICE_CATEGORY = "Preview"
	private const val PRIVACY_NOTICE_DETAIL = "This is what a blocked probe looks like."
}
