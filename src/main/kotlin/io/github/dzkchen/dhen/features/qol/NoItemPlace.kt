package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.world.item.context.BlockPlaceContext

object NoItemPlace : Module(
	name = "No Item Place",
	category = Category.QOL,
	description = "Prevents utility items from being placed as blocks."
) {
	@JvmStatic
	fun shouldPrevent(context: BlockPlaceContext): Boolean {
		if (!enabled) return false
		val player = context.player ?: return false
		return protects(SkyBlockItems.of(player.mainHandItem).id)
	}

	internal fun protects(id: String): Boolean = when {
		id.startsWith(ABIPHONE) -> true
		id.endsWith(TUBA) -> true
		id.endsWith(POWER_ORB) -> true
		id.endsWith(POCKET_BLACK_HOLE) -> true
		id.endsWith(FISHING_NET) -> true
		else -> id == BOUQUET_OF_LIES ||
			id == FLOWER_OF_TRUTH ||
			id == BAT_WAND ||
			id == STARRED_BAT_WAND ||
			id == INFINITE_SPIRIT_LEAP ||
			id == ROYAL_PIGEON ||
			id == ARROW_SWAPPER ||
			id == JINGLE_BELLS ||
			id == FIRE_FREEZE_STAFF ||
			id == UMBERELLA ||
			id == ETHERWARP_CONDUIT ||
			id == KUUDRA_SHOP_ITEM
	}

	private const val ABIPHONE = "ABIPHONE"
	private const val TUBA = "_TUBA"
	private const val POWER_ORB = "_POWER_ORB"
	private const val POCKET_BLACK_HOLE = "_POCKET_BLACK_HOLE"
	private const val FISHING_NET = "_FISHING_NET"
	private const val BOUQUET_OF_LIES = "BOUQUET_OF_LIES"
	private const val FLOWER_OF_TRUTH = "FLOWER_OF_TRUTH"
	private const val BAT_WAND = "BAT_WAND"
	private const val STARRED_BAT_WAND = "STARRED_BAT_WAND"
	private const val INFINITE_SPIRIT_LEAP = "INFINITE_SPIRIT_LEAP"
	private const val ROYAL_PIGEON = "ROYAL_PIGEON"
	private const val ARROW_SWAPPER = "ARROW_SWAPPER"
	private const val JINGLE_BELLS = "JINGLE_BELLS"
	private const val FIRE_FREEZE_STAFF = "FIRE_FREEZE_STAFF"
	private const val UMBERELLA = "UMBERELLA"
	private const val ETHERWARP_CONDUIT = "ETHERWARP_CONDUIT"
	private const val KUUDRA_SHOP_ITEM = "KUUDRA_SHOP_ITEM"
}
