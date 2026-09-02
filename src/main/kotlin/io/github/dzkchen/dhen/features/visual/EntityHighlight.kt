package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.HighlightStyle
import io.github.dzkchen.dhen.render.NO_HIGHLIGHT
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

object EntityHighlight : Module(
	name = "Entity Highlight",
	category = Category.VISUAL,
	description = "Draws a styled box around starred dungeon mobs and hides the name stands of the rest."
) {
	internal val highlightStarredSetting = BooleanSetting(
		"Highlight Starred Mobs",
		true,
		description = "Boxes the mob standing under a starred name tag."
	)
	internal val colorSetting = ColorSetting(
		"Highlight Color",
		Color.rgba(255, 255, 255),
		allowAlpha = true,
		description = "The colour of the box."
	)
	internal val styleSetting = SelectorSetting(
		"Render Style",
		OUTLINE,
		listOf(OUTLINE, FILLED, FILLED_OUTLINE),
		description = "How every Dhen highlight box is drawn."
	)
	internal val hideNonStarredNamesSetting = BooleanSetting(
		"Hide Non-Starred Names",
		true,
		description = "Removes the invisible name stands floating over unstarred mobs."
	)

	private var highlightStarred by highlightStarredSetting
	private var color by colorSetting
	private var style by styleSetting
	private var hideNonStarredNames by hideNonStarredNamesSetting

	private val starredMobs = HashSet<Entity>()
	private val abandoned = ArrayList<ArmorStand>()
	private var rule: Handle? = null

	init {
		on<ClientTickEvent.End> { sweep() }
		on<WorldChangeEvent> { starredMobs.clear() }
	}

	override fun onEnabled() {
		rule = EntityHighlights.boxes(::boxStyle) { entity ->
			if (highlightStarred && entity in starredMobs) color.argb else NO_HIGHLIGHT
		}
	}

	override fun onDisabled() {
		rule?.unsubscribe()
		rule = null
		starredMobs.clear()
		abandoned.clear()
	}

	internal fun debugColor(): Int = color.argb

	internal fun boxStyle(): HighlightStyle = when (style) {
		FILLED -> HighlightStyle.FILLED
		FILLED_OUTLINE -> HighlightStyle.FILLED_OUTLINE
		else -> HighlightStyle.OUTLINE
	}

	private fun sweep() {
		if (!highlightStarred) starredMobs.clear()
		if (SkyBlockLocation.island != Island.CATACOMBS) return
		if (!highlightStarred && !hideNonStarredNames) return
		val level = Minecraft.getInstance().level ?: return
		for (entity in level.entitiesForRendering()) {
			if (!entity.isAlive || entity !is ArmorStand) continue
			val name = entity.name.string
			if (SPAWN_NAMES.none { it in name }) continue
			val starred = STARRED.matches(name)
			if (hideNonStarredNames && entity.isInvisible && !starred) abandoned += entity
			if (highlightStarred && starred) EntityHighlights.mobUnder(entity)?.let(starredMobs::add)
		}
		for (stand in abandoned) stand.remove(Entity.RemovalReason.DISCARDED)
		abandoned.clear()
		starredMobs.removeIf { !it.isAlive }
	}

	private const val OUTLINE = "Outline"
	private const val FILLED = "Filled"
	private const val FILLED_OUTLINE = "Filled Outline"

	private val STARRED = Regex("""^.*✯ .*\d{1,3}(?:,\d{3})*(?:\.\d+)?.?❤$""")
	private val SPAWN_NAMES = listOf(
		"Lurker",
		"Dreadlord",
		"Souleater",
		"Zombie",
		"Skeleton",
		"Skeletor",
		"Sniper",
		"Super Archer",
		"Spider",
		"Fels",
		"Withermancer",
		"Lost Adventurer",
		"Angry Archaeologist",
		"Frozen Adventurer"
	)
}
