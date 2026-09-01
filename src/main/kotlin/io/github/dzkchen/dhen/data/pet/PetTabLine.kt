package io.github.dzkchen.dhen.data.pet

import java.util.regex.Matcher
import java.util.regex.Pattern

internal object PetTabLine {
	private val pet: Matcher = Pattern.compile(
		" \\[Lvl (?<level>[\\d,]+)] (?:\\[\\d+(?<altskin>✦)\\] )?(?<pet>[\\w -]+?)(?:(?<skin> ✦))?$"
	).matcher("")
	private val percentage: Matcher = Pattern.compile(".*\\((?<percentage>[\\d.]+)%\\).*").matcher("")

	fun read(lines: List<String>, stripped: List<String>): TabPet? {
		for (index in stripped.indices) {
			val line = stripped[index]
			if (!pet.reset(line).matches()) continue
			val name = pet.group("pet")
			val level = pet.group("level").replace(",", "").toIntOrNull() ?: return null
			val code = colorAt(lines.getOrElse(index) { line }, pet.start("pet"))
			val tier = tier(code)
			val skin = pet.group("skin") ?: pet.group("altskin")?.let { " $it" }.orEmpty()
			return TabPet("§$code$name$skin", level, tier, progress(stripped, index + 1))
		}
		return null
	}

	private fun progress(lines: List<String>, from: Int): Double {
		for (index in from until lines.size) {
			val line = lines[index]
			if (line.trim() == "MAX LEVEL") return 100.0
			if (percentage.reset(line).matches()) return percentage.group("percentage").toDoubleOrNull() ?: Double.NaN
		}
		return Double.NaN
	}

	private fun colorAt(styled: String, target: Int): Char {
		var visible = 0
		var color = 'f'
		var index = 0
		while (index < styled.length) {
			if (styled[index] == '§' && index + 1 < styled.length) {
				val code = styled[index + 1].lowercaseChar()
				if (code in "0123456789abcdef") color = code
				index += 2
				continue
			}
			if (visible >= target) return color
			visible++
			index++
		}
		return color
	}

	private fun tier(code: Char): String = when (code) {
		'a' -> "UNCOMMON"
		'9' -> "RARE"
		'5' -> "EPIC"
		'6' -> "LEGENDARY"
		'd' -> "MYTHIC"
		'b' -> "DIVINE"
		'c' -> "SPECIAL"
		'4' -> "SUPREME"
		else -> "COMMON"
	}
}

internal class TabPet(val styledName: String, val level: Int, val tier: String, val progress: Double)
