package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.data.pet.PetLines
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.EntityNameTagEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.input.shiftHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

@Suppress("unused")
object PetDisplay : Module(
	name = "Pet Display",
	category = Category.VISUAL,
	description = "Announces autopet swaps with a title and decorates the pets you own in menus and in the world."
) {
	internal val autoPetTitleSetting = BooleanSetting(
		"Auto Pet Title",
		description = "Shows a title when an autopet rule swaps your pet."
	)
	private var autoPetTitle by autoPetTitleSetting

	internal val dungeonsOnlySetting = BooleanSetting(
		"Dungeons Only",
		description = "Only shows the autopet title inside a dungeon."
	).withDependency { autoPetTitleSetting.on }
	private var dungeonsOnly by dungeonsOnlySetting

	internal val hideAutopetMessagesSetting = BooleanSetting(
		"Hide Autopet Messages",
		description = "Removes the autopet chat line."
	)
	private var hideAutopetMessages by hideAutopetMessagesSetting

	internal val highlightSetting = BooleanSetting(
		"Highlight Active Pet",
		description = "Tints the slot of your summoned pet in the Pets menu."
	)
	private var highlight by highlightSetting

	internal val highlightColorSetting = ColorSetting(
		"Highlight Color",
		Color.rgba(0, 255, 255),
		allowAlpha = true,
		description = "The colour the summoned pet's slot is tinted."
	).withDependency { highlightSetting.on }
	private var highlightColor by highlightColorSetting

	internal val hidePetLevelSetting = BooleanSetting(
		"Hide Pet Level",
		description = "Hides the level in front of a summoned pet's name above its head."
	)
	private var hidePetLevel by hidePetLevelSetting

	internal val hideMaxPetLevelSetting = BooleanSetting(
		"Hide Max Pet Level",
		description = "Hides that level only once the pet has reached level 100, or 200 for a dragon."
	)
	private var hideMaxPetLevel by hideMaxPetLevelSetting

	internal val petCandySetting = BooleanSetting(
		"Pet Candy Used",
		default = true,
		description = "Shows how many Pet Candies a pet has eaten on its slot."
	)
	private var petCandy by petCandySetting

	internal val hideOnMaxedSetting = BooleanSetting(
		"Hide On Maxed",
		description = "Leaves the candy count off pets that are already max level."
	).withDependency { petCandySetting.on }
	private var hideOnMaxed by hideOnMaxedSetting

	internal val expShareSetting = BooleanSetting(
		"Show Exp Share",
		default = true,
		description = "Marks pets holding an Exp Share with a flower."
	)
	private var expShare by expShareSetting

	internal val tierBoostSetting = BooleanSetting(
		"Show Tier Boost",
		default = true,
		description = "Marks pets holding a Tier Boost with a dot."
	)
	private var tierBoost by tierBoostSetting

	internal val petItemScaleSetting = NumberSetting(
		"Pet Item Scale",
		default = 0.9,
		min = 0.7,
		max = 1.5,
		step = 0.05,
		description = "How large the held pet item marker is drawn."
	).withDependency { expShareSetting.on || tierBoostSetting.on }
	private var petItemScale by petItemScaleSetting

	internal val petExpTooltipSetting = BooleanSetting(
		"Pet Exp Tooltip",
		default = true,
		description = "Shows progress to a pet's maximum level in its tooltip."
	)
	private var petExpTooltipEnabled by petExpTooltipSetting

	internal val showPetExpAlwaysSetting = BooleanSetting(
		"Show Pet Exp Always",
		description = "Shows pet experience progress without holding Shift."
	).withDependency { petExpTooltipSetting.on }
	private var showPetExpAlways by showPetExpAlwaysSetting

	internal val dragonEggSetting = BooleanSetting(
		"Dragon Egg",
		default = true,
		description = "Shows Golden Dragon egg progress to level 100 before its level 200 curve."
	).withDependency { petExpTooltipSetting.on }
	private var dragonEgg by dragonEggSetting

	internal val georgeHelperSetting = BooleanSetting(
		"George Helper",
		default = true,
		description = "Lists the cheapest wanted pets in George's Offer Pets menu."
	)
	private var georgeHelperEnabled by georgeHelperSetting

	internal val fetchOtherTiersSetting = BooleanSetting(
		"Fetch Other Tiers",
		description = "Checks adjacent rarities without adding Kat upgrade costs."
	).withDependency { georgeHelperSetting.on }
	private var fetchOtherTiers by fetchOtherTiersSetting

	internal val wheelScaleSetting = NumberSetting(
		"Wheel Scale",
		default = 100.0,
		min = 70.0,
		max = 135.0,
		step = 5.0,
		description = "How large the pet wheel appears."
	)
	private var wheelScale by wheelScaleSetting

	internal val showKeyLabelsSetting = BooleanSetting(
		"Show Key Labels",
		default = true,
		description = "Shows the key that selects each visible pet."
	)
	private var showKeyLabels by showKeyLabelsSetting

	internal val favouritePetsOnlySetting = BooleanSetting(
		"Favourite Pets Only",
		description = "Only shows pets favourited in Hypixel's Pets Menu."
	)
	private var favouritePetsOnly by favouritePetsOnlySetting

	internal val segmentColorSetting = ColorSetting(
		"Segment Color",
		Color.rgba(15, 15, 15, 200),
		allowAlpha = true,
		description = "The base colour of each pet wheel segment."
	)
	private var segmentColor by segmentColorSetting

	internal val hoverColorSetting = ColorSetting(
		"Hover Color",
		Color.rgba(255, 255, 255, 30),
		allowAlpha = true,
		description = "The overlay colour on the selected segment."
	)
	private var hoverColor by hoverColorSetting

	internal val separatorColorSetting = ColorSetting(
		"Separator Color",
		Color.rgba(255, 255, 255, 40),
		allowAlpha = true,
		description = "The colour between neighbouring wheel segments."
	)
	private var separatorColor by separatorColorSetting

	internal val useHotbarBindsSetting = BooleanSetting(
		"Use Hotbar Binds",
		description = "Uses your current vanilla hotbar keys for wheel selection."
	)
	private var useHotbarBinds by useHotbarBindsSetting

	internal val petSlot1Setting = petSlotSetting(1)
	private var petSlot1 by petSlot1Setting
	internal val petSlot2Setting = petSlotSetting(2)
	private var petSlot2 by petSlot2Setting
	internal val petSlot3Setting = petSlotSetting(3)
	private var petSlot3 by petSlot3Setting
	internal val petSlot4Setting = petSlotSetting(4)
	private var petSlot4 by petSlot4Setting
	internal val petSlot5Setting = petSlotSetting(5)
	private var petSlot5 by petSlot5Setting
	internal val petSlot6Setting = petSlotSetting(6)
	private var petSlot6 by petSlot6Setting
	internal val petSlot7Setting = petSlotSetting(7)
	private var petSlot7 by petSlot7Setting
	internal val petSlot8Setting = petSlotSetting(8)
	private var petSlot8 by petSlot8Setting
	internal val petSlot9Setting = petSlotSetting(9)
	private var petSlot9 by petSlot9Setting

	internal val petSlotSettings = arrayOf(
		petSlot1Setting,
		petSlot2Setting,
		petSlot3Setting,
		petSlot4Setting,
		petSlot5Setting,
		petSlot6Setting,
		petSlot7Setting,
		petSlot8Setting,
		petSlot9Setting
	)

	internal val previewScaleSetting = NumberSetting("Preview Scale", 1.0, 0.5, 3.0, 0.05)
	private var previewScale by previewScaleSetting

	internal val hudIconSetting = BooleanSetting("Pet Icon", true)
	private var hudIcon by hudIconSetting
	internal val skinAnimationSetting = BooleanSetting("Skin Animation", true).withDependency { hudIconSetting.on }
	private var skinAnimation by skinAnimationSetting
	internal val skinAnimationSpeedSetting = NumberSetting("Skin Animation Speed", 1.0, 0.0, 2.0, 0.1)
		.withDependency { hudIconSetting.on && skinAnimationSetting.on }
	private var skinAnimationSpeed by skinAnimationSpeedSetting
	internal val hudIconScaleSetting = NumberSetting("Icon Scale", 2.5, 0.1, 3.0, 0.1)
		.withDependency { hudIconSetting.on }
	private var hudIconScale by hudIconScaleSetting
	internal val staticRotationXSetting = NumberSetting("Static Rotation X", 0.0, 0.0, 360.0, 5.0)
		.withDependency { hudIconSetting.on }
	private var staticRotationX by staticRotationXSetting
	internal val staticRotationYSetting = NumberSetting("Static Rotation Y", 0.0, 0.0, 360.0, 5.0)
		.withDependency { hudIconSetting.on }
	private var staticRotationY by staticRotationYSetting
	internal val staticRotationZSetting = NumberSetting("Static Rotation Z", 0.0, 0.0, 360.0, 5.0)
		.withDependency { hudIconSetting.on }
	private var staticRotationZ by staticRotationZSetting
	internal val spinRotationXSetting = NumberSetting("Rotation Speed X", 0.0, -725.0, 725.0, 25.0)
		.withDependency { hudIconSetting.on }
	private var spinRotationX by spinRotationXSetting
	internal val spinRotationYSetting = NumberSetting("Rotation Speed Y", 0.0, -725.0, 725.0, 25.0)
		.withDependency { hudIconSetting.on }
	private var spinRotationY by spinRotationYSetting
	internal val spinRotationZSetting = NumberSetting("Rotation Speed Z", 0.0, -725.0, 725.0, 1.0)
		.withDependency { hudIconSetting.on }
	private var spinRotationZ by spinRotationZSetting
	internal val resetStaticRotationSetting = ActionSetting("Reset Static Rotations", {
		staticRotationXSetting.reset()
		staticRotationYSetting.reset()
		staticRotationZSetting.reset()
	}).withDependency { hudIconSetting.on }
	private var resetStaticRotation by resetStaticRotationSetting
	internal val resetSpinRotationSetting = ActionSetting("Reset Rotation Speeds", {
		spinRotationXSetting.reset()
		spinRotationYSetting.reset()
		spinRotationZSetting.reset()
	}).withDependency { hudIconSetting.on }
	private var resetSpinRotation by resetSpinRotationSetting
	internal val resetIconSetting = ActionSetting("Reset Icon Settings", {
		hudIconSetting.reset()
		skinAnimationSetting.reset()
		skinAnimationSpeedSetting.reset()
		hudIconScaleSetting.reset()
		staticRotationXSetting.reset()
		staticRotationYSetting.reset()
		staticRotationZSetting.reset()
		spinRotationXSetting.reset()
		spinRotationYSetting.reset()
		spinRotationZSetting.reset()
	})
	private var resetIcon by resetIconSetting

	internal val backgroundSetting = BooleanSetting("Background Enabled", true)
	private var hudBackground by backgroundSetting
	internal val backgroundPaddingSetting = NumberSetting("Background Padding", 4.0, 0.0, 8.0, 0.25)
		.withDependency { backgroundSetting.on }
	private var backgroundPadding by backgroundPaddingSetting
	internal val commonColorSetting = ColorSetting("Common Color", Color.rgba(255, 255, 255))
		.withDependency { backgroundSetting.on }
	private var commonColor by commonColorSetting
	internal val uncommonColorSetting = ColorSetting("Uncommon Color", Color.rgba(85, 255, 85))
		.withDependency { backgroundSetting.on }
	private var uncommonColor by uncommonColorSetting
	internal val rareColorSetting = ColorSetting("Rare Color", Color.rgba(85, 85, 255))
		.withDependency { backgroundSetting.on }
	private var rareColor by rareColorSetting
	internal val epicColorSetting = ColorSetting("Epic Color", Color.rgba(170, 0, 170))
		.withDependency { backgroundSetting.on }
	private var epicColor by epicColorSetting
	internal val legendaryColorSetting = ColorSetting("Legendary Color", Color.rgba(255, 170, 0))
		.withDependency { backgroundSetting.on }
	private var legendaryColor by legendaryColorSetting
	internal val mythicColorSetting = ColorSetting("Mythic Color", Color.rgba(255, 85, 255))
		.withDependency { backgroundSetting.on }
	private var mythicColor by mythicColorSetting
	internal val xpRingSetting = BooleanSetting("XP Ring Enabled", true).withDependency { backgroundSetting.on }
	private var xpRing by xpRingSetting
	internal val ringPaddingSetting = NumberSetting("Ring Padding", 3.0, 2.0, 10.0, 0.5)
		.withDependency { backgroundSetting.on && xpRingSetting.on }
	private var ringPadding by ringPaddingSetting
	internal val filledRingColorSetting = ColorSetting("Filled Ring Color", Color.rgba(0, 255, 255))
		.withDependency { backgroundSetting.on && xpRingSetting.on }
	private var filledRingColor by filledRingColorSetting
	internal val unfilledRingColorSetting = ColorSetting("Unfilled Ring Color", Color.rgba(192, 192, 192))
		.withDependency { backgroundSetting.on && xpRingSetting.on }
	private var unfilledRingColor by unfilledRingColorSetting
	internal val separatorRingSetting = BooleanSetting("Separator Ring Enabled", true)
		.withDependency { backgroundSetting.on && xpRingSetting.on }
	private var separatorRing by separatorRingSetting
	internal val separatorRingPaddingSetting = NumberSetting("Separator Ring Padding", 3.0, 2.0, 10.0, 0.5)
		.withDependency { backgroundSetting.on && xpRingSetting.on && separatorRingSetting.on }
	private var separatorRingPadding by separatorRingPaddingSetting
	internal val separatorRingColorSetting = ColorSetting("Ring Color", Color.rgba(128, 128, 128))
		.withDependency { backgroundSetting.on && xpRingSetting.on && separatorRingSetting.on }
	private var separatorRingColor by separatorRingColorSetting
	internal val resetBackgroundSetting = ActionSetting("Reset Background Settings", {
		backgroundSetting.reset()
		backgroundPaddingSetting.reset()
		commonColorSetting.reset()
		uncommonColorSetting.reset()
		rareColorSetting.reset()
		epicColorSetting.reset()
		legendaryColorSetting.reset()
		mythicColorSetting.reset()
	})
	private var resetBackground by resetBackgroundSetting
	internal val resetRingsSetting = ActionSetting("Reset Ring Settings", {
		xpRingSetting.reset()
		ringPaddingSetting.reset()
		filledRingColorSetting.reset()
		unfilledRingColorSetting.reset()
		separatorRingSetting.reset()
		separatorRingPaddingSetting.reset()
		separatorRingColorSetting.reset()
	})
	private var resetRings by resetRingsSetting

	internal val hudPetItemSetting = BooleanSetting("HUD Pet Item Enabled")
	private var hudPetItem by hudPetItemSetting
	internal val hudPetItemPlacementSetting = SelectorSetting(
		"HUD Pet Item Placement",
		"Bottom Right",
		listOf("Top Left", "Top Center", "Top Right", "Center Left", "Center", "Center Right", "Bottom Left", "Bottom Center", "Bottom Right"),
		listed = true
	).withDependency { hudPetItemSetting.on }
	private var hudPetItemPlacement by hudPetItemPlacementSetting
	internal val hudPetItemScaleSetting = NumberSetting("HUD Pet Item Scale", 1.0, 0.1, 2.0, 0.1)
		.withDependency { hudPetItemSetting.on }
	private var hudPetItemScale by hudPetItemScaleSetting

	internal val expSharePetsSetting = BooleanSetting("Exp-Share Pets")
	private var expSharePets by expSharePetsSetting
	internal val expSharePlacementSetting = SelectorSetting(
		"Exp-Share Placement Location",
		"Right",
		listOf("Top", "Bottom", "Left", "Right", "Orbit"),
		listed = true
	).withDependency { expSharePetsSetting.on }
	private var expSharePlacement by expSharePlacementSetting
	internal val expShareOrientationSetting = SelectorSetting(
		"Exp-Share Group Orientation",
		"Vertical",
		listOf("Horizontal", "Vertical"),
		listed = true
	).withDependency { expSharePetsSetting.on && expSharePlacementSetting.value != "Orbit" }
	private var expShareOrientation by expShareOrientationSetting
	internal val orbitDistanceSetting = NumberSetting("Orbit Distance", 1.0, 1.0, 10.0, 1.0)
		.withDependency { expSharePetsSetting.on && expSharePlacementSetting.value == "Orbit" }
	private var orbitDistance by orbitDistanceSetting
	internal val orbitDirectionSetting = SelectorSetting("Orbit Direction", "Clockwise", listOf("Clockwise", "Counter-Clockwise"))
		.withDependency { expSharePetsSetting.on && expSharePlacementSetting.value == "Orbit" }
	private var orbitDirection by orbitDirectionSetting
	internal val orbitSpeedSetting = NumberSetting("Orbit Speed", 20.0, 10.0, 360.0, 10.0)
		.withDependency { expSharePetsSetting.on && expSharePlacementSetting.value == "Orbit" }
	private var orbitSpeed by orbitSpeedSetting
	internal val expShareIconSpacingSetting = NumberSetting("Exp-Share Icon Spacing", 1.0, 1.0, 5.0, 1.0)
		.withDependency { expSharePetsSetting.on && expSharePlacementSetting.value != "Orbit" }
	private var expShareIconSpacing by expShareIconSpacingSetting
	internal val hideDisabledSlotsSetting = BooleanSetting("Hide Disabled Slots")
		.withDependency { expSharePetsSetting.on }
	private var hideDisabledSlots by hideDisabledSlotsSetting
	internal val disabledOpacitySetting = NumberSetting("Disabled Opacity", 0.5, 0.1, 1.0, 0.05)
		.withDependency { expSharePetsSetting.on && !hideDisabledSlotsSetting.on }
	private var disabledOpacity by disabledOpacitySetting
	internal val resetOrganizationSetting = ActionSetting("Reset Exp-Share Organization", {
		expSharePlacementSetting.reset()
		expShareOrientationSetting.reset()
		orbitDistanceSetting.reset()
		orbitDirectionSetting.reset()
		orbitSpeedSetting.reset()
		expShareIconSpacingSetting.reset()
		hideDisabledSlotsSetting.reset()
		disabledOpacitySetting.reset()
	}).withDependency { expSharePetsSetting.on }
	private var resetOrganization by resetOrganizationSetting

	internal val expShareIconSetting = BooleanSetting("Exp-Share Pet Icon", true)
		.withDependency { expSharePetsSetting.on }
	private var expShareHudIcon by expShareIconSetting
	internal val expShareSkinAnimationSetting = BooleanSetting("Exp-Share Skin Animation", true)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareSkinAnimation by expShareSkinAnimationSetting
	internal val expShareSkinAnimationSpeedSetting = NumberSetting("Exp-Share Skin Animation Speed", 1.0, 0.0, 2.0, 0.1)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on && expShareSkinAnimationSetting.on }
	private var expShareSkinAnimationSpeed by expShareSkinAnimationSpeedSetting
	internal val expShareIconScaleSetting = NumberSetting("Exp-Share Icon Scale", 1.5, 0.1, 3.0, 0.1)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareIconScale by expShareIconScaleSetting
	internal val expShareStaticRotationXSetting = NumberSetting("Exp-Share Static Rotation X", 0.0, 0.0, 360.0, 5.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareStaticRotationX by expShareStaticRotationXSetting
	internal val expShareStaticRotationYSetting = NumberSetting("Exp-Share Static Rotation Y", 0.0, 0.0, 360.0, 5.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareStaticRotationY by expShareStaticRotationYSetting
	internal val expShareStaticRotationZSetting = NumberSetting("Exp-Share Static Rotation Z", 0.0, 0.0, 360.0, 5.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareStaticRotationZ by expShareStaticRotationZSetting
	internal val expShareSpinRotationXSetting = NumberSetting("Exp-Share Rotation Speed X", 0.0, -725.0, 725.0, 25.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareSpinRotationX by expShareSpinRotationXSetting
	internal val expShareSpinRotationYSetting = NumberSetting("Exp-Share Rotation Speed Y", 0.0, -725.0, 725.0, 25.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareSpinRotationY by expShareSpinRotationYSetting
	internal val expShareSpinRotationZSetting = NumberSetting("Exp-Share Rotation Speed Z", 0.0, -725.0, 725.0, 1.0)
		.withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var expShareSpinRotationZ by expShareSpinRotationZSetting
	internal val resetExpShareStaticRotationSetting = ActionSetting("Reset Exp-Share Static Rotations", {
		expShareStaticRotationXSetting.reset()
		expShareStaticRotationYSetting.reset()
		expShareStaticRotationZSetting.reset()
	}).withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var resetExpShareStaticRotation by resetExpShareStaticRotationSetting
	internal val resetExpShareSpinRotationSetting = ActionSetting("Reset Exp-Share Rotation Speeds", {
		expShareSpinRotationXSetting.reset()
		expShareSpinRotationYSetting.reset()
		expShareSpinRotationZSetting.reset()
	}).withDependency { expSharePetsSetting.on && expShareIconSetting.on }
	private var resetExpShareSpinRotation by resetExpShareSpinRotationSetting
	internal val resetExpShareIconSetting = ActionSetting("Reset Exp-Share Icon Settings", {
		expShareIconSetting.reset()
		expShareSkinAnimationSetting.reset()
		expShareSkinAnimationSpeedSetting.reset()
		expShareIconScaleSetting.reset()
		expShareStaticRotationXSetting.reset()
		expShareStaticRotationYSetting.reset()
		expShareStaticRotationZSetting.reset()
		expShareSpinRotationXSetting.reset()
		expShareSpinRotationYSetting.reset()
		expShareSpinRotationZSetting.reset()
	}).withDependency { expSharePetsSetting.on }
	private var resetExpShareIcon by resetExpShareIconSetting
	internal val expShareBackgroundSetting = BooleanSetting("Exp-Share Background Enabled", true)
		.withDependency { expSharePetsSetting.on }
	private var expShareBackground by expShareBackgroundSetting
	internal val expShareBackgroundPaddingSetting = NumberSetting("Exp-Share Background Padding", 2.4, 0.0, 8.0, 0.25)
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareBackgroundPadding by expShareBackgroundPaddingSetting
	internal val expShareCommonColorSetting = ColorSetting("Exp-Share Common Color", Color.rgba(255, 255, 255))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareCommonColor by expShareCommonColorSetting
	internal val expShareUncommonColorSetting = ColorSetting("Exp-Share Uncommon Color", Color.rgba(85, 255, 85))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareUncommonColor by expShareUncommonColorSetting
	internal val expShareRareColorSetting = ColorSetting("Exp-Share Rare Color", Color.rgba(85, 85, 255))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareRareColor by expShareRareColorSetting
	internal val expShareEpicColorSetting = ColorSetting("Exp-Share Epic Color", Color.rgba(170, 0, 170))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareEpicColor by expShareEpicColorSetting
	internal val expShareLegendaryColorSetting = ColorSetting("Exp-Share Legendary Color", Color.rgba(255, 170, 0))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareLegendaryColor by expShareLegendaryColorSetting
	internal val expShareMythicColorSetting = ColorSetting("Exp-Share Mythic Color", Color.rgba(255, 85, 255))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareMythicColor by expShareMythicColorSetting
	internal val expShareRingSetting = BooleanSetting("Exp-Share XP Ring Enabled", true)
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on }
	private var expShareRing by expShareRingSetting
	internal val expShareRingPaddingSetting = NumberSetting("Exp-Share Ring Padding", 2.0, 2.0, 10.0, 0.5)
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on }
	private var expShareRingPadding by expShareRingPaddingSetting
	internal val expShareFilledRingColorSetting = ColorSetting("Exp-Share Filled Ring Color", Color.rgba(0, 255, 255))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on }
	private var expShareFilledRingColor by expShareFilledRingColorSetting
	internal val expShareUnfilledRingColorSetting = ColorSetting("Exp-Share Unfilled Ring Color", Color.rgba(192, 192, 192))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on }
	private var expShareUnfilledRingColor by expShareUnfilledRingColorSetting
	internal val expShareSeparatorRingSetting = BooleanSetting("Exp-Share Separator Ring Enabled", true)
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on }
	private var expShareSeparatorRing by expShareSeparatorRingSetting
	internal val expShareSeparatorRingPaddingSetting = NumberSetting("Exp-Share Separator Ring Padding", 2.0, 2.0, 10.0, 0.5)
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on && expShareSeparatorRingSetting.on }
	private var expShareSeparatorRingPadding by expShareSeparatorRingPaddingSetting
	internal val expShareSeparatorRingColorSetting = ColorSetting("Exp-Share Ring Color", Color.rgba(128, 128, 128))
		.withDependency { expSharePetsSetting.on && expShareBackgroundSetting.on && expShareRingSetting.on && expShareSeparatorRingSetting.on }
	private var expShareSeparatorRingColor by expShareSeparatorRingColorSetting
	internal val resetExpShareBackgroundSetting = ActionSetting("Reset Exp-Share Background Settings", {
		expShareBackgroundSetting.reset()
		expShareBackgroundPaddingSetting.reset()
		expShareCommonColorSetting.reset()
		expShareUncommonColorSetting.reset()
		expShareRareColorSetting.reset()
		expShareEpicColorSetting.reset()
		expShareLegendaryColorSetting.reset()
		expShareMythicColorSetting.reset()
	}).withDependency { expSharePetsSetting.on }
	private var resetExpShareBackground by resetExpShareBackgroundSetting
	internal val resetExpShareRingsSetting = ActionSetting("Reset Exp-Share Ring Settings", {
		expShareRingSetting.reset()
		expShareRingPaddingSetting.reset()
		expShareFilledRingColorSetting.reset()
		expShareUnfilledRingColorSetting.reset()
		expShareSeparatorRingSetting.reset()
		expShareSeparatorRingPaddingSetting.reset()
		expShareSeparatorRingColorSetting.reset()
	}).withDependency { expSharePetsSetting.on }
	private var resetExpShareRings by resetExpShareRingsSetting
	internal val expSharePetItemSetting = BooleanSetting("Exp-Share Pet Item Enabled")
		.withDependency { expSharePetsSetting.on }
	private var expSharePetItem by expSharePetItemSetting
	internal val expSharePetItemPlacementSetting = SelectorSetting(
		"Exp-Share Pet Item Placement",
		"Bottom Right",
		hudPetItemPlacementSetting.options,
		listed = true
	).withDependency { expSharePetsSetting.on && expSharePetItemSetting.on }
	private var expSharePetItemPlacement by expSharePetItemPlacementSetting
	internal val expSharePetItemScaleSetting = NumberSetting("Exp-Share Pet Item Scale", 0.6, 0.1, 2.0, 0.1)
		.withDependency { expSharePetsSetting.on && expSharePetItemSetting.on }
	private var expSharePetItemScale by expSharePetItemScaleSetting

	internal val enabledTextSetting = OrderedSelectionSetting(
		"Enabled Text",
		listOf("Pet Name", "Next Level", "Overflow XP", "Total XP", "Held Item"),
		listOf("Pet Name", "Next Level", "Held Item")
	)
	private var enabledText by enabledTextSetting
	internal val textLabelsSetting = BooleanSetting("Text Labels", true)
	private var textLabels by textLabelsSetting
	internal val textPetLevelSetting = BooleanSetting("Pet Level", true)
		.withDependency { enabledTextSetting.enabled("Pet Name") }
	private var textPetLevel by textPetLevelSetting
	internal val textSkinSymbolSetting = BooleanSetting("Skin Symbol", true)
		.withDependency { enabledTextSetting.enabled("Pet Name") }
	private var textSkinSymbol by textSkinSymbolSetting
	internal val nextLevelPercentSetting = BooleanSetting("Next Level %", true)
		.withDependency { enabledTextSetting.enabled("Next Level") }
	private var nextLevelPercent by nextLevelPercentSetting
	internal val xpFormatSetting = SelectorSetting("XP Format", "Default", listOf("Default", "Formatted", "Unformatted"))
	private var xpFormat by xpFormatSetting
	internal val textScaleSetting = NumberSetting("Text Scale", 1.0, 0.5, 2.0, 0.05)
	private var textScale by textScaleSetting
	internal val textLocationSetting = SelectorSetting("Text Location", "Right", listOf("Top", "Bottom", "Left", "Right"))
	private var textLocation by textLocationSetting
	internal val centerTargetSetting = SelectorSetting(
		"Center Target",
		"Equipped Pet Visuals",
		listOf("Equipped Pet Visuals", "All Pet Visuals")
	)
	private var centerTarget by centerTargetSetting
	internal val verticalAlignmentSetting = SelectorSetting("Vertical Alignment", "Center", listOf("Top", "Center", "Bottom"))
	private var verticalAlignment by verticalAlignmentSetting
	internal val horizontalAlignmentSetting = SelectorSetting("Horizontal Alignment", "Left", listOf("Left", "Center", "Right"))
	private var horizontalAlignment by horizontalAlignmentSetting

	internal val expShareTextSetting = BooleanSetting("Exp-Share Text Enabled")
		.withDependency { expSharePetsSetting.on }
	private var expShareText by expShareTextSetting
	internal val expShareTextModeSetting = SelectorSetting("Exp-Share Text Mode", "Bundled", listOf("Bundled", "Attached"))
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareTextMode by expShareTextModeSetting
	internal val bundledLocationSetting = SelectorSetting(
		"Bundled Location",
		"Below Main Text",
		listOf("Above Main Text", "Below Main Text", "Split Around Main Text")
	).withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareTextModeSetting.value == "Bundled" }
	private var bundledLocation by bundledLocationSetting
	internal val bundledSpacingSetting = NumberSetting("Bundled Spacing", 9.0, 0.0, 20.0, 1.0)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareTextModeSetting.value == "Bundled" }
	private var bundledSpacing by bundledSpacingSetting
	internal val expShareTextScaleSetting = NumberSetting("Exp-Share Text Scale", 1.0, 0.5, 2.0, 0.05)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareTextScale by expShareTextScaleSetting
	internal val expShareEnabledTextSetting = OrderedSelectionSetting(
		"Exp-Share Enabled Text",
		enabledTextSetting.options,
		listOf("Pet Name", "Next Level")
	).withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareEnabledText by expShareEnabledTextSetting
	internal val expShareTextLabelsSetting = BooleanSetting("Exp-Share Text Labels", true)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareTextLabels by expShareTextLabelsSetting
	internal val expShareTextPetLevelSetting = BooleanSetting("Exp-Share Pet Level", true)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareEnabledTextSetting.enabled("Pet Name") }
	private var expShareTextPetLevel by expShareTextPetLevelSetting
	internal val expShareTextSkinSymbolSetting = BooleanSetting("Exp-Share Skin Symbol", true)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareEnabledTextSetting.enabled("Pet Name") }
	private var expShareTextSkinSymbol by expShareTextSkinSymbolSetting
	internal val expShareNextLevelPercentSetting = BooleanSetting("Exp-Share Next Level %", true)
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareEnabledTextSetting.enabled("Next Level") }
	private var expShareNextLevelPercent by expShareNextLevelPercentSetting
	internal val expShareXpFormatSetting = SelectorSetting("Exp-Share XP Format", "Default", listOf("Default", "Formatted", "Unformatted"))
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareXpFormat by expShareXpFormatSetting
	internal val expShareTextLocationSetting = SelectorSetting("Exp-Share Text Location", "Right", listOf("Top", "Bottom", "Left", "Right"))
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on && expShareTextModeSetting.value == "Attached" }
	private var expShareTextLocation by expShareTextLocationSetting
	internal val expShareVerticalAlignmentSetting = SelectorSetting("Exp-Share Vertical Alignment", "Center", listOf("Top", "Center", "Bottom"))
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareVerticalAlignment by expShareVerticalAlignmentSetting
	internal val expShareHorizontalAlignmentSetting = SelectorSetting("Exp-Share Horizontal Alignment", "Left", listOf("Left", "Center", "Right"))
		.withDependency { expSharePetsSetting.on && expShareTextSetting.on }
	private var expShareHorizontalAlignment by expShareHorizontalAlignmentSetting

	private val nametags = PetNametags()
	private val petItems = SkyBlockItems.memo(TRACKED_SLOTS)
	private val petLevels = PetSlotLevels(TRACKED_SLOTS)
	private val candyLabels = Array(MAX_CANDY + 1) { CANDY_COLOR + it }
	private val petWheel = PetWheelScreen()
	private val petExpTooltip = PetExpTooltip()
	private val georgeHelper = GeorgeHelper()
	internal val hudElement = hud(PetDisplayHud())
	internal val georgeElement = hud(GeorgeHelperElement(georgeHelper))
	private var repoRequirement = Handle {}
	private var mayorRequirement = Handle {}
	private var priceRequirement = Handle {}
	private var repoHeld = false
	private var mayorHeld = false
	private var priceHeld = false

	init {
		on<ChatReceiveEvent> { autopetted(it) }
		on<ContainerReadyEvent> {
			petWheel.ready(it)
			georgeHelper.ready(it)
		}
		on<ContainerUpdatedEvent> {
			petWheel.updated(it)
			georgeHelper.updated(it)
		}
		on<ContainerClosedEvent> {
			petWheel.closed(it)
			georgeHelper.closed(it)
		}
		on<ScreenRenderEvent.Pre> { petWheel.render(it) }
		on<ContainerClickEvent> { petWheel.clicked(it) }
		on<ContainerKeyEvent> { petWheel.keyed(it) }
		on<ContainerScrollEvent> { petWheel.scrolled(it) }
		on<SlotRenderEvent.Pre> { highlighted(it) }
		on<SlotRenderEvent.Post> { decorated(it) }
		on<EntityNameTagEvent> { renamed(it) }
		on<TooltipEvent> {
			if (petExpTooltipEnabled) petExpTooltip.add(it, showPetExpAlways, dragonEgg, shiftHeld())
		}
		on<ClientTickEvent.End> { tickHud() }
		on<WorldChangeEvent> { georgeHelper.reset() }
	}

	override fun onEnabled() {
		ensureRequirements()
		hudElement.refresh()
	}

	override fun onDisabled() {
		repoRequirement.unsubscribe()
		mayorRequirement.unsubscribe()
		priceRequirement.unsubscribe()
		repoRequirement = Handle {}
		mayorRequirement = Handle {}
		priceRequirement = Handle {}
		repoHeld = false
		mayorHeld = false
		priceHeld = false
		petWheel.reset()
		petExpTooltip.reset()
		georgeHelper.reset()
		hudElement.refresh()
	}

	override fun onReset() = hudElement.refresh()

	private fun tickHud() {
		ensureRequirements()
		georgeHelper.tick(fetchOtherTiers)
		hudElement.refresh()
	}

	private fun ensureRequirements() {
		if (!repoHeld && ItemRepo.active()) {
			repoRequirement = ItemRepo.require()
			repoHeld = true
		}
		if (!mayorHeld && MayorService.active()) {
			mayorRequirement = MayorService.require()
			mayorHeld = true
		}
		if (georgeHelperEnabled && !priceHeld && Prices.active()) {
			priceRequirement = Prices.require()
			priceHeld = true
		} else if (!georgeHelperEnabled && priceHeld) {
			priceRequirement.unsubscribe()
			priceRequirement = Handle {}
			priceHeld = false
		}
	}

	internal fun titled(pet: String, dungeon: Boolean): Boolean =
		autoPetTitleSetting.on && pet.isNotEmpty() && (!dungeonsOnlySetting.on || dungeon)

	private fun autopetted(event: ChatReceiveEvent) {
		val pet = PetLines.autopetted(event.styled) ?: return
		if (hideAutopetMessages) event.cancelled = true
		if (!titled(pet, SkyBlockLocation.island == Island.CATACOMBS)) return
		DhenAlert.show(pet, ticks = TITLE_TICKS, sound = null)
	}

	private fun highlighted(event: SlotRenderEvent.Pre) {
		if (!highlight || !SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		if (slot.index != CurrentPet.menuSlot) return
		SharpGui.fill(
			event.graphics,
			slot.x,
			slot.y,
			slot.x + SLOT_SIZE,
			slot.y + SLOT_SIZE,
			highlightColorSetting.value.argb
		)
	}

	private fun renamed(event: EntityNameTagEvent) {
		if (!hidePetLevel && !hideMaxPetLevel) return
		val stand = event.entity
		if (stand !is ArmorStand || !SkyBlockLocation.inSkyBlock) return
		event.nameTag = nametags.rewrite(stand.id, event.nameTag, hidePetLevel, hideMaxPetLevel)
	}

	private fun decorated(event: SlotRenderEvent.Post) {
		if (!petCandy && !expShare && !tierBoost) return
		if (!SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		val stack = slot.item
		if (stack.count != LONE_STACK) return
		val pet = petItems.of(slot.index, stack).pet ?: return
		candied(event.graphics, slot, stack, pet)
		holding(event.graphics, slot, pet)
	}

	private fun candied(graphics: GuiGraphicsExtractor, slot: Slot, stack: ItemStack, pet: PetInfo) {
		if (!petCandy || pet.candyUsed <= NO_CANDY) return
		if (hideOnMaxed && PetLines.maxed(petLevels.of(slot.index, stack), pet.type)) return
		val label = if (pet.candyUsed <= MAX_CANDY) candyLabels[pet.candyUsed] else CANDY_COLOR + pet.candyUsed
		slotText(graphics, label, slot.x + CANDY_RIGHT, slot.y + CANDY_TOP, CANDY_SCALE)
	}

	private fun holding(graphics: GuiGraphicsExtractor, slot: Slot, pet: PetInfo) {
		when (pet.heldItem) {
			EXP_SHARE -> if (expShare) petItem(graphics, slot, EXP_SHARE_ICON)
			TIER_BOOST -> if (tierBoost) petItem(graphics, slot, TIER_BOOST_ICON)
		}
	}

	private fun petItem(graphics: GuiGraphicsExtractor, slot: Slot, icon: String) {
		val scale = petItemScaleSetting.amount.toFloat()
		val scaled = (DhenType.width(Minecraft.getInstance().font, icon) * scale).toInt()
		slotText(graphics, icon, slot.x + PET_ITEM_RIGHT - scaled, slot.y + PET_ITEM_TOP, scale)
	}

	private fun slotText(graphics: GuiGraphicsExtractor, text: String, right: Int, top: Int, scale: Float) {
		val font = Minecraft.getInstance().font
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate((right - DhenType.width(font, text)).toFloat(), top.toFloat())
			pose.scale(scale, scale)
			DhenType.text(graphics, font, text, 0, 0, DhenPalette.TEXT_PRIMARY, true)
		} finally {
			pose.popMatrix()
		}
	}

	private fun petSlotSetting(slot: Int): KeybindSetting = KeybindSetting(
		"Pet Slot $slot",
		GLFW.GLFW_KEY_1 + slot - 1,
		screenPolicy = KeybindScreenPolicy.NON_TEXT_SCREEN
	).withDependency { !useHotbarBindsSetting.on }

	private const val TITLE_TICKS = 40
	private const val SLOT_SIZE = 16
	private const val TRACKED_SLOTS = 128
	private const val LONE_STACK = 1
	private const val NO_CANDY = 0
	private const val MAX_CANDY = 10
	private const val CANDY_COLOR = "§c"
	private const val CANDY_RIGHT = 13
	private const val CANDY_TOP = 1
	private const val CANDY_SCALE = 0.9f
	private const val PET_ITEM_RIGHT = 22
	private const val PET_ITEM_TOP = -1
	private const val EXP_SHARE = "PET_ITEM_EXP_SHARE"
	private const val TIER_BOOST = "PET_ITEM_TIER_BOOST"
	private const val EXP_SHARE_ICON = "§5⚘"
	private const val TIER_BOOST_ICON = "§c●"
}
