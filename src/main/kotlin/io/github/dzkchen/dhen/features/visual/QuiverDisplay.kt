package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.quiver.QuiverArrow
import io.github.dzkchen.dhen.data.quiver.QuiverState
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoItem
import io.github.dzkchen.dhen.data.repo.RepoState
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.QuiverUpdateEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.grouped
import kotlinx.coroutines.delay
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object QuiverDisplay : Module(
	name = "Quiver Display",
	category = Category.VISUAL,
	description = "Shows the selected SkyBlock quiver arrow and remaining amount."
) {
	internal const val ALWAYS = "Always"
	internal const val BOW_IN_INVENTORY = "Bow in inventory"
	internal const val BOW_IN_HAND = "Bow in hand"

	internal val showIconSetting = BooleanSetting("Show Arrow Icon", true)
	internal val showWhenSetting = SelectorSetting(
		"Show When",
		BOW_IN_HAND,
		listOf(ALWAYS, BOW_IN_INVENTORY, BOW_IN_HAND)
	)
	internal val lowQuiverSetting = BooleanSetting(
		"Low Quiver Alert",
		true,
		"Notifies you when the selected arrow reaches the configured amount."
	)
	internal val reminderAfterRunSetting = BooleanSetting(
		"Reminder After Run",
		true,
		"Reminds you about low arrows after a Dungeon or Kuudra run."
	)
	internal val lowQuiverAmountSetting = NumberSetting(
		"Low Quiver Amount",
		100.0,
		50.0,
		500.0,
		50.0,
		"Amount at which to notify you."
	)
	private var showIcon by showIconSetting
	private var showWhen by showWhenSetting
	private var lowQuiver by lowQuiverSetting
	private var reminderAfterRun by reminderAfterRunSetting
	private var lowQuiverAmount by lowQuiverAmountSetting
	internal val equipment = QuiverEquipment()
	internal val element = hud(QuiverDisplayElement())
	internal val warning = QuiverWarning(NanoClock.SYSTEM, QuiverState::amount)
	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private var ticks = 0

	init {
		on<QuiverUpdateEvent> {
			if (
				warning.updated(
					it.arrow,
					it.amount,
					inInstance(),
					lowQuiverSetting.on && !QuiverState.infiniteArrows,
					lowQuiverAmountSetting.amount.toInt()
				)
			) {
				lowQuiverAlert(it.amount)
			}
			element.refresh()
		}
		on<ClientTickEvent.End> { ticked() }
		on<WorldChangeEvent> { warning.reset() }
		on<IslandChangeEvent> { if (it.resetsWorldState) warning.reset() }
	}

	override fun onEnabled() {
		repoHold.ensure()
		element.refresh()
	}

	override fun onDisabled() {
		repoHold.release()
		ticks = 0
		equipment.clear()
		warning.reset()
		element.refresh()
	}

	override fun onReset() = element.refresh()

	internal fun ticked() {
		val reminder = warning.takeReminder()
		if (reminder != null) instanceAlert(reminder)
		repoHold.ensure()
		element.refresh()
		if (!SkyBlockLocation.inSkyBlock) {
			samplingDue(false)
			if (equipment.clear()) element.refresh()
			return
		}
		if (!samplingDue(true)) return
		val player = Minecraft.getInstance().player
		if (player == null) {
			if (equipment.clear()) element.refresh()
			return
		}
		val inventory = player.inventory
		if (
			equipment.sample(inventory.nonEquipmentItems, player.mainHandItem)
		) {
			element.refresh()
		}
	}

	internal fun samplingDue(inSkyBlock: Boolean): Boolean {
		if (!inSkyBlock) {
			ticks = 0
			return false
		}
		ticks++
		if (ticks < SAMPLE_TICKS) return false
		ticks = 0
		return true
	}

	internal fun shows(mode: String, inSkyBlock: Boolean, editing: Boolean): Boolean =
		editing || inSkyBlock && when (mode) {
			ALWAYS -> true
			BOW_IN_INVENTORY -> equipment.hasBow
			else -> equipment.holdingBow
		}

	internal fun currentShowIcon(): Boolean = showIcon

	internal fun currentShowWhen(): String = showWhen

	internal fun instanceCompleted() {
		warning.completed(reminderAfterRunSetting.on, lowQuiverAmountSetting.amount.toInt())
	}

	private fun lowQuiverAlert(amount: Int) {
		DhenAlert.show(LOW_TITLE, sound = null)
		Dhen.announce("Low on arrows ($amount left)")
	}

	private fun instanceAlert(arrows: String) {
		DhenAlert.show(LOW_TITLE, sound = null)
		Dhen.announce("Low on $arrows!")
		launch {
			repeat(SOUND_REPEATS) {
				Minecraft.getInstance().soundManager.play(
					SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f)
				)
				delay(SOUND_DELAY_MILLIS)
			}
		}
	}

	private fun inInstance(): Boolean =
		SkyBlockLocation.island == Island.CATACOMBS || SkyBlockLocation.island == Island.KUUDRA

	private const val SAMPLE_TICKS = 40
	private const val LOW_TITLE = "Low on arrows!"
	private const val SOUND_REPEATS = 30
	private const val SOUND_DELAY_MILLIS = 100L
}

internal class QuiverEquipment {
	private val slots = SkyBlockItems.memo(Inventory.INVENTORY_SIZE + EXTRA_SLOTS)

	var hasBow: Boolean = false
		private set

	var holdingBow: Boolean = false
		private set

