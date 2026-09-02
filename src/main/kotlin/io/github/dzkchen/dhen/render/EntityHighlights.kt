package io.github.dzkchen.dhen.render

import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EntityGlowEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import java.util.function.Predicate

internal enum class HighlightStyle { FILLED, OUTLINE, FILLED_OUTLINE }

internal const val NO_HIGHLIGHT = 0

internal object EntityHighlights {
	fun interface Rule {
		fun colorOf(entity: Entity): Int
	}

	private class BoxRule(val style: () -> HighlightStyle, val paint: Rule)

	private const val CAPACITY = 32
	private const val FILL_ALPHA_UNDER_OUTLINE = 0.5f
	private const val FILL_INSET_UNDER_OUTLINE = 0.00005
	private const val NPC_UUID_VERSION = 2

	private var bus: EventBus? = null

	@Volatile
	private var boxRules: Array<BoxRule> = emptyArray()

	@Volatile
	private var glowRules: Array<Rule> = emptyArray()

	private var boxHandles: Array<Handle> = emptyArray()
	private var glowHandle: Handle? = null
	private var debugRule: Handle? = null

	private var matched = arrayOfNulls<Entity>(CAPACITY)
	private var matchedColors = IntArray(CAPACITY)
	private var matchedStyles = arrayOfNulls<HighlightStyle>(CAPACITY)
	private var matchedCount = 0

	private val validEntities = Predicate<Entity>(::isValidEntity)

	fun install(bus: EventBus) {
		synchronized(this) {
			this.bus = bus
			if (boxRules.isNotEmpty() && boxHandles.isEmpty()) boxHandles = openBoxFeed()
			if (glowRules.isNotEmpty() && glowHandle == null) glowHandle = bus.subscribe<EntityGlowEvent>(handler = ::paintGlow)
		}
	}

	fun boxes(style: () -> HighlightStyle, rule: Rule): Handle {
		val registration = BoxRule(style, rule)
		synchronized(this) {
			boxRules += registration
			if (boxHandles.isEmpty()) boxHandles = openBoxFeed()
		}
		return Handle { releaseBoxes(registration) }
	}

	fun glow(rule: Rule): Handle {
		synchronized(this) {
			glowRules += rule
			if (glowHandle == null) glowHandle = bus?.subscribe<EntityGlowEvent>(handler = ::paintGlow)
		}
		return Handle { releaseGlow(rule) }
	}

	fun toggleDebugRule(style: () -> HighlightStyle, color: () -> Int): Boolean {
		val current = debugRule
		debugRule = if (current == null) {
			boxes(style) { entity -> if (entity is Zombie) color() else NO_HIGHLIGHT }
		} else {
			current.unsubscribe()
			null
		}
		return debugRule != null
	}

	fun isValidEntity(entity: Entity): Boolean = when (entity) {
		is ArmorStand -> false
		is WitherBoss -> false
		is Player -> entity.uuid.version() == NPC_UUID_VERSION && entity !== Minecraft.getInstance().player
		else -> !entity.isInvisible
	}

	fun mobUnder(stand: ArmorStand): Entity? =
		stand.level().getEntities(stand, stand.boundingBox.move(0.0, -1.0, 0.0), validEntities).firstOrNull()

	fun drawEntityBox(
		event: WorldRenderEvent,
		entity: Entity,
		partialTick: Float,
		inflate: Double,
		outlineColor: Int,
		fillColor: Int,
		outline: Boolean,
		fill: Boolean,
		width: Float,
		depth: WorldDepth
	) {
		val bounds = entity.boundingBox
		val lag = 1f - partialTick
		val offsetX = (entity.xo - entity.x) * lag
		val offsetY = (entity.yo - entity.y) * lag
		val offsetZ = (entity.zo - entity.z) * lag
		val minX = bounds.minX + offsetX - inflate
		val minY = bounds.minY + offsetY - inflate
		val minZ = bounds.minZ + offsetZ - inflate
		val maxX = bounds.maxX + offsetX + inflate
		val maxY = bounds.maxY + offsetY + inflate
		val maxZ = bounds.maxZ + offsetZ + inflate
		if (fill) WorldDraw.drawFilledBox(event, minX, minY, minZ, maxX, maxY, maxZ, fillColor, depth)
		if (outline) WorldDraw.drawWireBox(event, minX, minY, minZ, maxX, maxY, maxZ, outlineColor, width, depth)
	}

