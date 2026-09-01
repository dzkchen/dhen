package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.data.pet.PetProgress
import io.github.dzkchen.dhen.data.pet.PetRecord
import io.github.dzkchen.dhen.data.pet.PetStorage
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.gui.ArcGui
import io.github.dzkchen.dhen.gui.DhenFont
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal class PetDisplayHud : HudElement("Current Pet", offsetX = 12, offsetY = 12) {
	private val mainMemos = Array(TEXT_OPTIONS) { DhenType.memo() }
	private val expMemos = Array(EXP_SHARE_SLOTS) { Array(TEXT_OPTIONS) { DhenType.memo() } }
	private val settings = PetDisplay.settings
	private var positionedX = 0
	private var positionedY = 0
	private var previewSnapshot: PetHudSnapshot? = null
	private var snapshot = PetHudSnapshot.EMPTY
	private var currentRevision = Int.MIN_VALUE
	private var storageRevision = Int.MIN_VALUE
	private var configFingerprint = Int.MIN_VALUE
	private var measuredSnapshot = PetHudSnapshot.EMPTY
	private var measuredFont = Int.MIN_VALUE
	private var measuredWidth = 1
	private var measuredHeight = 1
	private var layoutWidth = 1
	private var layoutHeight = 1
	private var layoutMainX = 0
	private var layoutMainY = 0
	private var layoutTextX = 0
	private var layoutTextY = 0
	private var layoutTextWidth = 0
	private var layoutMainTextY = 0
	private var layoutScale = 1.0

	override val hasContent: Boolean
		get() = editingHud() || SkyBlockLocation.inSkyBlock && snapshot.main != null

	override fun width(font: Font): Int {
		measure(font)
		return measuredWidth
	}

	override fun height(font: Font): Int {
		measure(font)
		return measuredHeight
	}

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		measure(font)
		val shown = if (editingHud()) preview() else snapshot
		val main = shown.main ?: return
		val visuals = PetDisplay.hudIconSetting.on
		val mainDiameter = if (visuals) diameter(main = true) else 0
		val now = Util.getMillis()
		val pose = graphics.pose()
		if (layoutScale != 1.0) {
			pose.pushMatrix()
			pose.scale(layoutScale.toFloat(), layoutScale.toFloat())
		}
		try {
			if (visuals) {
				drawVisual(graphics, main, layoutMainX, layoutMainY, true, 1f, now)
				drawExpShare(graphics, font, shown, layoutMainX, layoutMainY, mainDiameter, now)
			}
			drawMainText(graphics, font, shown)
			drawBundledText(graphics, font, shown)
		} finally {
			if (layoutScale != 1.0) pose.popMatrix()
		}
	}

	override fun invalidateMeasurement() {
		measuredSnapshot = PetHudSnapshot.EMPTY
		for (memo in mainMemos) memo.invalidate()
		for (group in expMemos) for (memo in group) memo.invalidate()
	}

	fun refresh() {
		val nextFingerprint = fingerprint()
		if (
			currentRevision == CurrentPet.revision && storageRevision == PetStorage.revision &&
			configFingerprint == nextFingerprint
		) return
		currentRevision = CurrentPet.revision
		storageRevision = PetStorage.revision
		configFingerprint = nextFingerprint
		snapshot = buildSnapshot()
		measuredSnapshot = PetHudSnapshot.EMPTY
	}

	private fun buildSnapshot(): PetHudSnapshot {
		val main = currentPet()
		val sharing = MayorService.isPerkActive(SHARING_IS_CARING)
		val pets = arrayOfNulls<PetHudPet>(EXP_SHARE_SLOTS)
		if (PetDisplay.expSharePetsSetting.on) {
			for (index in pets.indices) {
				val record = PetStorage.expShare(index) ?: continue
				if (record.uuid == CurrentPet.uuid) continue
				val active = PetStorage.expShareActive(index, sharing)
				if (!active && PetDisplay.hideDisabledSlotsSetting.on) continue
				pets[index] = record.pet(active)
			}
		}
		return PetHudSnapshot(main, pets)
	}

	private fun currentPet(): PetHudPet? {
		if (!CurrentPet.summoned) return null
		val info = CurrentPet.info
		val progress = when {
			CurrentPet.progress.isFinite() -> CurrentPet.progress
			info != null -> PetProgress.of(info).percentage
			else -> 0.0
		}
		return PetHudPet(
			CurrentPet.name,
			CurrentPet.level.takeIf { it > 0 } ?: info?.let { PetProgress.of(it).level } ?: 0,
			CurrentPet.tier.ifEmpty { info?.tier.orEmpty() },
			progress,
			info,
			CurrentPet.stack,
			heldStack(info?.heldItem),
			true,
			mainLines(CurrentPet.name, CurrentPet.level, info, progress)
		)
	}

	private fun PetRecord.pet(active: Boolean): PetHudPet {
		val progress = PetProgress.of(info)
		return PetHudPet(
			styledName,
			level.takeIf { it > 0 } ?: progress.level,
			info.tier,
			progress.percentage,
			info,
			stack,
			heldStack(info.heldItem),
			active,
			expLines(styledName, level, info, progress.percentage)
		)
	}

	private fun mainLines(name: String, level: Int, info: PetInfo?, progress: Double): Array<String> = lines(
		PetDisplay.enabledTextSetting.value,
		name,
		level,
		info,
		progress,
		PetDisplay.textLabelsSetting.on,
		PetDisplay.textPetLevelSetting.on,
		PetDisplay.textSkinSymbolSetting.on,
		PetDisplay.nextLevelPercentSetting.on,
		PetDisplay.xpFormatSetting.value
	)

	private fun expLines(name: String, level: Int, info: PetInfo?, progress: Double): Array<String> = lines(
		PetDisplay.expShareEnabledTextSetting.value,
		name,
		level,
		info,
		progress,
		PetDisplay.expShareTextLabelsSetting.on,
		PetDisplay.expShareTextPetLevelSetting.on,
		PetDisplay.expShareTextSkinSymbolSetting.on,
		PetDisplay.expShareNextLevelPercentSetting.on,
		PetDisplay.expShareXpFormatSetting.value
	)

	private fun lines(
		enabled: List<String>,
		name: String,
		reportedLevel: Int,
		info: PetInfo?,
		progress: Double,
		labels: Boolean,
		showLevel: Boolean,
		showSkin: Boolean,
		showPercentage: Boolean,
		format: String
	): Array<String> {
		val result = Array(TEXT_OPTIONS) { "" }
		val petProgress = info?.let(PetProgress::of)
		val level = reportedLevel.takeIf { it > 0 } ?: petProgress?.level ?: 0
		var line = 0
		for (option in enabled) {
			val value = when (option) {
				"Pet Name" -> petName(name, level, showLevel, showSkin)
				"Next Level" -> nextLevel(petProgress, progress, showPercentage, format)
				"Overflow XP" -> petProgress?.overflowXp?.takeIf { it > MIN_OVERFLOW }?.let { "§7+§b${number(it, format)}" }.orEmpty()
				"Total XP" -> info?.exp?.takeIf { it > 0.0 }?.let { "§b${number(it, format)}" }.orEmpty()
				"Held Item" -> info?.heldItem?.let(::heldItemName).orEmpty()
				else -> ""
			}
			if (value.isEmpty()) continue
			result[line++] = if (labels && option != "Pet Name") "§e$option§7: $value" else value
		}
		return result
	}

	private fun petName(name: String, level: Int, showLevel: Boolean, showSkin: Boolean): String {
		var shown = if (showSkin) name else name.removeSuffix(" ✦").trimEnd()
		if (showLevel && level > 0) shown = "§7[Lvl $level] $shown"
		return shown
	}

	private fun nextLevel(progress: io.github.dzkchen.dhen.data.repo.PetLevelProgress?, percent: Double, showPercent: Boolean, format: String): String {
		if (progress != null && progress.level >= progress.maxLevel) return ""
		val suffix = if (showPercent) " §7- §e${decimal(percent)}%" else ""
		if (progress == null || progress.nextLevelXp <= progress.currentLevelXp) return if (showPercent) "§e${decimal(percent)}%" else ""
		val current = progress.totalXp - progress.currentLevelXp
		val needed = progress.nextLevelXp - progress.currentLevelXp
		return "§b${number(current, if (format == "Default") "Unformatted" else format)}§9/§b${number(needed, if (format == "Default") "Formatted" else format)}$suffix"
	}

	private fun heldItemName(id: String): String =
		ItemRepo.item(id)?.displayName ?: id.lowercase().split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

	private fun heldStack(id: String?): ItemStack {
		val item = id?.let(ItemRepo::item) ?: return ItemStack.EMPTY
		val identifier = Identifier.tryParse(item.itemId) ?: return ItemStack.EMPTY
		val vanilla = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null) ?: return ItemStack.EMPTY
		return ItemStack(vanilla)
	}

	private fun number(value: Double, format: String): String {
		val whole = value.toLong()
		if (format != "Formatted") return String.format(Locale.US, "%,d", whole)
		val magnitude = abs(value)
		return when {
			magnitude >= 1_000_000_000.0 -> decimal(value / 1_000_000_000.0) + "B"
			magnitude >= 1_000_000.0 -> decimal(value / 1_000_000.0) + "M"
			magnitude >= 1_000.0 -> decimal(value / 1_000.0) + "k"
			else -> whole.toString()
		}
	}

	private fun decimal(value: Double): String {
		val rounded = (value * 10.0).roundToInt()
		return if (rounded % 10 == 0) (rounded / 10).toString() else "${rounded / 10}.${abs(rounded % 10)}"
	}

	private fun measure(font: Font) {
		val shown = if (editingHud()) preview() else snapshot
		if (measuredSnapshot === shown && measuredFont == DhenFont.revision) return
		measuredSnapshot = shown
		measuredFont = DhenFont.revision
		val rawVisualWidth = visualWidth(shown)
		val rawVisualHeight = visualHeight(shown)
		val attached = attachedText()
		val attachedWidth = attachedWidth(font, shown)
		val attachedHeight = attachedHeight(font, shown)
		val attachedLeft = if (attached && PetDisplay.expShareTextLocationSetting.value == "Left") attachedWidth + TEXT_GAP else 0
		val attachedTop = if (attached && PetDisplay.expShareTextLocationSetting.value == "Top") attachedHeight + TEXT_GAP else 0
		val attachedRight = if (attached && PetDisplay.expShareTextLocationSetting.value == "Right") attachedWidth + TEXT_GAP else 0
		val attachedBottom = if (attached && PetDisplay.expShareTextLocationSetting.value == "Bottom") attachedHeight + TEXT_GAP else 0
		val visualWidth = rawVisualWidth + attachedLeft + attachedRight
		val visualHeight = rawVisualHeight + attachedTop + attachedBottom
		val mainDiameter = if (PetDisplay.hudIconSetting.on) diameter(main = true) else 0
		val rawMainX = attachedLeft + mainVisualX(shown)
		val rawMainY = attachedTop + mainVisualY(shown)
		val mainTextWidth = textWidth(font, shown.main?.lines, mainMemos, PetDisplay.textScaleSetting.amount)
		val mainTextHeight = textHeight(font, shown.main?.lines, PetDisplay.textScaleSetting.amount)
		val bundledWidth = bundledWidth(font, shown)
		val textWidth = max(mainTextWidth, bundledWidth)
		val textHeight = mainTextHeight + bundledHeight(font, shown)
		val allVisuals = PetDisplay.centerTargetSetting.value == "All Pet Visuals"
		val anchorX = if (allVisuals) 0 else rawMainX
		val anchorY = if (allVisuals) 0 else rawMainY
		val anchorWidth = if (allVisuals) visualWidth else mainDiameter
		val anchorHeight = if (allVisuals) visualHeight else mainDiameter
		val rawTextX = if (visualWidth == 0 || visualHeight == 0) 0 else when (PetDisplay.textLocationSetting.value) {
			"Left" -> anchorX - textWidth - TEXT_GAP
			"Right" -> anchorX + anchorWidth + TEXT_GAP
			else -> aligned(anchorX, anchorWidth, textWidth, PetDisplay.horizontalAlignmentSetting.value)
		}
		val rawTextY = if (visualWidth == 0 || visualHeight == 0) 0 else when (PetDisplay.textLocationSetting.value) {
			"Top" -> anchorY - textHeight - TEXT_GAP
			"Bottom" -> anchorY + anchorHeight + TEXT_GAP
			else -> aligned(anchorY, anchorHeight, textHeight, PetDisplay.verticalAlignmentSetting.value)
		}
		val minX = minOf(0, rawTextX)
		val minY = minOf(0, rawTextY)
		layoutWidth = (max(visualWidth, rawTextX + textWidth) - minX).coerceAtLeast(1)
		layoutHeight = (max(visualHeight, rawTextY + textHeight) - minY).coerceAtLeast(1)
		layoutMainX = rawMainX - minX
		layoutMainY = rawMainY - minY
		layoutTextX = rawTextX - minX
		layoutTextY = rawTextY - minY
		layoutTextWidth = textWidth
		layoutMainTextY = bundledAboveHeight(font, shown)
		layoutScale = if (editingHud()) PetDisplay.previewScaleSetting.amount else 1.0
		measuredWidth = scaled(layoutWidth, layoutScale).coerceAtLeast(1)
		measuredHeight = scaled(layoutHeight, layoutScale).coerceAtLeast(1)
	}

	private fun drawMainText(
		graphics: GuiGraphicsExtractor,
		font: Font,
		shown: PetHudSnapshot
	) {
		val lines = shown.main?.lines ?: return
		val width = textWidth(font, lines, mainMemos, PetDisplay.textScaleSetting.amount)
		val x = aligned(layoutTextX, layoutTextWidth, width, PetDisplay.horizontalAlignmentSetting.value)
		drawText(
			graphics,
			font,
			lines,
			mainMemos,
			x,
			layoutTextY + layoutMainTextY,
			PetDisplay.textScaleSetting.amount,
			1f,
			PetDisplay.horizontalAlignmentSetting.value
		)
	}

	private fun drawBundledText(graphics: GuiGraphicsExtractor, font: Font, shown: PetHudSnapshot) {
		if (!PetDisplay.expShareTextSetting.on || PetDisplay.expShareTextModeSetting.value != "Bundled") return
		val total = expCount(shown)
		val above = when (PetDisplay.bundledLocationSetting.value) {
			"Above Main Text" -> total
			"Split Around Main Text" -> (total + 1) / 2
			else -> 0
		}
		var ordinal = 0
		var aboveY = layoutTextY
		var belowY = layoutTextY + layoutMainTextY + textHeight(font, shown.main?.lines, PetDisplay.textScaleSetting.amount) +
			if (total > above) PetDisplay.bundledSpacingSetting.amount.roundToInt() else 0
		for (index in shown.expShare.indices) {
			val pet = shown.expShare[index] ?: continue
			val memos = expMemos[index]
			val y = if (ordinal < above) aboveY else belowY
			drawText(
				graphics,
				font,
				pet.lines,
				memos,
				aligned(layoutTextX, layoutTextWidth, textWidth(font, pet.lines, memos, PetDisplay.expShareTextScaleSetting.amount), PetDisplay.expShareHorizontalAlignmentSetting.value),
				y,
				PetDisplay.expShareTextScaleSetting.amount,
				if (pet.active) 1f else PetDisplay.disabledOpacitySetting.amount.toFloat(),
				PetDisplay.expShareHorizontalAlignmentSetting.value
			)
			val advance = textHeight(font, pet.lines, PetDisplay.expShareTextScaleSetting.amount) + PetDisplay.bundledSpacingSetting.amount.roundToInt()
			if (ordinal < above) aboveY += advance else belowY += advance
			ordinal++
		}
	}

	private fun drawExpShare(
		graphics: GuiGraphicsExtractor,
		font: Font,
		shown: PetHudSnapshot,
		mainX: Int,
		mainY: Int,
		mainDiameter: Int,
		now: Long
	) {
		if (!PetDisplay.expSharePetsSetting.on || !PetDisplay.expShareIconSetting.on) return
		val diameter = diameter(main = false)
		val total = expCount(shown)
		var placed = 0
		for (index in shown.expShare.indices) {
			val pet = shown.expShare[index] ?: continue
			positionExp(mainX, mainY, mainDiameter, diameter, placed, total, now)
			val opacity = if (pet.active) 1f else PetDisplay.disabledOpacitySetting.amount.toFloat()
			drawVisual(graphics, pet, positionedX, positionedY, false, opacity, now)
			if (PetDisplay.expShareTextSetting.on && PetDisplay.expShareTextModeSetting.value == "Attached") {
				val memos = expMemos[index]
				val textWidth = textWidth(font, pet.lines, memos, PetDisplay.expShareTextScaleSetting.amount)
				val textHeight = textHeight(font, pet.lines, PetDisplay.expShareTextScaleSetting.amount)
				val textX = when (PetDisplay.expShareTextLocationSetting.value) {
					"Left" -> positionedX - textWidth - TEXT_GAP
					"Right" -> positionedX + diameter + TEXT_GAP
					else -> aligned(positionedX, diameter, textWidth, PetDisplay.expShareHorizontalAlignmentSetting.value)
				}
				val textY = when (PetDisplay.expShareTextLocationSetting.value) {
					"Top" -> positionedY - textHeight - TEXT_GAP
					"Bottom" -> positionedY + diameter + TEXT_GAP
					else -> aligned(positionedY, diameter, textHeight, PetDisplay.expShareVerticalAlignmentSetting.value)
				}
				drawText(
					graphics,
					font,
					pet.lines,
					memos,
					textX,
					textY,
					PetDisplay.expShareTextScaleSetting.amount,
					opacity,
					PetDisplay.expShareHorizontalAlignmentSetting.value
				)
			}
			placed++
		}
	}

	private fun drawVisual(
		graphics: GuiGraphicsExtractor,
		pet: PetHudPet,
		x: Int,
		y: Int,
		main: Boolean,
		opacity: Float,
		now: Long
	) {
		val diameter = diameter(main)
		val centerX = x + diameter / 2
		val centerY = y + diameter / 2
		val iconScale = if (main) PetDisplay.hudIconScaleSetting.amount else PetDisplay.expShareIconScaleSetting.amount
		val background = if (main) PetDisplay.backgroundSetting.on else PetDisplay.expShareBackgroundSetting.on
		val ring = background && if (main) PetDisplay.xpRingSetting.on else PetDisplay.expShareRingSetting.on
		val iconRadius = ceil(ITEM_SIZE * iconScale / 2.0).toInt()
		val backgroundRadius = iconRadius + if (background) {
			(if (main) PetDisplay.backgroundPaddingSetting.amount else PetDisplay.expShareBackgroundPaddingSetting.amount).roundToInt()
		} else 0
		if (background) RoundedGui.circle(graphics, centerX, centerY, backgroundRadius, rarityColor(pet.tier, main, opacity))
		if (ring) {
			val separator = if (main) PetDisplay.separatorRingSetting.on else PetDisplay.expShareSeparatorRingSetting.on
			val ringWidth = (if (main) PetDisplay.ringPaddingSetting.amount else PetDisplay.expShareRingPaddingSetting.amount).toFloat()
			var inner = backgroundRadius.toFloat()
			if (separator) {
				val separatorWidth = if (main) PetDisplay.separatorRingPaddingSetting.amount.toFloat() else PetDisplay.expShareSeparatorRingPaddingSetting.amount.toFloat()
				ArcGui.annularSegment(graphics, centerX.toFloat(), centerY.toFloat(), inner, inner + separatorWidth, START_ANGLE, ArcGui.TAU, alpha(if (main) PetDisplay.separatorRingColorSetting.value.argb else PetDisplay.expShareSeparatorRingColorSetting.value.argb, opacity))
				inner += separatorWidth
			}
			ArcGui.annularSegment(graphics, centerX.toFloat(), centerY.toFloat(), inner, inner + ringWidth, START_ANGLE, ArcGui.TAU, alpha(if (main) PetDisplay.unfilledRingColorSetting.value.argb else PetDisplay.expShareUnfilledRingColorSetting.value.argb, opacity))
			ArcGui.annularSegment(graphics, centerX.toFloat(), centerY.toFloat(), inner, inner + ringWidth, START_ANGLE, ArcGui.TAU * (pet.progress / 100.0).toFloat(), alpha(if (main) PetDisplay.filledRingColorSetting.value.argb else PetDisplay.expShareFilledRingColorSetting.value.argb, opacity))
		}
		val iconEnabled = if (main) PetDisplay.hudIconSetting.on else PetDisplay.expShareIconSetting.on
		if (iconEnabled && !pet.stack.isEmpty) {
			drawItem(graphics, pet.stack, centerX, centerY, main, iconScale.toFloat(), now)
			if (opacity < 1f) RoundedGui.circle(graphics, centerX, centerY, iconRadius, veil(opacity))
		}
		val itemEnabled = if (main) PetDisplay.hudPetItemSetting.on else PetDisplay.expSharePetItemSetting.on
		if (itemEnabled && !pet.heldStack.isEmpty) drawHeldItem(graphics, pet.heldStack, x, y, diameter, main, opacity)
	}

	private fun drawItem(
		graphics: GuiGraphicsExtractor,
		stack: ItemStack,
		centerX: Int,
		centerY: Int,
		main: Boolean,
		scale: Float,
		now: Long
	) {
		val xRotation = angle(main, 0, now)
		val yRotation = angle(main, 1, now)
		val zRotation = angle(main, 2, now)
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(centerX.toFloat(), centerY.toFloat())
		pose.rotate(Math.toRadians(zRotation).toFloat())
			pose.scale(scale * squash(yRotation), scale * squash(xRotation))
			graphics.item(stack, -ITEM_SIZE / 2, -ITEM_SIZE / 2)
		} finally {
			pose.popMatrix()
		}
	}

	private fun drawHeldItem(
		graphics: GuiGraphicsExtractor,
		stack: ItemStack,
		x: Int,
		y: Int,
		diameter: Int,
		main: Boolean,
		opacity: Float
	) {
		val placement = if (main) PetDisplay.hudPetItemPlacementSetting.value else PetDisplay.expSharePetItemPlacementSetting.value
		val scale = if (main) PetDisplay.hudPetItemScaleSetting.amount else PetDisplay.expSharePetItemScaleSetting.amount
		val width = scaled(ITEM_SIZE, scale)
		val height = scaled(ITEM_SIZE, scale)
		val left = when {
			placement.endsWith("Left") -> x - width / 2
			placement.endsWith("Right") -> x + diameter - width / 2
			else -> x + (diameter - width) / 2
		}
		val top = when {
			placement.startsWith("Top") -> y - height / 2
			placement.startsWith("Bottom") -> y + diameter - height / 2
			else -> y + (diameter - height) / 2
		}
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate((left + width / 2).toFloat(), (top + height / 2).toFloat())
			pose.scale(scale.toFloat(), scale.toFloat())
			graphics.item(stack, -ITEM_SIZE / 2, -ITEM_SIZE / 2)
		} finally {
			pose.popMatrix()
		}
		if (opacity < 1f) RoundedGui.circle(graphics, left + width / 2, top + height / 2, max(width, height) / 2, veil(opacity))
	}

	private fun drawText(
		graphics: GuiGraphicsExtractor,
		font: Font,
		lines: Array<String>,
		memos: Array<TextMemo>,
		x: Int,
		y: Int,
		scale: Double,
		opacity: Float,
		alignment: String
	) {
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(x.toFloat(), y.toFloat())
			pose.scale(scale.toFloat(), scale.toFloat())
			val width = textWidth(font, lines, memos, 1.0)
			var row = 0
			for (index in lines.indices) {
				val line = lines[index]
				if (line.isEmpty()) continue
				val lineWidth = memos[index].width(font, line)
				val lineX = aligned(0, width, lineWidth, alignment)
				memos[index].shadowed(graphics, font, line, lineX, row * DhenType.lineHeight(font), alpha(DhenPalette.TEXT_PRIMARY, opacity), 1f)
				row++
			}
		} finally {
			pose.popMatrix()
		}
	}

	private fun positionExp(mainX: Int, mainY: Int, mainDiameter: Int, diameter: Int, index: Int, count: Int, now: Long) {
		val gap = PetDisplay.expShareIconSpacingSetting.amount.roundToInt()
		if (PetDisplay.expSharePlacementSetting.value == "Orbit") {
			val distance = mainDiameter / 2 + diameter / 2 + PetDisplay.orbitDistanceSetting.amount.roundToInt()
			val direction = if (PetDisplay.orbitDirectionSetting.value == "Clockwise") 1.0 else -1.0
			val angle = direction * now / 1_000.0 * Math.toRadians(PetDisplay.orbitSpeedSetting.amount) + index * ArcGui.TAU / EXP_SHARE_SLOTS
			positionedX = mainX + mainDiameter / 2 + (cos(angle) * distance).roundToInt() - diameter / 2
			positionedY = mainY + mainDiameter / 2 + (sin(angle) * distance).roundToInt() - diameter / 2
			return
		}
		val horizontal = PetDisplay.expShareOrientationSetting.value == "Horizontal"
		val span = if (horizontal) count * diameter + (count - 1) * gap else diameter
		val offsetX = if (horizontal) index * (diameter + gap) else 0
		val offsetY = if (horizontal) 0 else index * (diameter + gap)
		when (PetDisplay.expSharePlacementSetting.value) {
			"Top" -> {
				positionedX = mainX + (mainDiameter - span) / 2 + offsetX
				positionedY = mainY - diameter - GROUP_GAP - offsetY
			}
			"Bottom" -> {
				positionedX = mainX + (mainDiameter - span) / 2 + offsetX
				positionedY = mainY + mainDiameter + GROUP_GAP + offsetY
			}
			"Left" -> {
				positionedX = mainX - diameter - GROUP_GAP - offsetX
				positionedY = mainY + (mainDiameter - if (horizontal) diameter else verticalSpan(count, diameter, gap)) / 2 + offsetY
			}
			else -> {
				positionedX = mainX + mainDiameter + GROUP_GAP + offsetX
				positionedY = mainY + (mainDiameter - if (horizontal) diameter else verticalSpan(count, diameter, gap)) / 2 + offsetY
			}
		}
	}

	private fun mainVisualX(shown: PetHudSnapshot): Int {
		if (!PetDisplay.hudIconSetting.on) return 0
		val count = expCount(shown)
		if (!PetDisplay.expSharePetsSetting.on || count == 0) return 0
		val main = diameter(main = true)
		val small = diameter(main = false)
		val placement = PetDisplay.expSharePlacementSetting.value
		if (placement == "Orbit") return small + PetDisplay.orbitDistanceSetting.amount.roundToInt()
		val gap = PetDisplay.expShareIconSpacingSetting.amount.roundToInt()
		val span = if (PetDisplay.expShareOrientationSetting.value == "Horizontal") count * small + (count - 1) * gap else small
		return when (placement) {
			"Left" -> span + GROUP_GAP
			"Top", "Bottom" -> (visualWidth(shown) - main) / 2
			else -> 0
		}
	}

	private fun mainVisualY(shown: PetHudSnapshot): Int {
		if (!PetDisplay.hudIconSetting.on) return 0
		val count = expCount(shown)
		if (!PetDisplay.expSharePetsSetting.on || count == 0) return 0
		val main = diameter(main = true)
		val small = diameter(main = false)
		val placement = PetDisplay.expSharePlacementSetting.value
		if (placement == "Orbit") return small + PetDisplay.orbitDistanceSetting.amount.roundToInt()
		val gap = PetDisplay.expShareIconSpacingSetting.amount.roundToInt()
		val span = if (PetDisplay.expShareOrientationSetting.value == "Vertical") verticalSpan(count, small, gap) else small
		return when (placement) {
			"Top" -> span + GROUP_GAP
			"Left", "Right" -> (visualHeight(shown) - main) / 2
			else -> 0
		}
	}

	private fun visualWidth(shown: PetHudSnapshot): Int {
		if (!PetDisplay.hudIconSetting.on) return 0
		val main = diameter(main = true)
		val count = expCount(shown)
		if (!PetDisplay.expSharePetsSetting.on || !PetDisplay.expShareIconSetting.on || count == 0) return main
		val small = diameter(main = false)
		if (PetDisplay.expSharePlacementSetting.value == "Orbit") return main + 2 * (small + PetDisplay.orbitDistanceSetting.amount.roundToInt())
		val span = if (PetDisplay.expShareOrientationSetting.value == "Horizontal") count * small + (count - 1) * PetDisplay.expShareIconSpacingSetting.amount.roundToInt() else small
		val placement = PetDisplay.expSharePlacementSetting.value
		return if (placement == "Left" || placement == "Right") main + GROUP_GAP + span else max(main, span)
	}

	private fun visualHeight(shown: PetHudSnapshot): Int {
		if (!PetDisplay.hudIconSetting.on) return 0
		val main = diameter(main = true)
		val count = expCount(shown)
		if (!PetDisplay.expSharePetsSetting.on || !PetDisplay.expShareIconSetting.on || count == 0) return main
		val small = diameter(main = false)
		if (PetDisplay.expSharePlacementSetting.value == "Orbit") return main + 2 * (small + PetDisplay.orbitDistanceSetting.amount.roundToInt())
		val span = if (PetDisplay.expShareOrientationSetting.value == "Vertical") count * small + (count - 1) * PetDisplay.expShareIconSpacingSetting.amount.roundToInt() else small
		val placement = PetDisplay.expSharePlacementSetting.value
		return if (placement == "Top" || placement == "Bottom") main + GROUP_GAP + span else max(main, span)
	}

	private fun diameter(main: Boolean): Int {
		val iconScale = if (main) PetDisplay.hudIconScaleSetting.amount else PetDisplay.expShareIconScaleSetting.amount
		val icon = ceil(ITEM_SIZE * iconScale).toInt()
		val background = if (main) PetDisplay.backgroundSetting.on else PetDisplay.expShareBackgroundSetting.on
		val backgroundPadding = if (main) PetDisplay.backgroundPaddingSetting.amount else PetDisplay.expShareBackgroundPaddingSetting.amount
		val ring = background && if (main) PetDisplay.xpRingSetting.on else PetDisplay.expShareRingSetting.on
		val ringPadding = if (main) PetDisplay.ringPaddingSetting.amount else PetDisplay.expShareRingPaddingSetting.amount
		val separator = ring && if (main) PetDisplay.separatorRingSetting.on else PetDisplay.expShareSeparatorRingSetting.on
		val separatorPadding = if (main) PetDisplay.separatorRingPaddingSetting.amount else PetDisplay.expShareSeparatorRingPaddingSetting.amount
		return icon + 2 * (if (background) backgroundPadding else 0.0).roundToInt() +
			2 * (if (ring) ringPadding else 0.0).roundToInt() + 2 * (if (separator) separatorPadding else 0.0).roundToInt()
	}

	private fun textWidth(font: Font, lines: Array<String>?, memos: Array<TextMemo>, scale: Double): Int {
		if (lines == null) return 0
		var width = 0
		for (index in lines.indices) if (lines[index].isNotEmpty()) width = max(width, memos[index].width(font, lines[index]))
		return scaled(width, scale)
	}

	private fun textHeight(font: Font, lines: Array<String>?, scale: Double): Int =
		if (lines == null) 0 else count(lines) * scaled(DhenType.lineHeight(font), scale)

	private fun bundledWidth(font: Font, shown: PetHudSnapshot): Int {
		if (!PetDisplay.expShareTextSetting.on || PetDisplay.expShareTextModeSetting.value != "Bundled") return 0
		var width = 0
		for (index in shown.expShare.indices) {
			val pet = shown.expShare[index] ?: continue
			width = max(width, textWidth(font, pet.lines, expMemos[index], PetDisplay.expShareTextScaleSetting.amount))
		}
		return width
	}

	private fun attachedWidth(font: Font, shown: PetHudSnapshot): Int {
		if (!attachedText()) return 0
		var width = 0
		for (index in shown.expShare.indices) {
			val pet = shown.expShare[index] ?: continue
			width = max(width, textWidth(font, pet.lines, expMemos[index], PetDisplay.expShareTextScaleSetting.amount))
		}
		return width
	}

	private fun attachedHeight(font: Font, shown: PetHudSnapshot): Int {
		if (!attachedText()) return 0
		var height = 0
		for (pet in shown.expShare) {
			if (pet == null) continue
			height = max(height, textHeight(font, pet.lines, PetDisplay.expShareTextScaleSetting.amount))
		}
		return height
	}

	private fun attachedText(): Boolean =
		PetDisplay.hudIconSetting.on && PetDisplay.expSharePetsSetting.on && PetDisplay.expShareIconSetting.on &&
			PetDisplay.expShareTextSetting.on && PetDisplay.expShareTextModeSetting.value == "Attached"

	private fun bundledHeight(font: Font, shown: PetHudSnapshot): Int {
		if (!PetDisplay.expShareTextSetting.on || PetDisplay.expShareTextModeSetting.value != "Bundled") return 0
		var height = 0
		var pets = 0
		for (pet in shown.expShare) {
			if (pet == null) continue
			height += textHeight(font, pet.lines, PetDisplay.expShareTextScaleSetting.amount)
			pets++
		}
		if (pets > 0) height += pets * PetDisplay.bundledSpacingSetting.amount.roundToInt()
		return height
	}

	private fun bundledAboveHeight(font: Font, shown: PetHudSnapshot): Int {
		if (!PetDisplay.expShareTextSetting.on || PetDisplay.expShareTextModeSetting.value != "Bundled") return 0
		val count = expCount(shown)
		val above = when (PetDisplay.bundledLocationSetting.value) {
			"Above Main Text" -> count
			"Split Around Main Text" -> (count + 1) / 2
			else -> 0
		}
		return bundledRangeHeight(font, shown, 0, above)
	}

	private fun bundledRangeHeight(font: Font, shown: PetHudSnapshot, from: Int, to: Int): Int {
		var height = 0
		var ordinal = 0
		for (pet in shown.expShare) {
			if (pet == null) continue
			if (ordinal >= from && ordinal < to) {
				height += textHeight(font, pet.lines, PetDisplay.expShareTextScaleSetting.amount)
				height += PetDisplay.bundledSpacingSetting.amount.roundToInt()
			}
			ordinal++
		}
		return height
	}

	private fun rarityColor(tier: String, main: Boolean, opacity: Float): Int {
		val setting = if (main) when (tier) {
			"UNCOMMON" -> PetDisplay.uncommonColorSetting
			"RARE" -> PetDisplay.rareColorSetting
			"EPIC" -> PetDisplay.epicColorSetting
			"LEGENDARY" -> PetDisplay.legendaryColorSetting
			"MYTHIC" -> PetDisplay.mythicColorSetting
			else -> PetDisplay.commonColorSetting
		} else when (tier) {
			"UNCOMMON" -> PetDisplay.expShareUncommonColorSetting
			"RARE" -> PetDisplay.expShareRareColorSetting
			"EPIC" -> PetDisplay.expShareEpicColorSetting
			"LEGENDARY" -> PetDisplay.expShareLegendaryColorSetting
			"MYTHIC" -> PetDisplay.expShareMythicColorSetting
			else -> PetDisplay.expShareCommonColorSetting
		}
		return alpha(setting.value.argb, opacity)
	}

	private fun angle(main: Boolean, axis: Int, now: Long): Double {
		val static = if (main) when (axis) {
			0 -> PetDisplay.staticRotationXSetting.amount
			1 -> PetDisplay.staticRotationYSetting.amount
			else -> PetDisplay.staticRotationZSetting.amount
		} else when (axis) {
			0 -> PetDisplay.expShareStaticRotationXSetting.amount
			1 -> PetDisplay.expShareStaticRotationYSetting.amount
			else -> PetDisplay.expShareStaticRotationZSetting.amount
		}
		val speed = if (main) when (axis) {
			0 -> PetDisplay.spinRotationXSetting.amount
			1 -> PetDisplay.spinRotationYSetting.amount
			else -> PetDisplay.spinRotationZSetting.amount
		} else when (axis) {
			0 -> PetDisplay.expShareSpinRotationXSetting.amount
			1 -> PetDisplay.expShareSpinRotationYSetting.amount
			else -> PetDisplay.expShareSpinRotationZSetting.amount
		}
		return static + speed * now / 1_000.0
	}

	private fun squash(angle: Double): Float = abs(cos(Math.toRadians(angle))).toFloat().coerceAtLeast(MIN_SQUASH)

	private fun fingerprint(): Int {
		var hash = 31 + ItemRepo.state.ordinal
		hash = 31 * hash + (ItemRepo.commit?.hashCode() ?: 0)
		hash = 31 * hash + if (MayorService.isPerkActive(SHARING_IS_CARING)) 1 else 0
		for (setting in settings) {
			hash = 31 * hash + when (val value = setting.value) {
				is Boolean -> if (value) 1 else 0
				is Double -> value.hashCode()
				is String -> value.hashCode()
				is io.github.dzkchen.dhen.util.Color -> value.argb
				is List<*> -> value.hashCode()
				else -> 0
			}
		}
		return hash
	}

	private fun count(lines: Array<String>): Int {
		var count = 0
		for (line in lines) if (line.isNotEmpty()) count++
		return count
	}

	private fun expCount(shown: PetHudSnapshot): Int {
		var count = 0
		for (pet in shown.expShare) if (pet != null) count++
		return count
	}

	private fun verticalSpan(count: Int, diameter: Int, gap: Int): Int = count * diameter + (count - 1) * gap

	private fun scaled(value: Int, scale: Double): Int = ceil(value * scale).toInt()

	private fun aligned(start: Int, span: Int, extent: Int, alignment: String): Int = when (alignment) {
		"Right", "Bottom" -> start + span - extent
		"Center" -> start + (span - extent) / 2
		else -> start
	}

	private fun alpha(color: Int, opacity: Float): Int = GlassGui.scaleAlpha(color, opacity.coerceIn(0f, 1f))

	private fun veil(opacity: Float): Int = GlassGui.scaleAlpha(DhenPalette.CANVAS, (1f - opacity).coerceIn(0f, 1f))

	private fun preview(): PetHudSnapshot {
		val cached = previewSnapshot
		if (cached != null) return cached
		val pet = PetHudPet(
			"§6Bee §d✦",
			100,
			"LEGENDARY",
			62.5,
			PREVIEW_INFO,
			ItemStack(Items.PLAYER_HEAD),
			ItemStack(Items.SUNFLOWER),
			true,
			arrayOf("§7[Lvl 100] §6Bee §d✦", "§eNext Level§7: §b2,000§9/§b4k §7- §e50%", "§eHeld Item§7: Exp Share", "", "")
		)
		return PetHudSnapshot(pet, arrayOfNulls(EXP_SHARE_SLOTS)).also { previewSnapshot = it }
	}

	private companion object {
		const val TEXT_OPTIONS = 5
		const val EXP_SHARE_SLOTS = 3
		const val ITEM_SIZE = 16
		const val TEXT_GAP = 2
		const val GROUP_GAP = 2
		const val MIN_OVERFLOW = 1_000.0
		const val MIN_SQUASH = 0.08f
		const val SHARING_IS_CARING = "Sharing is Caring"
		val START_ANGLE = (-PI / 2.0).toFloat()
		val PREVIEW_INFO = PetInfo("BEE", "LEGENDARY", 25_353_230.0, "PET_ITEM_EXP_SHARE", 0, "PET_SKIN_BEE_RGBEE")
	}
}

internal class PetHudPet(
	val name: String,
	val level: Int,
	val tier: String,
	val progress: Double,
	val info: PetInfo?,
	val stack: ItemStack,
	val heldStack: ItemStack,
	val active: Boolean,
	val lines: Array<String>
)

internal class PetHudSnapshot(val main: PetHudPet?, val expShare: Array<PetHudPet?>) {
	companion object {
		val EMPTY = PetHudSnapshot(null, emptyArray())
	}
}
