package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOTTOM_LEFT
import io.github.dzkchen.dhen.gui.SLOT_BOTTOM_RIGHT
import io.github.dzkchen.dhen.gui.SLOT_TOP_LEFT
import io.github.dzkchen.dhen.gui.SLOT_TOP_RIGHT
import io.github.dzkchen.dhen.util.NOT_ROMAN
import io.github.dzkchen.dhen.util.romanValue
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.core.component.DataComponents
import net.minecraft.references.BlockItemIds
import net.minecraft.world.item.Items
import java.util.regex.Pattern

internal val SLOT_ADDERS = arrayOf(
	SlotAdder("Essence Shop", "The perk level and your essence in an essence shop.", ".*Essence Shop", ::essenceShop),
	SlotAdder("Enchant Abbreviation", "A short code for the enchantment on a book.", write = ::enchantAbbreviation),
	SlotAdder("Enchant Level", "The level of the enchantment on a book.", write = ::enchantLevel),
	SlotAdder("Minion Level", "The tier of a minion, written as a number.", write = ::minionLevel),
	SlotAdder("Pet Level", "The level of a pet.", write = ::petLevel),
	SlotAdder("Choose Pet Level", "The level of each pet in the Choose Pet menu.", CHOOSE_PET_TITLE, ::choosePetLevel),
	SlotAdder("SkyBlock Level", "Your SkyBlock level in the SkyBlock Menu.", "SkyBlock Menu", ::skyblockLevel),
	SlotAdder("Mountain Perk Level", "The level of each Heart of the Mountain perk.", HOTM_TITLE, ::mountainPerk),
	SlotAdder("Forest Perk Level", "The level of each Heart of the Forest perk.", HOTF_TITLE, ::forestPerk),
	SlotAdder("Skill Level", "The level of each skill in Your Skills.", "Your Skills", ::skillLevel),
	SlotAdder("Dungeoneering Level", "The catacombs level in the Dungeoneering menu.", "Dungeoneering", ::dungeoneering),
	SlotAdder("Dungeon Class Level", "The level of each dungeon class.", "Dungeon Classes", ::dungeonClasses),
	SlotAdder("Ready Up Level", "The level of each dungeon class in Ready Up.", "Ready Up", ::readyUp),
	SlotAdder("Rancher's Boots Speed", "The speed cap set on Rancher's Boots.", write = ::rancherBoots),
	SlotAdder("Prehistoric Egg", "How far you have walked with a Prehistoric Egg.", write = ::prehistoricEgg),
	SlotAdder("Potion Level", "The level of a potion.", write = ::potionLevel),
	SlotAdder("Collection Tier", "The tier of each collection.", "\\w+ Collections", ::collectionTier),
	SlotAdder("Community Shop", "The tier of each Community Shop upgrade.", "Community Shop", ::communityShop),
	SlotAdder("Your Essence", "How much of each essence you own.", ESSENCE_MENU_TITLE, ::yourEssence),
	SlotAdder("Power Stone Guide", "Whether each power stone is learned.", "Power Stones Guide", ::powerStones),
	SlotAdder("Stats Tuning", "Points spent and points left in Stats Tuning.", "Stats Tuning", ::statsTuning),
	SlotAdder("Evolving Item Bonus", "The bonus an evolving item has built up.", write = ::evolvingItem),
	SlotAdder("New Year Cake Year", "The year of a New Year Cake.", write = ::newYearCake),
	SlotAdder("SkyBlock Guide", "Whether each SkyBlock Guide task is done.", GUIDE_TITLE, ::skyblockGuide),
	SlotAdder("Attribute Level", "The level of each attribute in the Attribute Menu.", ATTRIBUTE_TITLE, ::attributeLevel),
	SlotAdder("Bestiary Level", "The tier of each bestiary entry.", BESTIARY_TITLE, ::bestiaryLevel),
	SlotAdder("Hunting Toolkit", "A mark on every item that belongs to a toolkit.", write = ::huntingToolkit),
	SlotAdder("Chip Level", "The level of each chip in Manage Chips.", "Manage Chips", ::chipLevel),
	SlotAdder("Crop Milestone", "The milestone of each crop.", "Crop Milestones", ::cropMilestone),
	SlotAdder("Garden Upgrade", "The tier of each garden upgrade.", GARDEN_TITLE, ::gardenUpgrade),
	SlotAdder("Bottle Charge", "How charged a Thunder, Storm or Hurricane Bottle is.", write = ::bottleCharge),
	SlotAdder("Moby-Duck Progress", "How close a Moby-Duck is to evolving.", write = ::mobyDuckProgress),
	SlotAdder("Auto-Recombobulated", "An R on a fishing drop that dropped already recombobulated.", write = ::autoRecombFlag),
	SlotAdder("Item Stars", "The star count of an upgraded item, where its stack size would be.", write = ::itemStars)
)

