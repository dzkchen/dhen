package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SidebarEvent
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.CookieUpdateEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.MayorChangeEvent
import io.github.dzkchen.dhen.event.MaxwellUpdateEvent
import io.github.dzkchen.dhen.event.PartyEvent
import io.github.dzkchen.dhen.event.QuiverUpdateEvent
import io.github.dzkchen.dhen.event.ScoreboardUpdateEvent
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object CustomScoreboard : Module(
	name = "Custom Scoreboard",
	category = Category.VISUAL,
	description = "Replaces the vanilla SkyBlock sidebar with a movable Dhen scoreboard."
) {
	private val DEFAULT_LINES = listOf(
		ScoreboardLine.LOBBY_CODE,
		ScoreboardLine.SEPARATOR_1,
		ScoreboardLine.DATE,
		ScoreboardLine.TIME,
		ScoreboardLine.ISLAND,
		ScoreboardLine.PLAYER_COUNT,
		ScoreboardLine.LOCATION,
		ScoreboardLine.VISITING,
		ScoreboardLine.PROFILE,
		ScoreboardLine.SEPARATOR_2,
		ScoreboardLine.PURSE,
		ScoreboardLine.MOTES,
		ScoreboardLine.BANK,
		ScoreboardLine.BITS,
		ScoreboardLine.COPPER,
		ScoreboardLine.SOWDUST,
		ScoreboardLine.GEMS,
		ScoreboardLine.HEAT,
		ScoreboardLine.COLD,
		ScoreboardLine.NORTH_STARS,
		ScoreboardLine.SOULFLOW,
		ScoreboardLine.SEPARATOR_3,
		ScoreboardLine.EVENTS,
		ScoreboardLine.COOKIE,
		ScoreboardLine.QUIVER,
		ScoreboardLine.POWER,
		ScoreboardLine.TUNING,
		ScoreboardLine.SEPARATOR_4,
		ScoreboardLine.OBJECTIVE,
		ScoreboardLine.SLAYER,
		ScoreboardLine.POWDER,
		ScoreboardLine.MAYOR,
		ScoreboardLine.PARTY,
		ScoreboardLine.FOOTER,
		ScoreboardLine.EXTRA
	).map { it.label }

	private val DEFAULT_EVENTS = listOf(
		SidebarEvent.VOTING,
		SidebarEvent.SERVER_CLOSE,
		SidebarEvent.DUNGEONS,
		SidebarEvent.KUUDRA,
		SidebarEvent.DOJO,
		SidebarEvent.DARK_AUCTION,
		SidebarEvent.JACOB_CONTEST,
		SidebarEvent.JACOB_MEDALS,
		SidebarEvent.GALATEA,
		SidebarEvent.SAFARI,
		SidebarEvent.TRAPPER,
		SidebarEvent.GARDEN,
		SidebarEvent.FLIGHT_DURATION,
		SidebarEvent.NEW_YEAR,
		SidebarEvent.WINTER,
		SidebarEvent.SPOOKY,
		SidebarEvent.BROODMOTHER,
		SidebarEvent.MINING,
		SidebarEvent.DAMAGE,
		SidebarEvent.MAGMA_BOSS,
		SidebarEvent.CARNIVAL,
		SidebarEvent.RIFT,
		SidebarEvent.ESSENCE,
		SidebarEvent.ACTIVE_TABLIST,
		SidebarEvent.REDSTONE
	).map { it.label }

	internal val linesSetting = OrderedSelectionSetting(
		"Lines",
		ScoreboardLine.labels,
		DEFAULT_LINES,
		description = "Which typed lines the scoreboard shows, in the order it shows them."
	)
	private var enabledLines by linesSetting

	private var enabledEvents by OrderedSelectionSetting(
		"Events",
		SidebarEvent.labels,
		DEFAULT_EVENTS,
		description = "Which SkyBlock events the Events line shows, in the order it prefers them."
	).withDependency { linesSetting.enabled(ScoreboardLine.EVENTS.label) }

	private var allActiveEvents by BooleanSetting(
		"Show All Active Events",
		true,
		description = "Show every active event rather than only the highest-priority one."
	).withDependency { linesSetting.enabled(ScoreboardLine.EVENTS.label) }

	private val composer = ScoreboardComposer()

	internal val element = hud(CustomScoreboardElement())

	private var shownLines: List<String> = emptyList()

	private var shownEvents: List<String> = emptyList()

	private var shownAllActive = true

	private var mayorRequirement: Handle = Handle {}

	private var mayorHeld = false

	init {
		on<ScoreboardUpdateEvent> { rebuild() }
		on<TabWidgetUpdateEvent> { rebuild() }
		on<QuiverUpdateEvent> { rebuild() }
		on<PartyEvent> { rebuild() }
		on<IslandChangeEvent> { rebuild() }
		on<MayorChangeEvent> { rebuild() }
		on<MaxwellUpdateEvent> { rebuild() }
		on<CookieUpdateEvent> { rebuild() }
		on<ClientTickEvent.End> { ticked() }
	}

	override fun onEnabled() {
		ensureMayorFeed()
		rebuild()
	}

	override fun onDisabled() = releaseMayorFeed()

	internal fun ticked() {
		ensureMayorFeed()
		if (composer.faded() || composer.ticked() || enabledLines !== shownLines ||
			enabledEvents !== shownEvents || allActiveEvents != shownAllActive
		) {
			rebuild()
		}
	}

	private fun ensureMayorFeed() {
		val wanted = linesSetting.enabled(ScoreboardLine.MAYOR.label)
		if (wanted && !mayorHeld && MayorService.active()) {
			mayorRequirement = MayorService.require()
			mayorHeld = true
		} else if (!wanted && mayorHeld) {
			releaseMayorFeed()
		}
	}

	private fun releaseMayorFeed() {
		mayorRequirement.unsubscribe()
		mayorRequirement = Handle {}
		mayorHeld = false
	}

	internal fun rebuild() {
		composer.sampled()
		shownLines = enabledLines
		shownEvents = enabledEvents
		shownAllActive = allActiveEvents
		element.update(ScoreboardState.title, composer.compose(enabledLines, enabledEvents, allActiveEvents))
	}

	@JvmStatic
	fun shouldHideVanilla(): Boolean = enabled
}

