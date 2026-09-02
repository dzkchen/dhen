package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ActionBarEvent
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.InteractionEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.features.visual.Mask
import io.github.dzkchen.dhen.features.visual.MaskTimers
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.gui.slotText
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.ARGB
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

object ItemAbilities : Module(
	name = "Item Abilities",
	category = Category.INVENTORY,
	description = "Counts every item ability's cooldown down on its own slot, and paints the mask death-save " +
		"cooldowns on the mask itself."
) {
	@Volatile
	var mageCooldownMultiplier: Double = 1.0

	internal val backgroundSetting = BooleanSetting(
		"Cooldown Background",
		description = "Tints the whole slot with the cooldown colour, not just the number."
	)

	internal val readySetting = BooleanSetting(
		"Show When Ready",
		default = true,
		description = "Marks a ready ability with a green R while no menu is open."
	)

	internal val readyAlertSetting = BooleanSetting(
		"Ready Notification",
		description = "Shows a title the moment the chat says an ability is available again."
	)

	internal val readySoundSetting = BooleanSetting(
		"Ready Sound",
		description = "Plays a note with that title."
	).withDependency { readyAlertSetting.on }

	internal val depletedMasksSetting = BooleanSetting(
		"Depleted Bonzo's Masks",
		description = "Washes a spent Bonzo's or Spirit Mask red, fading to green as it comes back."
	)

	internal val maskOnItemSetting = BooleanSetting(
		"Mask Cooldown On Item",
		default = true,
		description = "Paints the Bonzo, Spirit and Phoenix death-save cooldowns onto their own inventory slot."
	)

	internal val maskDurabilitySetting = BooleanSetting(
		"Mask As Durability",
		description = "Draws that cooldown as a durability bar instead of a shade rising over the icon."
	).withDependency { maskOnItemSetting.on }

	internal val phoenixSetting = BooleanSetting(
		"Phoenix As Top Left",
		description = "A pet has no slot, so its cooldown goes on the top-left slot of your own inventory."
	).withDependency { maskOnItemSetting.on }

	internal val maskColorSetting = ColorSetting(
		"Mask Cooldown Color",
		Color.rgba(MASK_GREY, MASK_GREY, MASK_GREY),
		allowAlpha = true,
		description = "The colour of that bar or shade."
	).withDependency { maskOnItemSetting.on }

	private var backgroundShown by backgroundSetting
	private var readyShown by readySetting
	private var readyAlertShown by readyAlertSetting
	private var readySoundOn by readySoundSetting
	private var depletedMasks by depletedMasksSetting
	private var maskOnItem by maskOnItemSetting
	private var maskAsDurability by maskDurabilitySetting
	private var phoenixTopLeft by phoenixSetting
	private var maskColor by maskColorSetting

	private val containerAbilities = AbilityMemo(CONTAINER_SLOTS)
	private val hotbarAbilities = AbilityMemo(HOTBAR_MEMO_SLOTS)
	private val builder = StringBuilder(TEXT_CAPACITY)
	private val alignedOthers = Pattern.compile("You aligned .* other players?!").matcher("")
	private val buffedYourself =
		Pattern.compile("You buffed yourself for \\+\\d+" + STRENGTH_ICON + " Strength").matcher("")
	private val abilityUse = Pattern.compile("-\\d+ Mana \\((?<type>[^)]*)\\)").matcher("")
	private val abilityReadyLine = Pattern.compile("[a-zA-Z0-9 ]+ is now available!").matcher("")
	private var lastAbility = ""

	init {
		on<PacketReceiveEvent.Pre> { event ->
			when (val packet = event.packet) {
				is ClientboundSoundPacket -> sounded(packet)
				is ClientboundSoundEntityPacket -> sounded(packet)
			}
		}
		on<InteractionEvent.UseItem> { clicked(it.item) }
		on<InteractionEvent.UseBlock> { clicked(it.item) }
		on<InteractionEvent.UseEntity> { clicked(it.item) }
		on<ChatReceiveEvent> { chatted(it.stripped, Util.getMillis()) }
		on<ActionBarEvent> { actionBarred(it.stripped, Util.getMillis()) }
		on<ClientTickEvent.End> { ticked() }
		on<WorldChangeEvent> { forget() }
		on<SlotRenderEvent.Pre> { tinted(it) }
		on<SlotRenderEvent.Post> { labelled(it) }
	}

	override fun onDisabled() = forget()

	override fun onReset() = forget()

	internal fun forget() {
		ItemAbility.forgetAll()
		containerAbilities.forget()
		hotbarAbilities.forget()
		lastAbility = ""
	}

	fun tintHotbarSlot(stack: ItemStack) {
		if (!enabled || !SkyBlockLocation.inSkyBlock) return
		try {
			val slot = hotbarSlotOf(stack)
			val ability = hotbarAbilities.of(slot, stack) ?: return
			val tint = tintOf(ability, screenOpen())
			if (tint != 0) SlotTint.claim(tint, TINT_PRIORITY)
		} catch (throwable: Throwable) {
			reportError(throwable)
		}
	}

	@JvmStatic
	fun labelHotbarSlot(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
		if (!enabled || !SkyBlockLocation.inSkyBlock) return
		try {
			val slot = hotbarSlotOf(stack)
			val ability = hotbarAbilities.of(slot, stack) ?: return
			val open = screenOpen()
			label(graphics, ability, x, y, open)
			hotbarAbilities.other(slot)?.let { label(graphics, it, x, y, open) }
		} catch (throwable: Throwable) {
			reportError(throwable)
		}
	}

	internal fun sounded(packet: ClientboundSoundPacket) {
		if (!SkyBlockLocation.inSkyBlock) return
		heard(packet.sound.value().location().path, packet.pitch, packet.volume, Util.getMillis())
	}

	internal fun sounded(packet: ClientboundSoundEntityPacket) {
		if (!SkyBlockLocation.inSkyBlock) return
		heard(packet.sound.value().location().path, packet.pitch, packet.volume, Util.getMillis())
	}

	internal fun heard(path: String, pitch: Float, volume: Float, now: Long, held: SkyBlockItem = heldItem()) {
		when {
			path == "entity.zombie_villager.cure" && pitch == 0.6984127f && volume == 1f ->
				witherScrolls(ItemAbility.scrollsOf(held), now)

			path == "block.lava.extinguish" && pitch == 0.4920635f && volume == 1f ->
				if (ItemAbility.scrollsOf(held) and SCROLL_WARP != 0) ItemAbility.SHADOW_WARP_SCROLL.sound(now)

			path == "block.lava.pop" && pitch == 1f && volume == 1f -> ItemAbility.FIRE_FURY_STAFF.sound(now)

			path == "entity.ender_dragon.growl" && pitch == 1f && volume == 1f -> ItemAbility.ICE_SPRAY_WAND.sound(now)

			path == "entity.enderman.teleport" -> {
				if (pitch == 0.61904764f && volume == 1f) ItemAbility.GYROKINETIC_WAND_LEFT.sound(now)
				val id = held.id
				if (id == "SHADOW_FURY" || id == "STARRED_SHADOW_FURY") ItemAbility.SHADOW_FURY.sound(now)
			}

			path == "block.anvil.land" && pitch == 0.4920635f && volume == 0.5f -> ItemAbility.GIANTS_SWORD.sound(now)

			path == "entity.ghast.ambient" && pitch == 0.4920635f && volume == 0.15f ->
				ItemAbility.ATOMSPLIT_KATANA.sound(now)

			path == "block.lava.pop" && pitch == 0.7619048f && volume == 0.15f ->
				ItemAbility.WAND_OF_ATONEMENT.sound(now)

			path == "entity.bat.hurt" && volume == 0.1f -> ItemAbility.STARLIGHT_WAND.sound(now)

			path == "entity.ghast.hurt" && volume == 1f && pitch >= 1.6f && pitch <= 1.7f -> when {
				ItemAbility.VOODOO_DOLL.recentlyHeld(now) -> ItemAbility.VOODOO_DOLL.sound(now)
				ItemAbility.VOODOO_DOLL_WILTED.recentlyHeld(now) -> ItemAbility.VOODOO_DOLL_WILTED.sound(now)
			}

			path == "entity.generic.explode" -> {
				if (pitch == 1f && volume == 1f && ItemAbility.scrollsOf(held) and SCROLL_IMPLOSION != 0) {
					ItemAbility.IMPLOSION_SCROLL.sound(now)
				}
				if (pitch == 4.047619f && volume == 0.2f) ItemAbility.GOLEM_SWORD.sound(now)
				if (pitch == 0.4920635f && volume == 0.5f) ItemAbility.STAFF_OF_THE_VOLCANO.sound(now)
			}

			path == "entity.wolf.death" && volume == 0.5f -> {
				if (ItemAbility.WEIRD_TUBA.recentlyHeld(now)) ItemAbility.WEIRD_TUBA.sound(now)
				if (ItemAbility.WEIRDER_TUBA.recentlyHeld(now)) ItemAbility.WEIRDER_TUBA.sound(now)
			}

			path == "entity.zombie_villager.converted" && pitch == 2f && volume == 0.3f ->
				ItemAbility.END_STONE_SWORD.sound(now)

			path == "entity.wolf.pant" && pitch == 1.3968254f && volume == 0.4f -> ItemAbility.SOUL_ESOWARD.sound(now)

			path == "entity.piglin.angry" && pitch == 2f && volume == 0.3f -> ItemAbility.PIGMAN_SWORD.sound(now)

			path == "entity.ghast.shoot" && pitch == 1f && volume == 0.3f -> ItemAbility.EMBER_ROD.sound(now)

			path == "entity.elder_guardian.ambient" && pitch == 2f && volume == 0.2f ->
				ItemAbility.FIRE_FREEZE_STAFF.sound(now)

			path == "entity.generic.eat" && pitch == 1f && volume == 1f -> ItemAbility.STAFF_OF_THE_VOLCANO.sound(now)

			path == "entity.generic.drink" && Math.round(pitch * TENTHS) / TENTHS == 1.8f && volume == 1f ->
				ItemAbility.HOLY_ICE.sound(now)

			path == "entity.bat.ambient" && pitch == 0.4920635f && volume == 1f -> ItemAbility.ROYAL_PIGEON.sound(now)

			path == "entity.generic.eat" && pitch == 0.4920635f && volume == 1f -> ItemAbility.WAND_OF_STRENGTH.sound(now)

			path == "item.flintandsteel.use" && pitch == 0.74603176f && volume == 1f ->
				ItemAbility.TACTICAL_INSERTION.activate(now, ChatFormatting.DARK_PURPLE, TACTICAL_ARMED_MILLIS)

			path == "entity.zombie_villager.cure" && pitch == 1.8888888f && volume == 0.7f ->
				ItemAbility.TACTICAL_INSERTION.activate(now, null, TACTICAL_SPENT_MILLIS)

			path == "block.lever.click" && pitch == 0.84126985f && volume == 0.5f ->
				if (ItemAbility.TOTEM_OF_CORRUPTION.recentlyHeld(now)) ItemAbility.TOTEM_OF_CORRUPTION.sound(now)

			path == "entity.ender_dragon.growl" && pitch == 0.4920635f && volume == 2f -> ItemAbility.ENRAGER.sound(now)

			path == "entity.firework_rocket.launch" && pitch == 1f && volume == 3f -> {
				if (ItemAbility.ALERT_FLARE.recentlyHeld(now)) ItemAbility.ALERT_FLARE.sound(now)
				if (ItemAbility.SOS_FLARE.recentlyHeld(now)) ItemAbility.SOS_FLARE.sound(now)
			}
		}
	}

	private fun witherScrolls(scrolls: Int, now: Long) {
		if (scrolls == SCROLL_ALL) {
			ItemAbility.WITHER_IMPACT.sound(now)
			return
		}
		if (scrolls and SCROLL_SHIELD != 0) {
			ItemAbility.WITHER_SHIELD_SCROLL.activate(now, null, SHIELD_SOLO_MILLIS)
			ItemAbility.WITHER_SHIELD_SCROLL.sound(now)
		}
		if (scrolls and SCROLL_WARP != 0) ItemAbility.SHADOW_WARP_SCROLL.sound(now)
		if (scrolls and SCROLL_IMPLOSION != 0) ItemAbility.IMPLOSION_SCROLL.sound(now)
	}

	private fun clicked(stack: ItemStack) {
		if (!SkyBlockLocation.inSkyBlock || stack.isEmpty) return
		clicked(SkyBlockItems.of(stack), Util.getMillis())
	}

	internal fun clicked(item: SkyBlockItem, now: Long) {
		ItemAbility.byId(item.id)?.lastItemClick = now
		val scrolls = ItemAbility.scrollsOf(item)
		if (scrolls == SCROLL_ALL) {
			ItemAbility.WITHER_IMPACT.lastItemClick = now
			return
		}
		if (scrolls and SCROLL_SHIELD != 0) ItemAbility.WITHER_SHIELD_SCROLL.lastItemClick = now
		if (scrolls and SCROLL_WARP != 0) ItemAbility.SHADOW_WARP_SCROLL.lastItemClick = now
		if (scrolls and SCROLL_IMPLOSION != 0) ItemAbility.IMPLOSION_SCROLL.lastItemClick = now
	}

	internal fun chatted(line: String, now: Long) {
		if (!SkyBlockLocation.inSkyBlock) return
		when {
			line == VEIL_ON -> ItemAbility.WITHER_CLOAK.activate(now, ChatFormatting.LIGHT_PURPLE)
			line == VEIL_EXPIRED || line == VEIL_NO_MANA -> ItemAbility.WITHER_CLOAK.activate(now)
			line == VEIL_OFF -> ItemAbility.WITHER_CLOAK.activate(now, null, VEIL_OFF_MILLIS)
			line == ALIGNED_SELF || alignedOthers.reset(line).matches() ->
				ItemAbility.GYROKINETIC_WAND_RIGHT.activate(now, ChatFormatting.BLUE, GYRO_ALIGNED_MILLIS)

			line == RAGNAROCK_CANCELLED -> ItemAbility.RAGNAROCK_AXE.activate(now, null, RAGNAROCK_CANCEL_MILLIS)
			buffedYourself.reset(line).matches() -> ItemAbility.SWORD_OF_BAD_HEALTH.activate(now)
		}
		if (readyAlertShown && abilityReady(line)) {
			DhenAlert.show(line, sound = if (readySoundOn) SoundEvents.NOTE_BLOCK_PLING.value() else null)
		}
	}

	internal fun abilityReady(line: String): Boolean = abilityReadyLine.reset(line).matches()

	internal fun actionBarred(line: String, now: Long) {
		if (!SkyBlockLocation.inSkyBlock) return
		if (abilityUse.reset(line).find()) {
			val used = abilityUse.group(ABILITY_GROUP)
			if (used != lastAbility) {
				lastAbility = used
				ItemAbility.byAbilityName(used)?.activate(now)
			}
		} else {
			lastAbility = ""
		}
		when {
			line.contains(CASTING_IN) -> if (!ItemAbility.RAGNAROCK_AXE.isOnCooldown(now)) {
				ItemAbility.RAGNAROCK_AXE.activate(now, ChatFormatting.WHITE, RAGNAROCK_CAST_MILLIS)
			}

			line.contains(CASTING) -> if (ItemAbility.RAGNAROCK_AXE.specialColor != ChatFormatting.DARK_PURPLE) {
				ItemAbility.RAGNAROCK_AXE.activate(now, ChatFormatting.DARK_PURPLE, RAGNAROCK_CHANNEL_MILLIS)
			}

			line.contains(CANCELLED) -> ItemAbility.RAGNAROCK_AXE.activate(now, null, RAGNAROCK_CANCEL_MILLIS)
		}
	}

	private fun ticked() {
		if (!SkyBlockLocation.inSkyBlock) return
		val now = Util.getMillis()
		val held = Minecraft.getInstance()?.player?.mainHandItem
		if (held != null && !held.isEmpty) ItemAbility.byId(SkyBlockItems.of(held).id)?.lastHeld = now
		refresh(now)
	}

	internal fun refresh(now: Long) {
		val ready = if (readyShown) READY_LABEL else ""
		val table = ItemAbility.table
		for (index in table.indices) table[index].refresh(now, ready, builder)
	}

	private fun tinted(event: SlotRenderEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		depleted(slot.item)
		val ability = containerAbilities.of(slot.index, slot.item) ?: return
		val tint = tintOf(ability, true)
		if (tint != 0) SlotTint.claim(tint, TINT_PRIORITY)
	}

	private fun labelled(event: SlotRenderEvent.Post) {
		if (!SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		masked(event.graphics, slot)
		val ability = containerAbilities.of(slot.index, slot.item) ?: return
		label(event.graphics, ability, slot.x, slot.y, true)
		containerAbilities.other(slot.index)?.let { label(event.graphics, it, slot.x, slot.y, true) }
	}

	internal fun depleted(stack: ItemStack) {
		if (!depletedMasks || !MaskTimers.enabled) return
		val mask = deathSaveMask(stack) ?: return
		val left = mask.cooldownLeft
		if (left <= 0) return
		val hue = GREEN_HUE * (1f - left.toFloat() / mask.cooldownTicks)
		SlotTint.claim(Color.hsv(hue, 1f, 1f).argb, DEPLETED_PRIORITY)
	}

	private fun masked(graphics: GuiGraphicsExtractor, slot: Slot) {
		if (!maskOnItem || !MaskTimers.enabled) return
		val mask = maskOf(slot) ?: return
		val fraction = mask.cooldownLeft.toDouble() / mask.cooldownTicks
		if (fraction < 0.0 || fraction > 1.0) return
		val ink = maskColor.argb
		if (maskAsDurability && fraction > 0.0) {
			SharpGui.fill(
				graphics,
				slot.x + BAR_INSET,
				slot.y + BAR_TOP,
				slot.x + SLOT_BOX - BAR_INSET,
				slot.y + BAR_TOP + BAR_HEIGHT,
				legacyColor(ChatFormatting.BLACK)
			)
			SharpGui.fill(
				graphics,
				slot.x + BAR_INSET,
				slot.y + BAR_TOP,
				slot.x + SLOT_BOX - BAR_INSET - ((1.0 - fraction) * BAR_WIDTH).toInt(),
				slot.y + BAR_TOP + 1,
				ink
			)
			return
		}
		SharpGui.fill(
			graphics,
			slot.x,
			slot.y + ((1.0 - fraction) * SLOT_BOX).toInt(),
			slot.x + SLOT_BOX,
			slot.y + SLOT_BOX,
			ink
		)
	}

	private fun maskOf(slot: Slot): Mask? = deathSaveMask(slot.item)
		?: if (phoenixTopLeft && slot.containerSlot == PHOENIX_SLOT && slot.container is Inventory) {
			Mask.PHOENIX
		} else {
			null
		}

	internal fun deathSaveMask(stack: ItemStack): Mask? = when (SkyBlockItems.of(stack).id) {
		"BONZO_MASK", "STARRED_BONZO_MASK" -> Mask.BONZO
		"SPIRIT_MASK", "STARRED_SPIRIT_MASK" -> Mask.SPIRIT
		else -> null
	}

	internal fun tintOf(ability: ItemAbility, screenOpen: Boolean): Int {
		if (!backgroundShown) return 0
		if (screenOpen && !ability.onCooldown) return 0
		if (ability.onCooldown) return ARGB.color(COOLDOWN_OPACITY, ability.ink)
		if (!readyShown) return 0
		return ARGB.color(READY_OPACITY, ability.ink)
	}

	private fun label(graphics: GuiGraphicsExtractor, ability: ItemAbility, x: Int, y: Int, screenOpen: Boolean) {
		if (screenOpen && !ability.onCooldown) return
		val text = ability.label
		if (text.isEmpty()) return
		val shifted = ability.alternativePosition
		slotText(
			graphics,
			text,
			x + TEXT_RIGHT + (if (shifted) ALTERNATIVE_X else 0),
			y + TEXT_TOP + (if (shifted) ALTERNATIVE_Y else 0),
			TEXT_SCALE,
			ability.ink,
			ability.textMemo
		)
	}

	private fun hotbarSlotOf(stack: ItemStack): Int {
		if (stack.isEmpty) return -1
		val player = Minecraft.getInstance()?.player ?: return -1
		val inventory = player.inventory
		for (index in 0 until HOTBAR_SLOTS) if (inventory.getItem(index) === stack) return index
		return if (player.offhandItem === stack) OFFHAND_SLOT else -1
	}

	private fun heldItem(): SkyBlockItem {
		val stack = Minecraft.getInstance()?.player?.mainHandItem ?: return SkyBlockItem.NONE
		return SkyBlockItems.of(stack)
	}

	private fun screenOpen(): Boolean = Minecraft.getInstance()?.gui?.screen() != null

	private const val TINT_PRIORITY = 20
	private const val DEPLETED_PRIORITY = 5
	private const val GREEN_HUE = 1f / 3f
	private const val TEXT_RIGHT = 17
	private const val TEXT_TOP = 9
	private const val TEXT_SCALE = 1f
	private const val ALTERNATIVE_X = -8
	private const val ALTERNATIVE_Y = -10
	private const val COOLDOWN_OPACITY = 130
	private const val READY_OPACITY = 80
	private const val READY_LABEL = "R"
	private const val CONTAINER_SLOTS = 128
	private const val HOTBAR_SLOTS = 9
	private const val OFFHAND_SLOT = 9
	private const val HOTBAR_MEMO_SLOTS = 10
	private const val TEXT_CAPACITY = 8
	private const val TENTHS = 10f
	private const val MASK_GREY = 38
	private const val PHOENIX_SLOT = 9
	private const val BAR_INSET = 2
	private const val BAR_TOP = 13
	private const val BAR_HEIGHT = 2
	private const val BAR_WIDTH = 12
	private const val ABILITY_GROUP = "type"
	private const val STRENGTH_ICON = "\uE00D"
	private const val SHIELD_SOLO_MILLIS = 5_000L
	private const val VEIL_OFF_MILLIS = 5_000L
	private const val GYRO_ALIGNED_MILLIS = 6_000L
	private const val RAGNAROCK_CAST_MILLIS = 3_000L
	private const val RAGNAROCK_CHANNEL_MILLIS = 10_000L
	private const val RAGNAROCK_CANCEL_MILLIS = 17_000L
	private const val TACTICAL_ARMED_MILLIS = 3_000L
	private const val TACTICAL_SPENT_MILLIS = 17_000L
	private const val VEIL_ON = "Creeper Veil Activated!"
	private const val VEIL_OFF = "Creeper Veil De-activated!"
	private const val VEIL_EXPIRED = "Creeper Veil De-activated! (Expired)"
	private const val VEIL_NO_MANA = "Not enough mana! Creeper Veil De-activated!"
	private const val ALIGNED_SELF = "You aligned yourself!"
	private const val RAGNAROCK_CANCELLED = "Ragnarock was cancelled due to being hit!"
	private const val CASTING_IN = "CASTING IN "
	private const val CASTING = "CASTING"
	private const val CANCELLED = "CANCELLED"
}

internal class AbilityMemo(slots: Int) {
	private val items = SkyBlockItems.memo(slots)
	private val keys = arrayOfNulls<SkyBlockItem>(slots)
	private val first = arrayOfNulls<ItemAbility>(slots)
	private val second = arrayOfNulls<ItemAbility>(slots)

	fun of(slot: Int, stack: ItemStack): ItemAbility? {
		if (slot < 0 || slot >= keys.size) return null
		if (stack.isEmpty) {
			store(slot, null, null, null)
			return null
		}
		val item = items.of(slot, stack)
		if (keys[slot] === item) return first[slot]
		if (item.id.isEmpty()) {
			store(slot, item, null, null)
			return null
		}
		val displayName = stack.hoverName.string
		val found = ItemAbility.of(item, displayName)
		store(slot, item, found, if (found == null) null else ItemAbility.of(item, displayName, found))
		return found
	}

	fun other(slot: Int): ItemAbility? = if (slot < 0 || slot >= keys.size) null else second[slot]

	fun forget() {
		keys.fill(null)
		first.fill(null)
		second.fill(null)
	}

	private fun store(slot: Int, item: SkyBlockItem?, found: ItemAbility?, paired: ItemAbility?) {
		keys[slot] = item
		first[slot] = found
		second[slot] = paired
	}
}
