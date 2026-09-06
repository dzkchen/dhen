package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.repo.PackModelRepo
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.privacy.PackControls
import io.github.dzkchen.dhen.privacy.PackOverrides
import io.github.dzkchen.dhen.privacy.PrivacyLog
import io.github.dzkchen.dhen.privacy.ServerPackCache
import io.github.dzkchen.dhen.privacy.ServerPacks
import io.github.dzkchen.dhen.privacy.ShaderStripTracker
import io.github.dzkchen.dhen.privacy.TrackPackDetector
import io.github.dzkchen.dhen.privacy.Whitelist
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

object ServerPackBypass : Module(
	name = "Server Pack Bypass",
	category = Category.PRIVACY,
	description = "Decides how much of a server's resource pack is allowed to reach your game."
) {
	private const val ENTRY_SEPARATOR = " "
	private const val TABLE_RETRY_TICKS = 6000
	private val MOUSE_BUTTONS = GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST
	private const val SHADERS_UNCHANGED = "Leave alone"
	private const val SHADERS_PACK = "Server pack"
	private const val SHADERS_VANILLA = "Vanilla"
	private val modeSetting = SelectorSetting(
		"Mode",
		ServerPacks.MANUAL,
		ServerPacks.CHOOSABLE_MODES,
		listed = true,
		description = "Manual keeps Minecraft's prompt and the full pack. Ask starts stripped and lets you load it fully once per session. " +
			"Always On silently accepts the pack but keeps only its language files."
	)
	private var chosenMode by modeSetting

	private var reverting by BooleanSetting(
		"Vanilla Item Models",
		description = "Draws SkyBlock items with the vanilla models they replaced. Manual, and Ask once you have loaded the pack, " +
			"are the only modes that keep the pack's models, so there is nothing to undo in the others."
	)

	private val reverts: () -> Boolean = { enabled && reverting }

	private val whitelistCode by KeybindSetting(
		"Whitelist Item",
		GLFW.GLFW_KEY_P,
		"Hover an item in any menu and press this to keep the pack's model for that one item."
	).withDependency(reverts)

	private var replacementText by StringSetting(
		"Model Replacements",
		description = "Draws a SkyBlock item as a vanilla one instead. Space-separated pairs, for example HYPERION=diamond_sword."
	).withDependency(reverts)

	private var glintText by StringSetting(
		"Glint Overrides",
		description = "Forces the enchantment shimmer on or off per item. Space-separated pairs, for example HYPERION=off."
	).withDependency(reverts)

	private val chosenShaders by SelectorSetting(
		"Text Shaders",
		SHADERS_UNCHANGED,
		listOf(SHADERS_UNCHANGED, SHADERS_PACK, SHADERS_VANILLA),
		description = "Picks whose text and colour shaders the game uses when a server pack ships its own. " +
			"The pack only has shaders to offer in Manual, and in Ask once you have loaded it; in the other modes " +
			"both choices give you vanilla's."
	)

	private var unpinning by BooleanSetting(
		"Unpin Resource Packs",
		description = "Lets you move a server's pack in the Resource Packs screen, and keeps an optional one unselected " +
			"once you drag it out instead of the game putting it straight back."
	)

	private var sinkingHypixelPack by BooleanSetting(
		"Load Hypixel Pack First",
		description = "Puts Hypixel's own pack at the bottom of the list, so your own packs draw over the top of it."
	)

	private var serverPacksFirst by BooleanSetting(
		"Load Server Packs First",
		description = "Loads every server-sent pack before your own, so your own packs win wherever the two overlap."
	)

	private var skippingMismatchScreen by BooleanSetting(
		"Skip Pack Version Warning",
		description = "Selects a pack built for another game version straight away, instead of asking you to confirm."
	)

	private var ignoringCompatibility by BooleanSetting(
		"Ignore Pack Version Entirely",
		description = "Treats every pack as built for this version, so nothing is marked out of date anywhere."
	)

	private var storedWhitelist by StringSetting("Model Whitelist").hide()

	private var readWhitelist = ""
	private var readReplacements = ""
	private var readGlints = ""
	private var appliedShaders: String? = null
	private var tableWait = 0
	private var pressing = false

	init {
		on<GuiOpenEvent> { if (it.screen is ConnectScreen) TrackPackDetector.reset() }
		on<ClientTickEvent.End> {
			publish()
			val client = Minecraft.getInstance()
			ShaderStripTracker.flushPending(client.player != null)
			ServerPackConsentScreen.tryShow(client)
			republish(client)
		}
		on<WorldChangeEvent> {
			when (it.phase) {
				WorldChange.JOIN -> tableWait = 0
				WorldChange.DISCONNECT -> {
					PackControls.forgetSelections()
					ServerPacks.forgetAll()
				}
				else -> Unit
			}
		}
		on<KeyInputEvent> { pressing = it.action == InputAction.PRESS }
		on<ContainerKeyEvent> { event ->
			if (!pressing || whitelistCode == GLFW.GLFW_KEY_UNKNOWN || event.input.key() != whitelistCode) return@on
			if (whitelisted(event.hoveredSlot)) event.cancelled = true
		}
		on<ContainerClickEvent> { event ->
			if (whitelistCode !in MOUSE_BUTTONS || event.click.button() != whitelistCode) return@on
			if (whitelisted(event.hoveredSlot)) event.cancelled = true
		}
	}

	override fun onEnabled() {
		publish()
		ServerPackCache.launcher = ::launch
	}

	override fun onDisabled() {
		publish()
		applyShaders(Minecraft.getInstance())
		tableWait = 0
		PackOverrides.forgetProfiles()
		PackControls.forgetSelections()
		ServerPacks.forgetAll()
		TrackPackDetector.reset()
	}

	override fun onReset() {
		publish()
	}

	private fun publish() {
		ServerPacks.mode = ServerPacks.effectiveMode(enabled, chosenMode)
		PackOverrides.reverting = reverts()
		PackControls.unpinning = enabled && unpinning
		PackControls.sinkingHypixelPack = enabled && sinkingHypixelPack
		PackControls.serverPacksFirst = enabled && serverPacksFirst
		PackControls.skippingMismatchScreen = enabled && skippingMismatchScreen
		PackControls.ignoringCompatibility = enabled && ignoringCompatibility
	}

	private fun republish(client: Minecraft) {
		val wanted = reverts()
		PackOverrides.reverting = wanted
		if (wanted) {
			if (tableWait == 0) PackModelRepo.require()
			tableWait = (tableWait + 1) % TABLE_RETRY_TICKS
		}
		if (storedWhitelist != readWhitelist) {
			readWhitelist = storedWhitelist
			PackOverrides.whitelist = Whitelist.decode(storedWhitelist)
		}
		if (replacementText != readReplacements) {
			readReplacements = replacementText
			PackOverrides.replacements = PackOverrides.readReplacements(replacementText)
			reportIgnored(replacementText, PackOverrides.replacements.size, "model replacement")
		}
		if (glintText != readGlints) {
			readGlints = glintText
			PackOverrides.glints = PackOverrides.readGlints(glintText)
			reportIgnored(glintText, PackOverrides.glints.size, "glint override")
		}
		applyShaders(client)
	}

	private fun reportIgnored(raw: String, kept: Int, label: String) {
		val ignored = raw.split(ENTRY_SEPARATOR).count(String::isNotEmpty) - kept
		if (ignored > 0) PrivacyLog.detail("Dhen ignored $ignored unreadable $label entry/entries")
	}

	private fun applyShaders(client: Minecraft) {
		val choice = if (enabled) chosenShaders else SHADERS_UNCHANGED
		if (choice == appliedShaders) return
		val settled = appliedShaders != null
		appliedShaders = choice
		PackOverrides.packTextShaders = when (choice) {
			SHADERS_PACK -> true
			SHADERS_VANILLA -> false
			else -> null
		}
		if (settled && client.player != null) client.reloadResourcePacks()
	}

	private fun whitelisted(hovered: Slot?): Boolean {
		if (!reverts()) return false
		val stack = hovered?.takeIf { it.hasItem() }?.item ?: return false
		toggleWhitelist(PackOverrides.identity(stack) ?: return false)
		return true
	}

	private fun toggleWhitelist(id: String) {
		val kept = Whitelist.decode(storedWhitelist).toMutableSet()
		val added = kept.add(id)
		if (!added) kept.remove(id)
		storedWhitelist = Whitelist.encode(kept)
		persist()
		PrivacyLog.detail(if (added) "Keeping the pack's model for $id" else "Reverting $id to its vanilla model")
	}
}