internal class CustomScoreboardElement : HudElement("Scoreboard", HudAnchor.MIDDLE_RIGHT, -RIGHT_MARGIN, 0) {
	private val titleMemo = DhenType.memo()
	private val lineMemos = Array(MAX_LINES) { DhenType.memo() }
	private val liveLines = Array(MAX_LINES) { "" }
	private var liveTitle = ""
	private var liveLineCount = 0

	override val hasContent: Boolean
		get() = contentAvailable(editingHud())

	override fun width(font: Font): Int = contentWidth(font, editingHud())

	override fun height(font: Font): Int {
		val count = shownCount(editingHud())
		val lineHeight = DhenType.lineHeight(font)
		return PADDING * 2 + lineHeight + TITLE_GAP + count * (lineHeight + LINE_GAP)
	}

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val editing = editingHud()
		val title = shownTitle(editing)
		val lines = shownLines(editing)
		val count = shownCount(editing)
		val width = contentWidth(font, editing)
		val height = height(font)
		val lineHeight = DhenType.lineHeight(font)
		GlassGui.roundedFrame(graphics, 0, 0, width, height, RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		SharpGui.fill(graphics, ACCENT_INSET, 1, width - ACCENT_INSET, 1 + ACCENT_HEIGHT, DhenPalette.accent)
		titleMemo.text(
			graphics,
			font,
			title,
			(width - titleMemo.width(font, title)) / 2,
			PADDING,
			DhenPalette.TEXT_PRIMARY
		)
		val firstLineY = PADDING + lineHeight + TITLE_GAP
		for (index in 0 until count) {
			lineMemos[index].text(
				graphics,
				font,
				lines[index],
				PADDING,
				firstLineY + index * (lineHeight + LINE_GAP),
				DhenPalette.TEXT_PRIMARY
			)
		}
	}

	override fun invalidateMeasurement() {
		titleMemo.invalidate()
		for (memo in lineMemos) memo.invalidate()
	}

	internal fun update(title: String, lines: List<String>) {
		liveTitle = title
		liveLineCount = minOf(lines.size, MAX_LINES)
		for (index in 0 until liveLineCount) liveLines[index] = lines[index]
	}

	internal fun contentAvailable(editing: Boolean): Boolean = editing || liveLineCount > 0

	internal fun shownTitle(editing: Boolean): String = if (editing) PREVIEW_TITLE else liveTitle

	internal fun shownLine(index: Int, editing: Boolean): String = shownLines(editing)[index]

	internal fun shownCount(editing: Boolean): Int = if (editing) PREVIEW_LINES.size else liveLineCount

	private fun contentWidth(font: Font, editing: Boolean): Int {
		val title = shownTitle(editing)
		val lines = shownLines(editing)
		val count = shownCount(editing)
		var width = titleMemo.width(font, title)
		for (index in 0 until count) width = maxOf(width, lineMemos[index].width(font, lines[index]))
		return width + PADDING * 2
	}

	private fun shownLines(editing: Boolean): Array<String> = if (editing) PREVIEW_LINES else liveLines

	private companion object {
		const val MAX_LINES = 40
		const val RIGHT_MARGIN = 5
		const val PADDING = 8
		const val TITLE_GAP = 4
		const val LINE_GAP = 2
		const val ACCENT_HEIGHT = 2
		const val ACCENT_INSET = 4
		const val RADIUS = 4f
		const val PREVIEW_TITLE = "SKYBLOCK"
		val PREVIEW_LINES = arrayOf(
			"§7Late Summer 1st",
			"§7⏣ §bVillage",
			"§fPurse: §612,345,678 §7(§6+1,000§7)",
			"§fBits: §b2,400"
		)
	}
}
