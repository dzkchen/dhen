package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.NO_HIGHLIGHT
import io.github.dzkchen.dhen.util.Color
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.TransparentBlock
import kotlin.math.sqrt

object ItemGlow : Module(
	name = "Item Glow",
	category = Category.VISUAL,
	description = "Outlines dropped items in their rarity colour and hides coin piles too small to bother with."
) {
	internal val rarityColorsSetting = BooleanSetting(
		"Rarity Colors",
		default = true,
		description = "Paints each drop in its rarity colour instead of one colour for everything."
	)
	internal val glowColorSetting = ColorSetting(
		"Glow Color",
		Color.rgba(255, 153, 0),
		description = "The colour used for drops whose rarity Dhen cannot read."
	)
	internal val throughWallsSetting = BooleanSetting(
		"Through Walls",
		default = true,
		description = "Keeps the outline visible when the drop is behind a block."
	)
	internal val hideCheapCoinsSetting = BooleanSetting(
		"Hide Cheap Coins",
		description = "Stops drawing coin piles worth less than the amount below."
	)
	internal val coinThresholdSetting = NumberSetting(
		"Coin Threshold",
		default = 1000.0,
		min = 0.0,
		max = 100000.0,
		step = 100.0,
		description = "The smallest coin pile still worth drawing."
	).withDependency { hideCheapCoinsSetting.on }

	private val tiers = Reference2IntOpenHashMap<Entity>()
	private val waiting = Reference2IntOpenHashMap<Entity>()
	private val coins = Reference2IntOpenHashMap<Entity>()
	private val visible = ReferenceOpenHashSet<Entity>()
	private val tierColors = IntArray(ItemRarity.entries.size)
	private var painted = false
	private var paintedFallback = 0
	private var paintedByRarity = false
	private var ticks = 0
	private var rule: Handle? = null

	init {
		registerSetting(rarityColorsSetting)
		registerSetting(glowColorSetting)
		registerSetting(throughWallsSetting)
		registerSetting(hideCheapCoinsSetting)
		registerSetting(coinThresholdSetting)
		on<ClientTickEvent.End> { sweep() }
		on<WorldChangeEvent> { forget() }
		on<EntityRenderEvent> { if (hidesCoin(it.entity)) it.cancelled = true }
	}

	override fun onEnabled() {
		tiers.defaultReturnValue(EXCLUDED)
		waiting.defaultReturnValue(0)
		coins.defaultReturnValue(NOT_COINS)
		painted = false
		repaint()
		rule = EntityHighlights.glow(::paint)
	}

	override fun onDisabled() {
		rule?.unsubscribe()
		rule = null
		forget()
	}

	private fun paint(entity: Entity): Int {
		val tier = tiers.getInt(entity)
		if (tier == EXCLUDED) return NO_HIGHLIGHT
		if (!throughWallsSetting.on && entity !in visible) return NO_HIGHLIGHT
		return tierColors[tier - 1]
	}

	private fun hidesCoin(entity: Entity): Boolean {
		if (!hideCheapCoinsSetting.on) return false
		val amount = coins.getInt(entity)
		return amount != NOT_COINS && amount < coinThresholdSetting.amount
	}

	private fun forget() {
		tiers.clear()
		waiting.clear()
		coins.clear()
		visible.clear()
		ticks = 0
	}

	private fun sweep() {
		if (!SkyBlockLocation.inSkyBlock) {
			if (!tiers.isEmpty()) forget()
			return
		}
		val level = Minecraft.getInstance().level ?: return
		ticks++
		if (ticks % PRUNE_INTERVAL_TICKS == 0) prune()
		repaint()
		for (entity in level.entitiesForRendering()) {
			if (entity !is ItemEntity || !entity.isAlive || tiers.containsKey(entity)) continue
			resolve(entity)
		}
		if (throughWallsSetting.on) return
		val player = Minecraft.getInstance().player ?: return
		if (ticks % SIGHT_INTERVAL_TICKS == 0) rebuildVisible(player)
	}

	private fun prune() {
		tiers.keys.removeIf { !it.isAlive }
		waiting.keys.removeIf { !it.isAlive }
		coins.keys.removeIf { !it.isAlive }
	}

	private fun repaint() {
		val fallback = glowColorSetting.value.argb
		val byRarity = rarityColorsSetting.on
		if (painted && fallback == paintedFallback && byRarity == paintedByRarity) return
		painted = true
		paintedFallback = fallback
		paintedByRarity = byRarity
		for (rarity in ItemRarity.entries) {
			tierColors[rarity.ordinal] =
				if (byRarity && rarity != ItemRarity.NONE) legacyColor(rarity.baseColor) else fallback
		}
	}

	private fun resolve(item: ItemEntity) {
		if (!coins.containsKey(item)) {
			val amount = coinAmount(item)
			if (amount != NOT_COINS) coins.put(item, amount)
		}
		val waited = waiting.getInt(item)
		val rarity = ItemRarity.of(item.item)
		if (rarity == ItemRarity.NONE && waited < LORE_WAIT_TICKS) {
			waiting.put(item, waited + 1)
			return
		}
		waiting.removeInt(item)
		if (isShowcase(item)) {
			tiers.put(item, EXCLUDED)
			return
		}
		tiers.put(item, rarity.ordinal + 1)
	}

	private fun isShowcase(item: ItemEntity): Boolean {
		val stands = item.level().getEntitiesOfClass(ArmorStand::class.java, item.boundingBox.inflate(SHOWCASE_RADIUS))
		for (stand in stands) {
			val block = (stand.getItemBySlot(EquipmentSlot.HEAD).item as? BlockItem)?.block ?: continue
			if (block is TransparentBlock) return true
		}
		return false
	}

	private fun coinAmount(item: ItemEntity): Int {
		val stack = item.item
		if (!stack.`is`(Items.PLAYER_HEAD)) return NOT_COINS
		val match = COIN_NAME.matchEntire(withoutCodes(stack.hoverName.string).trim()) ?: return NOT_COINS
		return match.groupValues[AMOUNT_GROUP].replace(",", "").toIntOrNull() ?: NOT_COINS
	}

	private fun rebuildVisible(player: Player) {
		visible.clear()
		val eye = player.eyePosition
		val look = player.lookAngle
		for (entity in tiers.keys) {
			if (tiers.getInt(entity) == EXCLUDED) continue
			val toX = entity.x - eye.x
			val toY = entity.y + entity.bbHeight * HALF - eye.y
			val toZ = entity.z - eye.z
			val span = sqrt(toX * toX + toY * toY + toZ * toZ)
			if (span == 0.0) continue
			val facing = (look.x * toX + look.y * toY + look.z * toZ) / span
			if (facing > LOOK_DOT_THRESHOLD && player.hasLineOfSight(entity)) visible.add(entity)
		}
	}

	private const val EXCLUDED = 0
	private const val NOT_COINS = -1
	private const val SHOWCASE_RADIUS = 1.5
	private const val LOOK_DOT_THRESHOLD = 0.15
	private const val SIGHT_INTERVAL_TICKS = 10
	private const val PRUNE_INTERVAL_TICKS = 20
	private const val LORE_WAIT_TICKS = 4
	private const val AMOUNT_GROUP = 1
	private const val HALF = 0.5

	private val COIN_NAME = Regex("""([\d,]+) Coins?""")
}