private fun essenceShop(scribe: SlotScribe) {
	if (scribe.slotId > MENU_LAST_SLOT) return
	if (ESSENCE_PERK.reset(scribe.name).matches()) {
		val level = romanValue(ESSENCE_PERK.group(1))
		if (level == NOT_ROMAN) return
		val owned = if (scribe.loreIs(UNLOCKED)) level else level - 1
		scribe.write(SLOT_BOTTOM_RIGHT, owned.toString(), DhenPalette.SLOT_CREAM)
		return
	}
	val matcher = scribe.loreMatch(ESSENCE_OWNED) ?: return
	scribe.write(SLOT_BOTTOM_RIGHT, essenceAmount(matcher.group(1)) ?: return, DhenPalette.SLOT_CREAM)
}

private fun enchantAbbreviation(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.ENCHANTED_BOOK) || scribe.name != PLAIN_BOOK) return
	val enchantment = scribe.item.enchantments.keys.singleOrNull() ?: return
	val normal = ENCHANT_CODES[enchantment]
	if (normal != null) {
		scribe.write(SLOT_TOP_RIGHT, normal, DhenPalette.SLOT_ENCHANT)
		return
	}
	scribe.write(SLOT_TOP_RIGHT, ULTIMATE_CODES[enchantment] ?: return, DhenPalette.SLOT_ULTIMATE)
}

private fun enchantLevel(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.ENCHANTED_BOOK)) return
	val level = if (scribe.name == PLAIN_BOOK) {
		scribe.item.enchantments.values.singleOrNull() ?: return
	} else {
		romanValue(scribe.name.substringAfterLast(' '))
	}
	if (level <= 0) return
	scribe.write(SLOT_BOTTOM_LEFT, level.toString(), DhenPalette.SLOT_CREAM)
}

private fun minionLevel(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.PLAYER_HEAD) || !MINION_NAME.reset(scribe.name).matches()) return
	val tier = romanValue(MINION_NAME.group(1))
	if (tier == NOT_ROMAN) return
	scribe.write(SLOT_TOP_RIGHT, tier.toString(), DhenPalette.SLOT_CREAM)
}

private fun petLevel(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.PLAYER_HEAD) || scribe.item.id != PET) return
	if (!PET_NAME.reset(scribe.name).matches()) return
	val level = PET_NAME.group(1)
	if (topPetLevel(level, scribe.name)) return
	scribe.write(SLOT_TOP_LEFT, level, DhenPalette.SLOT_CREAM)
}

private fun choosePetLevel(scribe: SlotScribe) {
	if (scribe.slotId < CHOOSE_PET_FIRST || scribe.slotId > MENU_LAST_ROW_SLOT) return
	if (!scribe.stack.`is`(Items.PLAYER_HEAD) || scribe.loreIs(EXCEPT_IF)) return
	val level = when {
		scribe.loreMatch(AUTOPET_NAME) != null -> AUTOPET_NAME.group(1)
		PET_NAME.reset(scribe.name).matches() -> PET_NAME.group(1)
		else -> return
	}
	if (topPetLevel(level, scribe.name)) return
	scribe.write(SLOT_BOTTOM_RIGHT, level, DhenPalette.SLOT_CREAM)
}

private fun topPetLevel(level: String, name: String): Boolean = when (level) {
	MAX_PET_LEVEL -> !name.contains(DRAGON) || name.contains(ENDER_DRAGON)
	MAX_DRAGON_LEVEL -> true
	else -> false
}

private fun skyblockLevel(scribe: SlotScribe) {
	if (scribe.slotId != SKYBLOCK_LEVEL_SLOT) return
	val siblings = scribe.loreStyled(0)?.siblings ?: return
	if (siblings.size < SKYBLOCK_LEVEL_PARTS) return
	val level = siblings[SKYBLOCK_LEVEL_PART].string
	if (level.isEmpty() || level.any { it !in '0'..'9' }) return
	scribe.write(SLOT_BOTTOM_LEFT, level, DhenPalette.SLOT_CREAM)
}

private fun mountainPerk(scribe: SlotScribe) {
	if (scribe.stack.`is`(Items.COAL)) return
	perkLevel(scribe)
}

