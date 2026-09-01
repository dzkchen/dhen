package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.data.pet.PetLines
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.Color

object PetDisplay : Module(
	name = "Pet Display",
	category = Category.VISUAL,
	description = "Announces autopet swaps with a title and highlights the pet you have summoned in the Pets menu."
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

	init {
		on<ChatReceiveEvent> { autopetted(it) }
		on<SlotRenderEvent.Pre> { highlighted(it) }
	}

	internal fun titled(pet: String, dungeon: Boolean): Boolean =
		autoPetTitleSetting.on && pet.isNotEmpty() && (!dungeonsOnlySetting.on || dungeon)

	private fun autopetted(event: ChatReceiveEvent) {
		val pet = PetLines.autopetted(event.styled) ?: return
		if (hideAutopetMessagesSetting.on) event.cancelled = true
		if (!titled(pet, SkyBlockLocation.island == Island.CATACOMBS)) return
		DhenAlert.show(pet, ticks = TITLE_TICKS, sound = null)
	}

	private fun highlighted(event: SlotRenderEvent.Pre) {
		if (!highlightSetting.on || !SkyBlockLocation.inSkyBlock) return
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

	private const val TITLE_TICKS = 40
	private const val SLOT_SIZE = 16
}
