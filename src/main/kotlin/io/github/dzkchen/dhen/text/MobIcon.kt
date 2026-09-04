package io.github.dzkchen.dhen.text

internal enum class MobIcon(glyph: Char, val label: String, val short: String) {
	UNDEAD('\uE084', "Undead", "UND"),
	SKELETAL('\uE081', "Skeletal", "SKEL"),
	ENDER('\uE078', "Ender", "END"),
	ATHROPOD('\uE074', "Athropod", "ATHR"),
	HUMANOID('\uE07B', "Humanoid", "HUM"),
	INFERNAL('\uE07C', "Infernal", "INF"),
	CUBIC('\uE076', "Cubic", "CUB"),
	FROZEN('\uE079', "Frozen", "FRO"),
	SPOOKY('\uE082', "Spooky", "BOO"),
	MYTHOLOGICAL('\uE07E', "Mythological", "MYTH"),
	WITHER('\uE085', "Wither", "WITH"),
	SUBTERRANEAN('\uE083', "Subterranean", "SUB"),
	AQUATIC('\uE072', "Aquatic", "AQUA"),
	PEST('\uE018', "Pest", "PEST"),
	ANIMAL('\uE071', "Animal", "ANI"),
	MAGMATIC('\uE07D', "Magmatic", "MAGM"),
	ELUSIVE('\uE077', "Elusive", "ELUS"),
	CONSTRUCT('\uE075', "Construct", "CONST"),
	ARCANE('\uE073', "Arcane", "ARC"),
	SHIELDED('\uE080', "Shielded", "SHIE"),
	AIRBORNE('\uE070', "Airborne", "AIR"),
	GLACIAL('\uE07A', "Glacial", "GLAC"),
	WOODLAND('\uE086', "Woodland", "WOOD"),
	CRITTER('\uE087', "Critter", "CRIT"),
	TIMID('\uE088', "Timid", "TIM");

	val glyph: String = glyph.toString()
}
