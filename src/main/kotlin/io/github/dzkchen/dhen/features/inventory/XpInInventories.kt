package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.FINAL_WORD
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

object XpInInventories : Module(
	name = "XP In Inventories",
	category = Category.INVENTORY,
	description = "Adds your own experience level under any menu line that asks for experience levels."
) {
	private val costLine = Pattern.compile(COST_LINE).matcher("")

	init {
		on<TooltipEvent>(FINAL_WORD) { compared(it) }
	}

	private fun compared(event: TooltipEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val lines = event.lines
		for (index in lines.indices) {
			if (!costLine.reset(withoutCodes(lines[index].string)).find()) continue
			val required = costLine.group("xp").toIntOrNull() ?: return
			val held = Minecraft.getInstance().player?.experienceLevel ?: return
			val color = if (held >= required) ENOUGH else SHORT
			event.edit().add(index + 1, DhenType.component("$LABEL$color$held"))
			return
		}
	}

	private const val COST_LINE = "(?<xp>\\d+) (?:Exp|XP) Levels"
	private const val LABEL = "§7Your XP: "
	private const val ENOUGH = "§a"
	private const val SHORT = "§c"
}