	fun sample(items: List<ItemStack>, selected: ItemStack): Boolean {
		var foundBow = false
		for (index in items.indices) {
			if (realBow(items[index], slots.of(index, items[index]))) {
				foundBow = true
				break
			}
		}
		val selectedBow = realBow(selected, slots.of(HAND_SLOT, selected))
		if (hasBow == foundBow && holdingBow == selectedBow) return false
		hasBow = foundBow
		holdingBow = selectedBow
		return true
	}

	fun clear(): Boolean {
		if (!hasBow && !holdingBow) return false
		hasBow = false
		holdingBow = false
		return true
	}

	private fun realBow(stack: ItemStack, item: io.github.dzkchen.dhen.data.item.SkyBlockItem): Boolean =
		stack.item is BowItem && item.id != BOSS_SPIRIT_BOW && item.id != CRYPT_BOW

	private companion object {
		const val EXTRA_SLOTS = 1
		const val HAND_SLOT = Inventory.INVENTORY_SIZE
		const val BOSS_SPIRIT_BOW = "BOSS_SPIRIT_BOW"
		const val CRYPT_BOW = "CRYPT_BOW"
	}
}

internal class QuiverDisplayElement : HudElement("Quiver Display", offsetX = 12, offsetY = 28) {
	private val memo = DhenType.memo()
	private var shown = DhenType.component("None").copy().withStyle(ChatFormatting.GRAY)
	private var icon = ItemStack(Items.ARROW)
	private var iconShown = true
	private var arrow: QuiverArrow? = null
	private var amount = 0
	private var infinite = false
	private var repoState = RepoState.IDLE
	private var repoCommit: String? = null
	internal var rebuilds = 0
		private set

	internal val shownText: String
		get() = shown.string

	internal val shownIcon: ItemStack
		get() = icon

	internal val shownNameColor: Int?
		get() = shown.siblings.lastOrNull()?.getStyle()?.color?.value

	override val hasContent: Boolean
		get() = QuiverDisplay.shows(
			QuiverDisplay.currentShowWhen(),
			SkyBlockLocation.inSkyBlock,
			editingHud()
		)

	override fun width(font: Font): Int = textLeft() + memo.width(font, shown)

	override fun height(font: Font): Int = maxOf(if (iconShown) ICON_SIZE else 0, DhenType.lineHeight(font))

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val height = height(font)
		if (iconShown) graphics.item(icon, 0, (height - ICON_SIZE) / 2)
		memo.shadowed(
			graphics,
			font,
			shown,
			textLeft(),
			(height - DhenType.lineHeight(font)) / 2,
			textInk,
			scale
		)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	fun refresh() {
		val nextArrow = QuiverState.currentArrow
		val nextAmount = QuiverState.currentAmount
		val nextInfinite = QuiverState.infiniteArrows
		val nextIconShown = QuiverDisplay.currentShowIcon()
		val nextRepoState = ItemRepo.state
		val nextRepoCommit = ItemRepo.commit
		if (!needsRefresh(nextArrow, nextAmount, nextInfinite, nextIconShown, nextRepoState, nextRepoCommit)) return
		refresh(
			nextArrow,
			nextAmount,
			nextInfinite,
			nextIconShown,
			nextRepoState,
			nextRepoCommit,
			nextArrow?.let { ItemRepo.item(it.id) }
		)
	}

	internal fun refresh(
		arrow: QuiverArrow?,
		amount: Int,
		infinite: Boolean,
		showIcon: Boolean,
		repoState: RepoState,
		repoCommit: String?,
		repoItem: RepoItem?
	) {
		if (!needsRefresh(arrow, amount, infinite, showIcon, repoState, repoCommit)) return
		this.arrow = arrow
		this.amount = amount
		this.infinite = infinite
		iconShown = showIcon
		this.repoState = repoState
		this.repoCommit = repoCommit
		val shownArrow = arrow ?: QuiverArrow.NONE
		val hideAmount = infinite || shownArrow == QuiverArrow.NONE
		val name = if (!hideAmount && amount != 1) shownArrow.displayName + "s" else shownArrow.displayName
		val rarity = rarity(repoItem)
		val prefix = if (hideAmount) "" else grouped(amount.coerceAtLeast(0).toLong()) + "x "
		shown = DhenType.component(prefix).copy().append(
			DhenType.component(name).copy()
				.withStyle(ChatFormatting.getByCode(rarity.colorCode[1]) ?: ChatFormatting.GRAY)
		)
		icon = stack(repoItem)
		memo.invalidate()
		rebuilds++
	}

	private fun needsRefresh(
		arrow: QuiverArrow?,
		amount: Int,
		infinite: Boolean,
		showIcon: Boolean,
		repoState: RepoState,
		repoCommit: String?
	): Boolean =
		this.arrow != arrow || this.amount != amount || this.infinite != infinite ||
			iconShown != showIcon || this.repoState != repoState || this.repoCommit != repoCommit

	private fun textLeft(): Int = if (iconShown) ICON_SIZE + ICON_GAP else 0

	private fun stack(item: RepoItem?): ItemStack {
		val identifier = item?.itemId?.let(Identifier::tryParse)
		val vanilla = identifier?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: Items.ARROW
		return ItemStack(vanilla)
	}

	private fun rarity(item: RepoItem?): ItemRarity {
		val lore = item?.lore ?: return ItemRarity.NONE
		for (index in lore.indices.reversed()) ItemRarity.fromLoreLine(lore[index])?.let { return it }
		return ItemRarity.NONE
	}

	private companion object {
		const val ICON_SIZE = 16
		const val ICON_GAP = 1
	}
}
