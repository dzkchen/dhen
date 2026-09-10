package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TablistState
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.social.SocialRosters
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import java.util.function.Function
import kotlin.random.Random

object CompactTabList : Module(
	name = "Compact Tab List",
	category = Category.VISUAL,
	description = "Replaces the SkyBlock tab list with compact sections and configurable player names."
) {
	private val toggle = BooleanSetting("Toggle Tab")
	private val adverts = BooleanSetting("Hide Hypixel Adverts")
	private val fireSales = BooleanSetting("Hide Fire Sale Adverts")
	private val background = BooleanSetting("Hide Tab Background")
	private val sort = SelectorSetting("Player Sort", "Rank (Default)", listOf("Rank (Default)", "SB Level", "Name (Abc)", "Ironman-Bingo", "Party-Friends-Guild", "Random"))
	private val invert = BooleanSetting("Invert Sort")
	private val icons = BooleanSetting("Hide Player Icons")
	private val rank = BooleanSetting("Hide Rank Color")
	private val emblems = BooleanSetting("Hide Emblems")
	private val level = BooleanSetting("Hide Level")
	private val brackets = BooleanSetting("Hide Level Brackets")
	private val levelColor = BooleanSetting("Level Color As Name")
	private val bingoNumber = BooleanSetting("Bingo Rank Number")
	private val factions = BooleanSetting("Hide Factions")
	private val special = BooleanSetting("Mark Special Persons")
	private val flags = arrayOf(adverts, fireSales, background, invert, icons, rank, emblems, level, brackets, levelColor, bingoNumber, factions, special)
	private val latch = TabToggle()
	private val random = HashMap<String, RandomOrder>()
	private var columns: List<List<DrawRow>> = emptyList()
	private var header: List<DrawRow> = emptyList()
	private var footer: List<DrawRow> = emptyList()
	private var widths = IntArray(0)
	private var flagsSeen = -1
	private var sortSeen = ""
	private var refreshTicks = 0
	private var visible = false
	private var dirty = true

	init {
		registerSetting(toggle)
		registerSetting(sort)
		for (flag in flags) registerSetting(flag)
		on<TablistUpdateEvent> { dirty = true }
		on<WorldChangeEvent> { forget() }
		on<ClientTickEvent.End> { tick() }
	}

	override fun onEnabled() { dirty = true }

	override fun onDisabled() = forget()

	private fun forget() {
		latch.reset()
		visible = false
		columns = emptyList()
		header = emptyList()
		footer = emptyList()
		random.clear()
		dirty = true
	}

	private fun tick() {
		val client = Minecraft.getInstance()
		if (!SkyBlockLocation.inSkyBlock) {
			if (visible || columns.isNotEmpty()) forget()
			return
		}
		visible = latch.update(client.options.keyPlayerList.isDown, client.gui.screen() != null, toggle.on)
		var bits = 0
		for (index in flags.indices) if (flags[index].on) bits = bits or (1 shl index)
		if (bits != flagsSeen || sort.value != sortSeen) {
			flagsSeen = bits
			sortSeen = sort.value
			dirty = true
		}
		if (++refreshTicks >= REFRESH_TICKS) {
			refreshTicks = 0
			dirty = true
		}
		if (dirty) rebuild()
	}

	private fun rebuild() {
		dirty = false
		val client = Minecraft.getInstance()
		val advanced = SkyBlockLocation.island != Island.CATACOMBS && SkyBlockLocation.island != Island.KUUDRA
		val players = HashMap<String, CompactTabPlayer>()
		val sorted = if (advanced) sorted(TablistState.lines, players) else TablistState.lines
		columns = CompactTabModel.columns(sorted, TablistState.footer, fireSales.on).map { rows ->
			rows.map { row ->
				val player = row.player?.let(players::get)
				val text = if (player == null) row.text else formatted(player)
				DrawRow(text, row.title, if (row.player != null && !icons.on) client.connection?.getPlayerInfo(row.player) else null)
			}
		}
		header = CompactTabModel.frame(TablistState.header, adverts.on).map { DrawRow(it) }
		footer = CompactTabModel.frame(TablistState.footer, adverts.on).map { DrawRow(it) }
		widths = IntArray(columns.size)
	}

	private fun sorted(lines: List<String>, into: MutableMap<String, CompactTabPlayer>): List<String> {
		if (lines.isEmpty()) return lines
		val found = ArrayList<CompactTabPlayer>()
		var titles = 0
		var end = 1
		while (end < lines.size) {
			val line = lines[end]
			if (line.isEmpty() || line.contains("Server Info") || line == "               Info") break
			if (line.contains("Players")) titles++ else {
				val player = CompactTabPlayer.read(line, SkyBlockLocation.island == Island.CRIMSON_ISLE, ::bingo)
				if (player != null) {
					found += player
					into[player.name] = player
				}
			}
			end++
		}
		when (sort.value) {
			"SB Level" -> found.sortByDescending { it.level }
			"Name (Abc)" -> found.sortBy { it.sortName }
			"Ironman-Bingo" -> found.sortByDescending { if (it.ironman) 10 else it.bingo }
			"Party-Friends-Guild" -> found.sortByDescending { social(it.name) }
			"Random" -> found.sortBy { randomOrder(it.name) }
		}
		if (invert.on) found.reverse()
		val rebuilt = ArrayList<String>(lines.size)
		rebuilt += lines.first()
		for (index in found.indices) {
			if (index > 0 && index % SOURCE_PLAYER_ROWS == 0 && titles > 0) {
				rebuilt += lines.first()
				titles--
			}
			rebuilt += found[index].original
		}
		rebuilt.addAll(lines.subList(end, lines.size))
		return rebuilt
	}

	private fun formatted(player: CompactTabPlayer): String {
		val parts = ArrayList<String>()
		if (!level.on) parts += if (brackets.on) player.levelText else "§8[${player.levelText}§8]"
		parts += when {
			levelColor.on -> lastColor(player.levelText) + player.name
			rank.on -> "§b${player.name}"
			else -> player.coloredName
		}
		if (!emblems.on) parts += player.suffix else if (player.ironman) parts += "§7♲" else if (player.bingo >= 0) {
			parts += BINGO_ICONS[player.bingo] + if (bingoNumber.on) " ${player.bingo}" else ""
		}
		if (!factions.on && player.faction.isNotEmpty()) parts += player.faction
		if (special.on) parts += when (social(player.name)) {
			8 -> MarkedPlayers.tabColor() + "§lMARKED"
			5 -> "§9§lP"
			4 -> "§d§lF"
			3 -> "§2§lG"
			else -> ""
		}
		return parts.filter(String::isNotEmpty).joinToString(" ")
	}

	private fun social(name: String): Int = when {
		name == Minecraft.getInstance().user.name -> 10
		MarkedPlayers.markedInTab(name) -> 8
		name in PartyState.members -> 5
		SocialRosters.friends.any { it.equals(name, true) } -> 4
		SocialRosters.guild.any { it.equals(name, true) } -> 3
		else -> 1
	}

	private fun randomOrder(name: String): Int {
		val now = System.currentTimeMillis()
		val held = random[name]
		if (held != null && now < held.until) return held.order
		val fresh = RandomOrder(Random.nextInt(RANDOM_BOUND), now + RANDOM_DURATION)
		random[name] = fresh
		return fresh.order
	}

	internal fun replacement(failsafe: Failsafe): Function<FabricHudElement, FabricHudElement> {
		val wrapper = TabLayer(failsafe)
		return Function { wrapper.bind(it) }
	}

	private class TabLayer(private val failsafe: Failsafe) : FabricHudElement {
		private var vanilla: FabricHudElement? = null

		fun bind(element: FabricHudElement): FabricHudElement {
			vanilla = element
			return this
		}

		override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: net.minecraft.client.DeltaTracker) {
			val original = vanilla ?: return
			vanilla = null
			if (!enabled || !SkyBlockLocation.inSkyBlock || failsafe.failed) original.extractRenderState(graphics, deltaTracker)
			else failsafe.guard("compact tab list") { if (visible) draw(graphics) }
		}
	}

	private fun draw(graphics: GuiGraphicsExtractor) {
		val font = Minecraft.getInstance().font
		var total = 0
		var rows = 0
		for (index in columns.indices) {
			var width = 0
			val column = columns[index]
			for (rowIndex in column.indices) {
				val row = column[rowIndex]
				width = maxOf(width, row.memo.width(font, row.text) + if (row.info == null) 4 else 10)
			}
			widths[index] = width
			total += width + COLUMN_GAP
			rows = maxOf(rows, column.size)
		}
		if (total > 0) total -= COLUMN_GAP
		var panel = total
		for (index in header.indices) panel = maxOf(panel, header[index].memo.width(font, header[index].text))
		for (index in footer.indices) panel = maxOf(panel, footer[index].memo.width(font, footer[index].text))
		val lineHeight = DhenType.lineHeight(font)
		val top = 10
		val columnTop = top + header.size * lineHeight
		val footerTop = columnTop + rows * lineHeight + PADDING
		val left = (graphics.guiWidth() - panel) / 2
		if (!background.on) SharpGui.fill(graphics, left - PADDING, top - PADDING, left + panel + PADDING,
			footerTop + footer.size * lineHeight + PADDING, DhenPalette.TAB_BACKGROUND)
		for (index in header.indices) centered(graphics, header[index], top + index * lineHeight)
		var x = (graphics.guiWidth() - total) / 2
		for (index in columns.indices) {
			val column = columns[index]
			SharpGui.fill(graphics, x, columnTop, x + widths[index], columnTop + column.size * lineHeight,
				if (background.on) DhenPalette.TAB_COLUMN_DARK else DhenPalette.TAB_COLUMN)
			for (rowIndex in column.indices) {
				val row = column[rowIndex]
				val y = columnTop + rowIndex * lineHeight
				val info = row.info
				if (info != null) PlayerFaceExtractor.extractRenderState(graphics, info.skin.body().texturePath(), x, y, 8, info.showHat(), false, DhenPalette.TEXT_PRIMARY)
				val inset = if (info == null) 2 else 10
				val rowX = if (row.title) x + (widths[index] - row.memo.width(font, row.text)) / 2 else x + inset
				row.memo.shadowed(graphics, font, row.text, rowX, y, DhenPalette.TEXT_PRIMARY, 1f)
			}
			x += widths[index] + COLUMN_GAP
		}
		for (index in footer.indices) centered(graphics, footer[index], footerTop + index * lineHeight)
	}

	private fun centered(graphics: GuiGraphicsExtractor, row: DrawRow, y: Int) {
		val font = Minecraft.getInstance().font
		row.memo.shadowed(graphics, font, row.text, (graphics.guiWidth() - row.memo.width(font, row.text)) / 2, y, DhenPalette.TEXT_PRIMARY, 1f)
	}

	private class DrawRow(val text: String, val title: Boolean = false, val info: net.minecraft.client.multiplayer.PlayerInfo? = null) {
		val memo = DhenType.memo()
	}

	private class RandomOrder(val order: Int, val until: Long)

	private fun bingo(line: String): Int = BINGO_ICONS.indexOfFirst(line::contains)

	private fun lastColor(text: String): String {
		for (index in text.length - 2 downTo 0) if (text[index] == '§' && text[index + 1] in "0123456789abcdef") return text.substring(index, index + 2)
		return "§b"
	}

	private val BINGO_ICONS = arrayOf("§7Ⓑ", "§aⒷ", "§9Ⓑ", "§5Ⓑ", "§6Ⓑ")
	private const val RANDOM_BOUND = 500
	private const val RANDOM_DURATION = 1_200_000L
	private const val SOURCE_PLAYER_ROWS = 19
	private const val REFRESH_TICKS = 20
	private const val COLUMN_GAP = 6
	private const val PADDING = 3
}