private fun forestPerk(scribe: SlotScribe) {
	if (scribe.stack.get(DataComponents.ITEM_MODEL) == PALE_OAK_BUTTON) return
	perkLevel(scribe)
}

private fun perkLevel(scribe: SlotScribe) {
	if (scribe.slotId > MENU_LAST_ROW_SLOT) return
	if (!PERK_LEVEL.reset(scribe.lore(0)).matches()) return
	val maxed = PERK_LEVEL.group(2) == null
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		PERK_LEVEL.group(1),
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun skillLevel(scribe: SlotScribe) {
	val row = scribe.slotId / ROW_SLOTS
	if (row < 1 || row > SKILL_LAST_ROW) return
	if (scribe.stack.`is`(Items.STAINED_GLASS_PANE.black()) || scribe.name == DUNGEONEERING) return
	val space = scribe.name.lastIndexOf(' ')
	if (space < 0) {
		scribe.write(SLOT_BOTTOM_LEFT, ZERO, DhenPalette.SLOT_PURPLE)
		return
	}
	val written = scribe.name.substring(space + 1)
	val level = romanValue(written).takeIf { it != NOT_ROMAN } ?: written.toIntOrNull() ?: 0
	val maxed = scribe.loreHas(MAX_SKILL)
	scribe.write(
		SLOT_BOTTOM_LEFT,
		level.toString(),
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun dungeoneering(scribe: SlotScribe) {
	if (scribe.slotId != DUNGEONEERING_SLOT && scribe.slotId !in DUNGEON_CLASS_SLOTS) return
	if (!DUNGEONEERING_NAME.reset(scribe.name).matches()) return
	val arabic = DUNGEONEERING_NAME.group(1)
	val roman = DUNGEONEERING_NAME.group(2)
	val level = when {
		arabic != null -> arabic.toIntOrNull() ?: return
		roman != null -> romanValue(roman).takeIf { it != NOT_ROMAN } ?: return
		else -> 0
	}
	scribe.write(SLOT_BOTTOM_LEFT, level.toString(), DhenPalette.SLOT_CREAM)
}

private fun dungeonClasses(scribe: SlotScribe) {
	if (scribe.slotId !in CLASS_MENU_SLOTS) return
	bracketedLevel(scribe)
}

private fun readyUp(scribe: SlotScribe) {
	if (scribe.slotId !in DUNGEON_CLASS_SLOTS) return
	bracketedLevel(scribe)
}

private fun bracketedLevel(scribe: SlotScribe) {
	if (!scribe.name.startsWith(LEVEL_PREFIX)) return
	val close = scribe.name.indexOf(']')
	if (close < 0) return
	val level = scribe.name.substring(LEVEL_PREFIX.length, close)
	if (level.isEmpty() || level.any { it !in '0'..'9' }) return
	scribe.write(SLOT_BOTTOM_LEFT, level, DhenPalette.SLOT_CREAM)
}

private fun rancherBoots(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.LEATHER_BOOTS) && scribe.item.id != RANCHERS_BOOTS) return
	val matcher = scribe.loreMatch(SPEED_CAP) ?: return
	scribe.write(SLOT_BOTTOM_LEFT, matcher.group(2) ?: matcher.group(1), DhenPalette.SLOT_CREAM)
}

private fun prehistoricEgg(scribe: SlotScribe) {
	if (scribe.item.id != PREHISTORIC_EGG) return
	val walked = scribe.item.blocksWalked
	if (walked == SkyBlockItem.NOT_WALKED) return
	scribe.write(SLOT_BOTTOM_LEFT, shortNumber(walked.toLong()), DhenPalette.SLOT_CREAM)
}

private fun potionLevel(scribe: SlotScribe) {
	val level = scribe.item.potionLevel
	if (level <= 0 || scribe.name.contains(HEALER) || scribe.name.contains(CLASS_PASSIVES)) return
	scribe.write(SLOT_BOTTOM_RIGHT, level.toString(), DhenPalette.SLOT_CREAM)
}

private fun collectionTier(scribe: SlotScribe) {
	if (scribe.slotId > MENU_LAST_SLOT || !COLLECTION_NAME.reset(scribe.name).matches()) return
	val tier = romanValue(COLLECTION_NAME.group(1))
	if (tier == NOT_ROMAN) return
	val maxed = !scribe.loreHas(COLLECTION_PROGRESS)
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		tier.toString(),
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun communityShop(scribe: SlotScribe) {
	if (scribe.slotId in SHOP_CATEGORY_SLOTS && scribe.stack.`is`(Items.STAINED_GLASS_PANE.lime())) {
		shopCategory = scribe.slotId - SHOP_CATEGORY_SLOTS.first
		return
	}
	if (shopCategory != SHOP_UPGRADES || scribe.slotId !in SHOP_UPGRADE_SLOTS) return
	val tier = romanValue(scribe.name.substringAfterLast(' '))
	if (tier == NOT_ROMAN) return
	when (scribe.lore(scribe.loreSize - 1)) {
		SHOP_MAXED -> scribe.write(SLOT_BOTTOM_LEFT, MAXED_MARK, DhenPalette.SLOT_ORANGE)
		SHOP_UPGRADING, SHOP_INSTANT -> scribe.write(SLOT_BOTTOM_LEFT, CLOCK_MARK, DhenPalette.SLOT_YELLOW)
		SHOP_CLAIM -> scribe.write(SLOT_BOTTOM_LEFT, TICK_MARK, DhenPalette.SLOT_CLAIM)
		else -> scribe.write(SLOT_BOTTOM_LEFT, tier.toString(), DhenPalette.SLOT_PURPLE)
	}
}

private fun yourEssence(scribe: SlotScribe) {
	if (!scribe.name.contains(ESSENCE)) return
	val owned = scribe.loreFind(ESSENCE_OWNED_FIND, 0)
		?: scribe.loreFind(ESSENCE_OWNED, scribe.loreSize - ESSENCE_GUIDE_OFFSET)
		?: return
	scribe.write(SLOT_BOTTOM_RIGHT, essenceAmount(owned.group(1)) ?: return, DhenPalette.SLOT_CREAM)
}

private fun powerStones(scribe: SlotScribe) {
	val matcher = scribe.loreMatch(POWER_LEARNED) ?: return
	writeMark(scribe, matcher.group(2), SLOT_BOTTOM_RIGHT)
}

private fun statsTuning(scribe: SlotScribe) {
	if (scribe.name == STATS_TUNING) {
		val unassigned = scribe.loreMatch(UNASSIGNED_POINTS) ?: return
		scribe.write(SLOT_BOTTOM_RIGHT, unassigned.group(1), DhenPalette.SLOT_CREAM)
		return
	}
	val assigned = scribe.loreMatch(ASSIGNED_POINTS) ?: return
	val points = assigned.group(1)
	if (points == ZERO) return
	scribe.write(SLOT_BOTTOM_RIGHT, points, DhenPalette.SLOT_CREAM)
}

private fun evolvingItem(scribe: SlotScribe) {
	val label = EVOLVING_LABELS[scribe.item.id] ?: return
	for (index in 0 until scribe.loreSize) {
		val siblings = scribe.loreStyled(index)?.siblings ?: continue
		if (siblings.size < 2 || siblings[0].string != label) continue
		val bonus = siblings[1]
		if (!EVOLVING_BONUS.reset(bonus.string).find()) return
		val ink = bonus.style.color?.value?.let { OPAQUE or it } ?: DhenPalette.SLOT_CREAM
		scribe.write(SLOT_BOTTOM_LEFT, EVOLVING_BONUS.group(1), ink)
		return
	}
}

private fun newYearCake(scribe: SlotScribe) {
	if (!scribe.stack.`is`(Items.CAKE)) return
	val year = scribe.item.newYearCake
	if (year <= 0) return
	scribe.write(SLOT_BOTTOM_LEFT, year.toString(), DhenPalette.SLOT_BLUE)
}

private fun skyblockGuide(scribe: SlotScribe) {
	if (scribe.slotId < GUIDE_FIRST_SLOT || scribe.slotId > MENU_LAST_ROW_SLOT) return
	if (!GUIDE_NAME.reset(scribe.name).matches()) return
	writeMark(scribe, GUIDE_NAME.group(1), SLOT_BOTTOM_RIGHT)
}

private fun attributeLevel(scribe: SlotScribe) {
	if (scribe.slotId < CHOOSE_PET_FIRST || scribe.slotId > ATTRIBUTE_LAST_SLOT) return
	if (scribe.stack.`is`(Items.STAINED_GLASS_PANE.black()) || scribe.stack.customName == null) return
	val level = romanValue(scribe.name.substringAfterLast(' '))
	if (level < 1 || level > MAX_ATTRIBUTE_LEVEL) return
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		level.toString(),
		if (level == MAX_ATTRIBUTE_LEVEL) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun bestiaryLevel(scribe: SlotScribe) {
	val column = scribe.slotId % ROW_SLOTS
	val row = scribe.slotId / ROW_SLOTS
	if (row < 1 || row > BESTIARY_LAST_ROW || column < 1 || column > BESTIARY_LAST_COLUMN) return
	if (scribe.stack.`is`(Items.DYE.gray()) || !BESTIARY_NAME.reset(scribe.name).matches()) return
	val tier = romanValue(BESTIARY_NAME.group(1))
	if (tier == NOT_ROMAN) return
	val maxed = scribe.loreHas(BESTIARY_DONE)
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		tier.toString(),
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun huntingToolkit(scribe: SlotScribe) {
	if (!scribe.loreHas(TOOLKIT_LINE)) return
	scribe.write(SLOT_TOP_LEFT, TOOLKIT_MARK, DhenPalette.SLOT_TOOLKIT)
}

private fun chipLevel(scribe: SlotScribe) {
	if (scribe.slotId !in CHIP_SLOTS || scribe.stack.`is`(Items.DYE.gray())) return
	if (!PERK_LEVEL.reset(scribe.lore(0)).matches()) return
	val level = PERK_LEVEL.group(1)
	val cap = PERK_LEVEL.group(2)
	val ink = if (level != cap) DhenPalette.SLOT_CREAM else when (cap) {
		CHIP_CAP_TEN -> DhenPalette.SLOT_CHIP_TEN
		CHIP_CAP_FIFTEEN -> DhenPalette.SLOT_CHIP_FIFTEEN
		CHIP_CAP_TWENTY -> DhenPalette.SLOT_GOLD
		else -> DhenPalette.SLOT_CREAM
	}
	scribe.write(SLOT_BOTTOM_RIGHT, level, ink)
}

private fun cropMilestone(scribe: SlotScribe) {
	if (scribe.slotId > MENU_LAST_SLOT) return
	val space = scribe.name.lastIndexOf(' ')
	if (space < 0) return
	val milestone = scribe.name.substring(space + 1)
	if (milestone.isEmpty() || milestone.any { it !in '0'..'9' }) return
	val maxed = scribe.loreHas(CROP_MAXED)
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		milestone,
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun gardenUpgrade(scribe: SlotScribe) {
	if (scribe.slotId > MENU_LAST_SLOT) return
	val matcher = scribe.loreMatch(GARDEN_TIER) ?: return
	val tier = matcher.group(1)
	val maxed = tier == matcher.group(2)
	scribe.write(
		SLOT_BOTTOM_RIGHT,
		tier,
		if (maxed) DhenPalette.SLOT_GOLD else DhenPalette.SLOT_CREAM
	)
}

private fun bottleCharge(scribe: SlotScribe) {
	val capacity = BOTTLE_CAPACITY[scribe.item.id] ?: return
	scribe.write(SLOT_BOTTOM_LEFT, percentOf(scribe.item.thunderCharge.toLong(), capacity), DhenPalette.SLOT_BLUE)
}

private fun mobyDuckProgress(scribe: SlotScribe) {
	if (scribe.item.id != MOBY_DUCK) return
	scribe.write(SLOT_BOTTOM_RIGHT, percentOf(scribe.item.secondsHeld.toLong(), MOBY_DUCK_SECONDS), DhenPalette.SLOT_BLUE)
}

private fun autoRecombFlag(scribe: SlotScribe) {
	if (!scribe.item.isRecombobulated || scribe.item.id !in AUTO_RECOMB_DROPS) return
	scribe.write(SLOT_BOTTOM_LEFT, RECOMB_MARK, DhenPalette.SLOT_BLUE)
}

private fun itemStars(scribe: SlotScribe) {
	val stars = scribe.item.upgradeLevel
	if (stars <= 0) return
	scribe.write(SLOT_BOTTOM_RIGHT, stars.toString(), DhenPalette.slotStar(stars, scribe.loreHas(DUNGEON_CATEGORY)))
}

private fun percentOf(amount: Long, capacity: Long): String =
	"${(amount * FULL_PERCENT / capacity).coerceIn(0L, FULL_PERCENT)}%"

private fun writeMark(scribe: SlotScribe, symbol: String, corner: Int) {
	if (symbol == CROSS_SOURCE) {
		scribe.write(corner, CROSS_MARK, DhenPalette.SLOT_RED)
	} else {
		scribe.write(corner, TICK_SOURCE, DhenPalette.SLOT_GREEN)
	}
}

private fun essenceAmount(written: String): String? {
	val amount = written.replace(",", "").toLongOrNull() ?: return null
	return shortNumber(amount)
}

private var shopCategory = NO_SHOP_CATEGORY

private val PALE_OAK_BUTTON = BlockItemIds.PALE_OAK_BUTTON.item().identifier()

private val ESSENCE_PERK = Pattern.compile("[\\w ]+ ([IVXLCDM]+)").matcher("")
private val ESSENCE_OWNED = Pattern.compile("Your \\w+ Essence: ([\\d,]+)").matcher("")
private val ESSENCE_OWNED_FIND = Pattern.compile("You currently own ([\\d,]+)").matcher("")
private val MINION_NAME = Pattern.compile(".* Minion ([IVXLCDM]+)").matcher("")
private val PET_NAME = Pattern.compile("⭐? ?\\[Lvl (\\d+)].*").matcher("")
private val AUTOPET_NAME = Pattern.compile("Equip: ⭐? ?\\[Lvl (\\d+)].*").matcher("")
private val PERK_LEVEL = Pattern.compile("Level (\\d+)/?(\\d+)?").matcher("")
private val DUNGEONEERING_NAME = Pattern.compile(".*?(?:(?: (\\d+)| ([IVXLC]+))(?: ✯)?)?").matcher("")
private val SPEED_CAP = Pattern.compile("Current Speed Cap: (\\d+) ?(\\d+)?").matcher("")
private val COLLECTION_NAME = Pattern.compile("[\\w -]+ ([IVXLCDM]+)").matcher("")
private val POWER_LEARNED = Pattern.compile("Learned: (Yes|Not Yet) ([✖✔])").matcher("")
private val ASSIGNED_POINTS = Pattern.compile("Stat has: (\\d+) (?:points|point)").matcher("")
private val UNASSIGNED_POINTS = Pattern.compile("Unassigned Points: (\\d+)!!!").matcher("")
private val EVOLVING_BONUS = Pattern.compile("\\+?([\\d.]+)").matcher("")
private val GUIDE_NAME = Pattern.compile("([✖✔])\\s*.+").matcher("")
private val BESTIARY_NAME = Pattern.compile("[\\w '-]+ ([IVXLCDM]+)").matcher("")
private val GARDEN_TIER = Pattern.compile("Current Tier: (\\d+)/(\\d+)").matcher("")

private val BOTTLE_CAPACITY = mapOf(
	"THUNDER_IN_A_BOTTLE_EMPTY" to 50_000L,
	"STORM_IN_A_BOTTLE_EMPTY" to 500_000L,
	"HURRICANE_IN_A_BOTTLE_EMPTY" to 5_000_000L
)

private val AUTO_RECOMB_DROPS = setOf(
	"SLUG_BOOTS", "MOOGMA_LEGGINGS", "FLAMING_CHESTPLATE", "TAURUS_HELMET",
	"BLADE_OF_THE_VOLCANO", "STAFF_OF_THE_VOLCANO", "FAIRY_CHESTPLATE", "FAIRY_HELMET",
	"FAIRY_LEGGINGS", "FAIRY_BOOTS", "SQUID_BOOTS", "RABBIT_HAT", "WATER_HYDRA_HEAD",
	"FISH_AFFINITY_TALISMAN", "LUCKY_HOOF", "TIKI_MASK"
)

private val EVOLVING_LABELS = mapOf(
	"NEW_BOTTLE_OF_JYRRE" to "Current Bonus: ",
	"DARK_CACAO_TRUFFLE" to "Current Bonus: ",
	"DISCRITE" to "Current Bonus: ",
	"MOBY_DUCK" to "Current Bonus: ",
	"ROSEWATER_FLASK" to "Current Bonus: ",
	"NOT_VERY_MOLDY_BREAD" to "Current Bonus: ",
	"TRAINING_WEIGHTS" to "Strength Gain: ",
	"BOTTLE_OF_JYRRE" to "Intelligence Bonus: "
)

private val ENCHANT_CODES = mapOf(
	"absorb" to "AB", "angler" to "AN", "aqua_affinity" to "AA", "arcane" to "WS", "aiming" to "DT",
	"bane_of_arthropods" to "BOA", "big_brain" to "BB", "blast_protection" to "BP", "blessing" to "BL",
	"bug_blender" to "BUG", "caster" to "CAT", "cayenne" to "CAY", "champion" to "CHM", "chance" to "CHN",
	"charm" to "CHR", "cleave" to "CL", "compact" to "COM", "corruption" to "COR", "counter_strike" to "CS",
	"critical" to "CR", "cubism" to "CUB", "cultivating" to "CUL", "dedication" to "DED", "delicate" to "DEL",
	"depth_strider" to "DS", "divine_gift" to "DG", "dragon_hunter" to "GV", "efficiency" to "EF",
	"ender_slayer" to "ES", "execute" to "EXE", "experience" to "EXP", "expertise" to "EPR", "feast" to "FE",
	"feather_falling" to "FF", "ferocious_mana" to "VIV", "fire_aspect" to "FA", "fire_protection" to "FPR",
	"first_strike" to "FS", "flame" to "FL", "forest_pledge" to "FPL", "fortune" to "FO", "frail" to "FR",
	"giant_killer" to "GK", "great_spook" to "GS", "green_thumb" to "GT", "growth" to "GR",
	"hardened_mana" to "HV", "harvesting" to "HRV", "hecatomb" to "HEC", "ice_cold" to "IC",
	"impaling" to "IMP", "infinite_quiver" to "IQ", "karma" to "KA", "knockback" to "KB", "lapidary" to "LAP",
	"lethality" to "LE", "life_steal" to "LS", "looting" to "LO", "luck" to "LU", "luck_of_the_sea" to "LTS",
	"lure" to "LR", "magmarizer" to "PY", "magnet" to "MAG", "mana_steal" to "MS", "mana_vampire" to "VV",
	"overload" to "OV", "paleontologist" to "PAL", "pesterminator" to "PS", "petalfall" to "PET",
	"piercing" to "PR", "piscary" to "PSC", "power" to "POW", "pristine" to "PRI",
	"projectile_protection" to "PP", "prosecute" to "PRS", "prosperity" to "PSP", "protection" to "PRO",
	"punch" to "PU", "quantum" to "QUA", "quick_bite" to "QB", "rainbow" to "RA", "reflection" to "REF",
	"rejuvenate" to "RJ", "replenish" to "REP", "respiration" to "RES", "respite" to "RSP",
	"scavenger" to "SCV", "scuba" to "SCU", "sharpness" to "SH", "silk_touch" to "ST", "small_brain" to "SB",
	"smarty_pants" to "SP", "smelting_touch" to "SMT", "smite" to "SMI", "smoldering" to "SML",
	"snipe" to "SN", "spiked_hook" to "SPH", "stealth" to "STL", "strong_mana" to "SV", "sugar_rush" to "SR",
	"syphon" to "DR", "tabasco" to "TAB", "thorns" to "TH", "thunderbolt" to "TB", "thunderlord" to "TL",
	"tidal" to "TD", "titan_killer" to "TK", "toxophilite" to "TX", "transylvanian" to "TRN",
	"triple_strike" to "TS", "true_protection" to "TP", "turbo_cactus" to "TCC", "turbo_cane" to "TCN",
	"turbo_carrot" to "TCR", "turbo_coco" to "TCO", "turbo_melon" to "TME", "turbo_moonflower" to "TMO",
	"turbo_mushrooms" to "TMU", "turbo_potato" to "TPO", "turbo_pumpkin" to "TPU", "turbo_rose" to "TRO",
	"turbo_sunflower" to "TSU", "turbo_warts" to "TWA", "turbo_wheat" to "TWH", "vampirism" to "VMP",
	"venomous" to "VEN", "vicious" to "VIC"
)

private val ULTIMATE_CODES = mapOf(
	"ultimate_bank" to "B", "ultimate_bobbin_time" to "BT", "ultimate_chimera" to "CH",
	"ultimate_combo" to "CO", "ultimate_crop_fever" to "CF", "ultimate_reiterate" to "DU",
	"ultimate_fatal_tempo" to "FT", "ultimate_first_impression" to "FI", "ultimate_flash" to "FL",
	"ultimate_flowstate" to "FLW", "ultimate_habanero_tactics" to "HT", "ultimate_inferno" to "IN",
	"ultimate_last_stand" to "LS", "ultimate_legion" to "L", "ultimate_missile" to "M",
	"ultimate_no_pain_no_gain" to "NP", "ultimate_one_for_all" to "OFA", "ultimate_refrigerate" to "RF",
	"ultimate_rend" to "RN", "ultimate_soul_eater" to "SE", "ultimate_sunset" to "SU",
	"ultimate_swarm" to "SW", "ultimate_the_one" to "TO", "ultimate_jerry" to "UJ",
	"ultimate_wise" to "UW", "ultimate_wisdom" to "W"
)

private val DUNGEON_CLASS_SLOTS = 29..33
private val CLASS_MENU_SLOTS = 11..15
private val SHOP_CATEGORY_SLOTS = 10..14
private val SHOP_UPGRADE_SLOTS = 18..44
private val CHIP_SLOTS = 18..35

private const val CHOOSE_PET_TITLE = "(?:\\(\\d+/\\d+\\) )?Choose Pet"
private const val HOTM_TITLE = "Heart of the Mountain"
private const val HOTF_TITLE = "Heart of the Forest"
private const val ESSENCE_MENU_TITLE = "(?:Your Essence|Essence Guide)"
private const val GUIDE_TITLE = "(?:\\(\\d+/\\d+\\)\\s+)?Guide ➜ \\w+"
private const val ATTRIBUTE_TITLE = "(?:\\(\\d+/\\d+\\) )?Attribute Menu"
private const val BESTIARY_TITLE = "(?:\\(\\d+/\\d+\\) )?(?:Bestiary|Fishing) ➜ .+"
private const val GARDEN_TITLE = "(?:Crop|Greenhouse) Upgrades"

private const val PET = "PET"
private const val PLAIN_BOOK = "Enchanted Book"
private const val RANCHERS_BOOTS = "RANCHERS_BOOTS"
private const val PREHISTORIC_EGG = "PREHISTORIC_EGG"
private const val UNLOCKED = "UNLOCKED"
private const val EXCEPT_IF = "Except if:"
private const val DRAGON = "Dragon"
private const val ENDER_DRAGON = "Ender Dragon"
private const val MAX_PET_LEVEL = "100"
private const val MAX_DRAGON_LEVEL = "200"
private const val DUNGEONEERING = "Dungeoneering"
private const val MAX_SKILL = "Max Skill level reached!"
private const val LEVEL_PREFIX = "[Lvl "
private const val HEALER = "Healer"
private const val CLASS_PASSIVES = "Class Passives"
private const val COLLECTION_PROGRESS = "Progress to "
private const val ESSENCE = "Essence"
private const val STATS_TUNING = "Stats Tuning"
private const val CROP_MAXED = "Max tier reached!"
private const val BESTIARY_DONE = "Overall Progress: 100%"
private const val TOOLKIT_LINE = "Part of a Toolkit!"
private const val TOOLKIT_MARK = "❒"
private const val SHOP_MAXED = "Maxed out!"
private const val SHOP_UPGRADING = "Currently upgrading!"
private const val SHOP_INSTANT = "Click to instantly upgrade!"
private const val SHOP_CLAIM = "Click to claim!"
private const val MAXED_MARK = "Max"
private const val CLOCK_MARK = "⏰"
private const val TICK_MARK = "✅"
private const val CROSS_SOURCE = "✖"
private const val CROSS_MARK = "✘"
private const val TICK_SOURCE = "✔"
private const val ZERO = "0"
private const val CHIP_CAP_TEN = "10"
private const val CHIP_CAP_FIFTEEN = "15"
private const val CHIP_CAP_TWENTY = "20"
private const val NO_SHOP_CATEGORY = -1
private const val SHOP_UPGRADES = 1
private const val ROW_SLOTS = 9
private const val SKILL_LAST_ROW = 4
private const val BESTIARY_LAST_ROW = 4
private const val BESTIARY_LAST_COLUMN = 7
private const val MENU_LAST_SLOT = 53
private const val MENU_LAST_ROW_SLOT = 44
private const val CHOOSE_PET_FIRST = 9
private const val ATTRIBUTE_LAST_SLOT = 43
private const val MAX_ATTRIBUTE_LEVEL = 10
private const val SKYBLOCK_LEVEL_SLOT = 22
private const val SKYBLOCK_LEVEL_PARTS = 3
private const val SKYBLOCK_LEVEL_PART = 2
private const val DUNGEONEERING_SLOT = 12
private const val GUIDE_FIRST_SLOT = 18
private const val ESSENCE_GUIDE_OFFSET = 3
private const val OPAQUE = 0xFF shl 24
private const val MOBY_DUCK = "MOBY_DUCK"
private const val MOBY_DUCK_SECONDS = 300L * 60 * 60
private const val RECOMB_MARK = "R"
private const val DUNGEON_CATEGORY = " DUNGEON "
private const val FULL_PERCENT = 100L
