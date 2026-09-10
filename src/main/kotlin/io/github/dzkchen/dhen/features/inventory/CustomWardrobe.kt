package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.dzkchen.dhen.config.*
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.*
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object CustomWardrobe : Module(
	name = "Custom Wardrobe",
	category = Category.INVENTORY,
	description = "Shows every armor set together, with favorites, previews and the wardrobe's existing slot bindings."
) {
	internal val followMouse = BooleanSetting("Follow Mouse", true)
	internal val hideEmpty = BooleanSetting("Hide Empty")
	internal val hideLocked = BooleanSetting("Hide Locked")
	internal val estimatedValue = BooleanSetting("Estimated Value", true)
	internal val loadingText = BooleanSetting("Loading Text", true)
	internal val tooltipRequired = BooleanSetting("Tooltip Key Required")
	internal val tooltipKey = MenuKeybinds.menuKey("Tooltip Key", GLFW.GLFW_KEY_LEFT_SHIFT, "Hold to see armor lore.").withDependency { tooltipRequired.on }
	internal val numberKeys = BooleanSetting("Use Number Keys in Custom Wardrobe", true)
	internal val onlyFavorites = BooleanSetting("Favorites Only").hide()
	private var stored by StringSetting("Wardrobe Cache").hide()
	internal val background = ColorSetting("Background", Color(DhenPalette.WARDROBE_BACKGROUND), true)
	internal val equippedColor = ColorSetting("Equipped", Color(DhenPalette.WARDROBE_EQUIPPED), true)
	internal val favoriteColor = ColorSetting("Favorite", Color(DhenPalette.WARDROBE_FAVORITE), true)
	internal val samePage = ColorSetting("Same Page", Color(DhenPalette.WARDROBE_SAME_PAGE), true)
	internal val otherPage = ColorSetting("Other Page", Color(DhenPalette.WARDROBE_BACKGROUND), true)
	internal val outlineTop = ColorSetting("Top Outline", Color(DhenPalette.WARDROBE_OUTLINE_TOP), true)
	internal val outlineBottom = ColorSetting("Bottom Outline", Color(DhenPalette.WARDROBE_OUTLINE_BOTTOM), true)
	internal val globalScale = NumberSetting("Global Scale", 100.0, 30.0, 200.0)
	internal val outlineThickness = NumberSetting("Outline Thickness", 5.0, 1.0, 15.0)
	internal val outlineBlur = NumberSetting("Outline Blur", 0.5, 0.0, 1.0, 0.1)
	internal val slotWidth = NumberSetting("Slot Width", 75.0, 30.0, 100.0)
	internal val slotHeight = NumberSetting("Slot Height", 140.0, 60.0, 200.0)
	internal val playerScale = NumberSetting("Player Scale", 75.0, 0.0, 100.0)
	internal val perRow = NumberSetting("Slots Per Row", 9.0, 5.0, 18.0)
	internal val horizontal = NumberSetting("Slot Horizontal Spacing", 3.0, 1.0, 20.0)
	internal val vertical = NumberSetting("Slot Vertical Spacing", 3.0, 1.0, 20.0)
	internal val slotsToButtons = NumberSetting("Slot-to-Button Spacing", 10.0, 1.0, 40.0)
	internal val buttonHorizontal = NumberSetting("Button Horizontal Spacing", 10.0, 1.0, 40.0)
	internal val buttonVertical = NumberSetting("Button Vertical Spacing", 10.0, 1.0, 40.0)
	internal val buttonWidth = NumberSetting("Button Width", 50.0, 1.0, 60.0)
	internal val buttonHeight = NumberSetting("Button Height", 20.0, 1.0, 60.0)
	internal val padding = NumberSetting("Background Padding", 10.0, 1.0, 20.0)
	private val colors = arrayOf(background, equippedColor, favoriteColor, samePage, otherPage, outlineTop, outlineBottom)
	private val spacing = arrayOf(globalScale, outlineThickness, outlineBlur, slotWidth, slotHeight, playerScale, perRow,
		horizontal, vertical, slotsToButtons, buttonHorizontal, buttonVertical, buttonWidth, buttonHeight, padding)
	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	internal var model = WardrobeModel()
		private set
	internal var revision = 0
		private set
	private var profile: String? = null
	private var generation = 0
	private var saving = false
	private var saveAgain = false
	internal var editing: AbstractContainerScreen<*>? = null
		private set
	internal var swapping = false
		private set
	private var loading = false
	private val titlePattern = Regex("\\((\\d+)/(\\d+)\\) Armor Sets")

	init {
		for (setting in arrayOf(followMouse, hideEmpty, hideLocked, estimatedValue, loadingText, tooltipRequired, tooltipKey, numberKeys, onlyFavorites)) registerSetting(setting)
		for (setting in colors) registerSetting(setting)
		for (setting in spacing) registerSetting(setting)
		registerSetting(ActionSetting("Reset Colours", {
			for (setting in colors) setting.reset()
			persist()
		}))
		registerSetting(ActionSetting("Reset Spacing", {
			for (setting in spacing) setting.reset()
			persist()
		}))
		on<GuiOpenEvent> { event ->
			if (!swapping && SkyBlockLocation.inSkyBlock) {
				val screen = event.screen as? AbstractContainerScreen<*>
				val match = screen?.let { titlePattern.find(withoutCodes(it.title.string)) }
				if (screen != null && match != null) {
					followProfile()
					model.waiting = true
					model.waitingPage = match.groupValues[1].toInt() - 1
					model.window = -1
					event.screen = WardrobeScreen(screen)
				}
			}
		}
		on<ContainerReadyEvent> { read(it.title, it.windowId, it.stacks) }
		on<ContainerUpdatedEvent> { read(it.title, it.windowId, it.stacks) }
		on<ClientTickEvent.End> {
			priceHold.ensure()
			repoHold.ensure()
			followProfile()
			if (editing != null && Minecraft.getInstance().gui.screen() !== editing) editing = null
		}
		on<ScreenRenderEvent.Post> { editButton(it) }
		on<ContainerClickEvent>(AFTER_PRODUCERS) { event ->
			if (event.screen === editing && event.click.x() in 4.0..100.0 && event.click.y() in 4.0..24.0) {
				event.cancelled = true
				swap(event.screen, false)
			}
		}
		on<WorldChangeEvent> { forget() }
	}

	override fun onDisabled() {
		val screen = Minecraft.getInstance().gui.screen() as? WardrobeScreen
		if (screen != null) swap(screen.container, true)
		priceHold.release()
		repoHold.release()
		forget()
	}

	private fun forget() {
		generation++
		model = WardrobeModel()
		profile = null
		editing = null
		saving = false
		saveAgain = false
		loading = false
		revision++
	}

	private fun read(title: Component, window: Int, stacks: List<ItemStack>) {
		if (!SkyBlockLocation.inSkyBlock) return
		val match = titlePattern.find(withoutCodes(title.string)) ?: return
		followProfile()
		if (model.read(match.groupValues[1].toInt() - 1, match.groupValues[2].toInt(), window, stacks)) {
			revision++
			save()
		}
	}

	internal fun favorite(index: Int) {
		if (loading) return
		model.favorites[index] = !model.favorites[index]
		revision++
		save()
	}

	internal fun filter() {
		onlyFavorites.on = !onlyFavorites.on
		revision++
		persist()
	}

	internal fun swap(container: AbstractContainerScreen<*>, edit: Boolean) {
		swapping = true
		try {
			val client = Minecraft.getInstance()
			val shown = if (edit && enabled && container.menu is net.minecraft.world.inventory.ChestMenu) {
				WardrobeEditScreen(container.menu as net.minecraft.world.inventory.ChestMenu, client.player!!.inventory, container.title)
			} else if (edit) container else WardrobeScreen(container)
			editing = if (edit && enabled) shown as AbstractContainerScreen<*> else null
			client.gui.setScreen(shown)
		} finally {
			swapping = false
		}
	}

	private val editMemo = DhenType.memo()

	private fun editButton(event: ScreenRenderEvent.Post) {
		if (event.screen !== editing) return
		io.github.dzkchen.dhen.gui.RoundedGui.pill(event.graphics, 4, 4, 100, 24, DhenPalette.SURFACE_RAISED)
		editMemo.text(event.graphics, Minecraft.getInstance().font, "Custom Wardrobe", 9, 9, DhenPalette.TEXT_PRIMARY)
	}

	private fun followProfile() {
		val key = ProfileHooks.profile ?: return
		if (key == profile) return
		profile = key
		saving = false
		saveAgain = false
		model = WardrobeModel()
		generation++
		val expected = generation
		val target = model
		val source = stored
		val registry = Minecraft.getInstance().connection?.registryAccess() ?: return
		loading = true
		launch {
			val loaded = withContext(Dispatchers.IO) {
				runCatching {
					val root = JsonParser.parseString(source).asJsonObject.getAsJsonObject(key) ?: return@runCatching null
					val ops = registry.createSerializationContext(JsonOps.INSTANCE)
					val stacks = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, root.get("items")).result().orElse(null)
					root to stacks
				}.getOrNull()
			}
			if (expected != generation) return@launch
			loading = false
			if (loaded == null) {
				if (saveAgain) {
					saveAgain = false
					save()
				}
				return@launch
			}
			val (root, stacks) = loaded
			for (index in 0 until WARDROBE_SETS) {
				target.favorites[index] = root.getAsJsonArray("favorites")?.any { it.asInt == index } == true
				if (target.known[index]) continue
				target.locked[index] = root.getAsJsonArray("locked")?.get(index)?.asBoolean ?: true
				for (part in 0 until 4) target.pieces[index][part] = stacks?.getOrNull(index * 4 + part) ?: ItemStack.EMPTY
			}
			revision++
			if (saveAgain) {
				saveAgain = false
				save()
			}
		}
	}

	private fun save() {
		if (saving || loading) {
			saveAgain = true
			return
		}
		val key = profile ?: return
		val registry = Minecraft.getInstance().connection?.registryAccess() ?: return
		val items = model.pieces.flatMap { pieces -> pieces.map(ItemStack::copy) }
		val locked = model.locked.copyOf()
		val favorites = model.favorites.copyOf()
		val source = stored
		val expected = generation
		saving = true
		launch {
			val encoded = withContext(Dispatchers.IO) {
				val root = runCatching { JsonParser.parseString(source).asJsonObject }.getOrElse { JsonObject() }
				val data = JsonObject()
				data.add("items", ItemStack.OPTIONAL_CODEC.listOf().encodeStart(registry.createSerializationContext(JsonOps.INSTANCE), items).result().orElse(null))
				data.add("locked", com.google.gson.JsonArray().also { for (value in locked) it.add(value) })
				data.add("favorites", com.google.gson.JsonArray().also { for (index in favorites.indices) if (favorites[index]) it.add(index) })
				root.add(key, data)
				root.toString()
			}
			if (expected != generation) return@launch
			stored = encoded
			saving = false
			persist()
			if (saveAgain) {
				saveAgain = false
				save()
			}
		}
	}
}
