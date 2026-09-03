package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.NO_HIGHLIGHT
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.world.level.block.Blocks
import java.util.WeakHashMap

object MobHighlight : Module(
	name = "Mob Highlight",
	category = Category.COMBAT,
	description = "Outlines the mobs worth finding: Corleone, Arachne and her brood, runic and corrupted mobs, and Zealots."
) {
	internal val corleoneSetting = BooleanSetting("Corleone", true, "Highlights Boss Corleone in the Crystal Hollows.")
	internal val corleoneColorSetting = ColorSetting("Corleone Color", legacy(ChatFormatting.DARK_PURPLE))
		.withDependency { corleoneSetting.on }

	internal val keeperSetting = BooleanSetting("Arachne Keeper", true, "Highlights the Arachne Keeper in the Spider's Den.")
	internal val keeperColorSetting = ColorSetting("Arachne Keeper Color", legacy(ChatFormatting.DARK_BLUE))
		.withDependency { keeperSetting.on }

	internal val arachneSetting = BooleanSetting("Arachne Boss", true, "Highlights Arachne and her brood.")
	internal val arachneColorSetting = ColorSetting("Arachne Color", legacy(ChatFormatting.RED))
		.withDependency { arachneSetting.on }
	internal val broodColorSetting = ColorSetting("Arachne Brood Color", legacy(ChatFormatting.GOLD))
		.withDependency { arachneSetting.on }

	internal val runicSetting = BooleanSetting("Runic Mob", description = "Highlights runic mobs.")
	internal val runicColorSetting = ColorSetting("Runic Mob Color", legacy(ChatFormatting.LIGHT_PURPLE))
		.withDependency { runicSetting.on }

	internal val corruptedSetting = BooleanSetting("Corrupted Mob", description = "Highlights corrupted mobs.")
	internal val corruptedColorSetting = ColorSetting("Corrupted Mob Color", legacy(ChatFormatting.DARK_PURPLE))
		.withDependency { corruptedSetting.on }

	internal val zealotSetting = BooleanSetting("Zealot", description = "Highlights Zealots and Bruisers in The End.")
	internal val zealotColorSetting = ColorSetting("Zealot Color", legacy(ChatFormatting.DARK_AQUA))
		.withDependency { zealotSetting.on }

	internal val chestZealotSetting = BooleanSetting("Zealot with Chest", description = "Highlights Zealots carrying a chest.")
	internal val chestZealotColorSetting = ColorSetting("Zealot with Chest Color", legacy(ChatFormatting.GREEN))
		.withDependency { chestZealotSetting.on }

	internal val specialZealotSetting = BooleanSetting("Special Zealots", true, "Highlights the Zealots that drop Summoning Eyes.")
	internal val specialZealotColorSetting = ColorSetting("Special Zealot Color", legacy(ChatFormatting.DARK_RED))
		.withDependency { specialZealotSetting.on }

	internal val lineToArachneSetting = BooleanSetting("Line to Arachne", description = "Draws a line pointing at Arachne.")
	internal val lineToArachneWidthSetting = NumberSetting("Line to Arachne Width", 5.0, 1.0, 10.0, 1.0)
		.withDependency { lineToArachneSetting.on }

	private var corleone by corleoneSetting
	private var corleoneColor by corleoneColorSetting
	private var keeper by keeperSetting
	private var keeperColor by keeperColorSetting
	private var arachneBoss by arachneSetting
	private var arachneColor by arachneColorSetting
	private var broodColor by broodColorSetting
	private var runic by runicSetting
	private var runicColor by runicColorSetting
	private var corrupted by corruptedSetting
	private var corruptedColor by corruptedColorSetting
	private var zealot by zealotSetting
	private var zealotColor by zealotColorSetting
	private var chestZealot by chestZealotSetting
	private var chestZealotColor by chestZealotColorSetting
	private var specialZealot by specialZealotSetting
	private var specialZealotColor by specialZealotColorSetting
	private var lineToArachne by lineToArachneSetting
	private var lineToArachneWidth by lineToArachneWidthSetting

	private val paints = Reference2IntOpenHashMap<Entity>()
	private val standNames = WeakHashMap<ArmorStand, String>()
	private var arachne: Entity? = null
	private var rule: Handle? = null
	private val mayorHold = RequirementHold(MayorService::active, MayorService::require)

	init {
		on<ClientTickEvent.End> { sweep() }
		on<WorldRenderEvent> { pointAtArachne(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onEnabled() {
		paints.defaultReturnValue(NO_HIGHLIGHT)
		rule = EntityHighlights.glow { entity -> paints.getInt(entity) }
		mayorHold.ensure()
	}

	override fun onDisabled() {
		rule?.unsubscribe()
		rule = null
		mayorHold.release()
		forget()
	}

	private fun forget() {
		paints.clear()
		standNames.clear()
		arachne = null
	}

	private fun sweep() {
		paints.clear()
		if (!SkyBlockLocation.inSkyBlock) return
		val level = Minecraft.getInstance().level ?: return
		if (arachne?.isAlive != true) arachne = null
		val inTheEnd = SkyBlockLocation.island == Island.THE_END
		for (entity in level.entitiesForRendering()) {
			if (!entity.isAlive) continue
			when {
				entity is ArmorStand -> readNameTag(entity)
				entity is EnderMan && inTheEnd -> readZealot(entity)
				entity is LivingEntity && corrupted -> readCorrupted(entity)
			}
		}
	}

	private fun readNameTag(stand: ArmorStand) {
		if (!corleone && !keeper && !arachneBoss && !runic && !lineToArachne) return
		val name = standNames.getOrPut(stand) {
			MOB_NAME.find(stand.name.string)?.groupValues?.get(NAME_GROUP)?.trim().orEmpty()
		}
		if (name.isEmpty()) return
		val color = when (name) {
			CORLEONE -> if (corleone) corleoneColor.argb else NO_HIGHLIGHT
			KEEPER -> if (keeper) keeperColor.argb else NO_HIGHLIGHT
			BROOD -> if (arachneBoss) broodColor.argb else NO_HIGHLIGHT
			ARACHNE -> if (arachneBoss) arachneColor.argb else NO_HIGHLIGHT
			else -> if (runic && legacyCodes(stand.name).startsWith(RUNIC_PREFIX)) runicColor.argb else NO_HIGHLIGHT
		}
		if (color == NO_HIGHLIGHT && name != ARACHNE) return
		val mob = EntityHighlights.mobUnder(stand) ?: return
		if (name == ARACHNE) arachne = mob
		if (color != NO_HIGHLIGHT) paints.put(mob, color)
	}

	private fun readZealot(enderman: EnderMan) {
		if (!zealot && !chestZealot && !specialZealot) return
		val block = enderman.carriedBlock?.block
		if (block == Blocks.END_PORTAL_FRAME) {
			if (specialZealot) paints.put(enderman, specialZealotColor.argb)
			return
		}
		if (block == Blocks.ENDER_CHEST) {
			if (chestZealot) paints.put(enderman, chestZealotColor.argb)
			return
		}
		if (zealot && baseMaxHealth(enderman) in ZEALOT_HEALTHS) paints.put(enderman, zealotColor.argb)
	}

	private fun readCorrupted(entity: LivingEntity) {
		val health = derpy(realHealth(entity))
		val maxHealth = baseMaxHealth(entity)
		if (maxHealth == health * CORRUPTED_MULTIPLIER || maxHealth == health * CORRUPTED_MULTIPLIER * RUNIC_MULTIPLIER) {
			paints.put(entity, corruptedColor.argb)
		}
	}

	private fun pointAtArachne(event: WorldRenderEvent) {
		if (!lineToArachne) return
		val target = arachne ?: return
		if (!target.isAlive) return
		val self = Minecraft.getInstance().player ?: return
		if (target.distanceToSqr(self) > ARACHNE_LINE_RANGE * ARACHNE_LINE_RANGE) return
		WorldDraw.drawTracer(
			event,
			target.x,
			target.y + target.bbHeight / 2.0,
			target.z,
			legacyColor(ChatFormatting.RED),
			lineToArachneWidth.toFloat()
		)
	}

	private fun realHealth(entity: LivingEntity): Int =
		if (entity.health == SERVER_MASKED_HEALTH) baseMaxHealth(entity) else entity.health.toInt()

	private fun baseMaxHealth(entity: LivingEntity): Int =
		entity.getAttributeBaseValue(Attributes.MAX_HEALTH).toInt()

	private fun derpy(health: Int): Int {
		var scaled = health
		if (MayorService.isPerkActive(DOUBLE_MOBS_HP)) scaled /= DOUBLE_MOBS_HP_DIVISOR
		if (MayorService.isPerkActive(WORK_HARDER)) scaled = scaled / WORK_HARDER_DIVISOR * WORK_HARDER_FACTOR
		return scaled
	}

	private fun legacy(formatting: ChatFormatting): Color = Color(legacyColor(formatting))

	private val MOB_NAME =
		Regex("""(?:\[Lv(?:\d+)] )?(?:[^\w\s✯\-]+ )?(?:.Corrupted )?([^ᛤ]*)(?: ᛤ)? [\dBMk.,❤]+""")
	private const val NAME_GROUP = 1
	private const val CORLEONE = "Boss Corleone"
	private const val KEEPER = "Arachne's Keeper"
	private const val BROOD = "Arachne's Brood"
	private const val ARACHNE = "Arachne"
	private const val RUNIC_PREFIX = "§5"
	private const val DOUBLE_MOBS_HP = "DOUBLE MOBS HP!!!"
	private const val WORK_HARDER = "Work Harder"
	private const val DOUBLE_MOBS_HP_DIVISOR = 2
	private const val WORK_HARDER_DIVISOR = 11
	private const val WORK_HARDER_FACTOR = 10
	private const val CORRUPTED_MULTIPLIER = 3
	private const val RUNIC_MULTIPLIER = 4
	private const val SERVER_MASKED_HEALTH = 1024f
	private const val ARACHNE_LINE_RANGE = 10.0
	private val ZEALOT_HEALTHS = intArrayOf(13_000, 65_000, 13_000 * 4, 65_000 * 4)
}