	private fun openBoxFeed(): Array<Handle> {
		val eventBus = bus ?: return emptyArray()
		return arrayOf(
			eventBus.subscribe<ClientTickEvent.End>(AFTER_PRODUCERS) { scan() },
			eventBus.subscribe<WorldRenderEvent>(handler = ::render),
			eventBus.subscribe<WorldChangeEvent> { forget() }
		)
	}

	private fun releaseBoxes(registration: BoxRule) {
		synchronized(this) {
			boxRules = boxRules.filter { it !== registration }.toTypedArray()
			if (boxRules.isNotEmpty()) return
			for (handle in boxHandles) handle.unsubscribe()
			boxHandles = emptyArray()
			forget()
		}
	}

	private fun releaseGlow(rule: Rule) {
		synchronized(this) {
			glowRules = glowRules.filter { it !== rule }.toTypedArray()
			if (glowRules.isNotEmpty()) return
			glowHandle?.unsubscribe()
			glowHandle = null
		}
	}

	private fun forget() {
		matched.fill(null)
		matchedStyles.fill(null)
		matchedCount = 0
	}

	private fun paintGlow(event: EntityGlowEvent) {
		val rules = glowRules
		val entity = event.entity
		for (rule in rules) {
			val color = rule.colorOf(entity)
			if (color == NO_HIGHLIGHT) continue
			event.glowing = true
			event.color = color
			return
		}
	}

	private fun scan() {
		matchedCount = 0
		val rules = boxRules
		if (rules.isEmpty()) return
		val level = Minecraft.getInstance().level ?: return
		for (entity in level.entitiesForRendering()) {
			if (!entity.isAlive) continue
			for (rule in rules) {
				val color = rule.paint.colorOf(entity)
				if (color == NO_HIGHLIGHT) continue
				remember(entity, color, rule.style())
				break
			}
		}
	}

	private fun remember(entity: Entity, color: Int, style: HighlightStyle) {
		if (matchedCount == matched.size) {
			matched = matched.copyOf(matchedCount * 2)
			matchedColors = matchedColors.copyOf(matchedCount * 2)
			matchedStyles = matchedStyles.copyOf(matchedCount * 2)
		}
		matched[matchedCount] = entity
		matchedColors[matchedCount] = color
		matchedStyles[matchedCount] = style
		matchedCount++
	}

	private fun render(event: WorldRenderEvent) {
		if (matchedCount == 0) return
		val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
		var index = 0
		while (index < matchedCount) {
			val entity = matched[index]
			val style = matchedStyles[index]
			if (entity != null && style != null && entity.isAlive) {
				drawStyled(event, entity, matchedColors[index], style, partialTick)
			}
			index++
		}
	}

	private fun drawStyled(
		event: WorldRenderEvent,
		entity: Entity,
		color: Int,
		style: HighlightStyle,
		partialTick: Float
	) {
		when (style) {
			HighlightStyle.FILLED ->
				drawEntityBox(event, entity, partialTick, 0.0, color, color, false, true, LINE_WIDTH, WorldDepth.TESTED)
			HighlightStyle.OUTLINE ->
				drawEntityBox(event, entity, partialTick, 0.0, color, color, true, false, LINE_WIDTH, WorldDepth.TESTED)
			HighlightStyle.FILLED_OUTLINE -> {
				drawEntityBox(
					event,
					entity,
					partialTick,
					FILL_INSET_UNDER_OUTLINE,
					color,
					ARGB.multiplyAlpha(color, FILL_ALPHA_UNDER_OUTLINE),
					false,
					true,
					LINE_WIDTH,
					WorldDepth.TESTED
				)
				drawEntityBox(event, entity, partialTick, 0.0, color, color, true, false, LINE_WIDTH, WorldDepth.TESTED)
			}
		}
	}

	private const val LINE_WIDTH = 3f
}
