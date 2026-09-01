package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.data.pet.PetLines
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.EntityNameTagEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

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

	private val nametags = PetNametags()
	private val petItems = SkyBlockItems.memo(TRACKED_SLOTS)
	private val petLevels = PetSlotLevels(TRACKED_SLOTS)
	private val candyLabels = Array(MAX_CANDY + 1) { CANDY_COLOR + it }

	init {
		on<ChatReceiveEvent> { autopetted(it) }
		on<SlotRenderEvent.Pre> { highlighted(it) }
		on<SlotRenderEvent.Post> { decorated(it) }
		on<EntityNameTagEvent> { renamed(it) }
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
