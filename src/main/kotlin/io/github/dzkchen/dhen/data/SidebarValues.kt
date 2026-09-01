package io.github.dzkchen.dhen.data

import java.util.regex.Pattern

object SidebarValues {
	const val ABSENT = -1L

	private const val MIN_UNKNOWN_LENGTH = 4
	private const val SLAYER_BLOCK_LINES = 3

	private val lobbyCodeLine = matcher("\\s*§.(?:\\d{2}/?){3} §8.*")
	private val dateLine = matcher("\\s*(?<date>(?:§.)*(?:(?:Late|Early) )?(?:Spring|Summer|Autumn|Winter) \\d+(?:st|nd|rd|th)?).*")
	private val timeLine = matcher("\\s*(?<time>(?:§.)*\\d+:\\d+(?:am|pm))\\s*(?<symbol>§.[☽☀⚡☔])?\\s*")
	private val purseLine = matcher("\\s*(?:§.)*(?:Piggy|Purse): §6(?<coins>[\\d,.]+).*")
	private val bitsLine = matcher("\\s*(?:§.)*Bits: §b(?<bits>[\\d,.]+).*")
	private val slayerLine = matcher("\\s*(?:§.)*Slayer Quest\\s*")
	private val footerLine = matcher("\\s*§e(?:www|alpha)\\.hypixel\\.net\\s*")
	private val locationLine = matcher("\\s*(?<location>§\\d. §.(?<area>.*?))\\s*")

	private val slayerBlock = ArrayList<String>(SLAYER_BLOCK_LINES)
	private val unknownBlock = ArrayList<String>()
	private var claimed = BooleanArray(0)

	var date: String? = null
		private set

	var time: String? = null
		private set

	var location: String? = null
		private set

	var area: String? = null
		private set

	var purseText: String? = null
		private set

	var purse: Long = ABSENT
		private set

	var bitsText: String? = null
		private set

	var bits: Long = ABSENT
		private set

	var footer: String? = null
		private set

	val slayer: List<String> get() = slayerBlock

	val unknown: List<String> get() = unknownBlock

	internal fun read(lines: List<String>) {
		reset()
		if (claimed.size < lines.size) claimed = BooleanArray(lines.size)
		for (index in lines.indices) claim(lines, index)
		for (index in lines.indices) {
			if (claimed[index] || lines[index].trim().length < MIN_UNKNOWN_LENGTH) continue
			unknownBlock += lines[index].removePrefix(" ")
		}
	}

	internal fun reset() {
		date = null
		time = null
		location = null
		area = null
		purseText = null
		purse = ABSENT
		bitsText = null
		bits = ABSENT
		footer = null
		slayerBlock.clear()
		unknownBlock.clear()
		claimed.fill(false)
	}

	private fun claim(lines: List<String>, index: Int) {
		if (claimed[index]) return
		val line = lines[index]
		when {
			lobbyCodeLine.reset(line).matches() -> Unit
			date == null && dateLine.reset(line).matches() -> date = dateLine.group("date")
			time == null && timeLine.reset(line).matches() -> time = timed()
			purse == ABSENT && purseLine.reset(line).matches() -> {
				purseText = purseLine.group("coins")
				purse = digits(purseText)
			}

			bits == ABSENT && bitsLine.reset(line).matches() -> {
				bitsText = bitsLine.group("bits")
				bits = digits(bitsText)
			}

			slayerBlock.isEmpty() && slayerLine.reset(line).matches() ->
				return block(lines, index, SLAYER_BLOCK_LINES, slayerBlock)

			footer == null && footerLine.reset(line).matches() -> footer = line.trim()
			location == null && locationLine.reset(line).matches() -> {
				location = locationLine.group("location")
				area = locationLine.group("area")
			}

			else -> return
		}
		claimed[index] = true
	}

	private fun block(lines: List<String>, index: Int, count: Int, into: MutableList<String>) {
		for (offset in 0 until count) {
			val at = index + offset
			if (at >= lines.size) return
			claimed[at] = true
			into += lines[at].removePrefix(" ")
		}
	}

	private fun timed(): String {
		val text = timeLine.group("time")
		val symbol = timeLine.group("symbol") ?: return text
		return "$text $symbol"
	}

	private fun digits(text: String?): Long {
		var value = ABSENT
		for (character in text ?: return ABSENT) {
			if (!character.isDigit()) continue
			value = (if (value == ABSENT) 0L else value) * 10 + (character - '0')
		}
		return value
	}

	private fun matcher(pattern: String) = Pattern.compile(pattern).matcher("")
}
