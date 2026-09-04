package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.WikiLinks
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.regex.Pattern

object WikiLookup : Module(
	name = "Wiki Lookup",
	category = Category.INVENTORY,
	description = "Opens the wiki page for the item, pet, visitor or calendar event your cursor is on."
) {
	private val openSetting = KeybindSetting(
		"Open Wiki Page",
		GLFW.GLFW_KEY_F1,
		"Opens the wiki page for whatever the cursor is on.",
		KeybindScreenPolicy.NON_TEXT_SCREEN
	).onPress { pressed() }

	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)

	private val petName = Pattern.compile("⭐? ?\\[Lvl \\d+] (.+)").matcher("")
	private val visitorTitle = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Visitor's Logbook").matcher("")
	private val eventName = Pattern.compile("[0-9a-z ]*(.+)").matcher("")

	init {
		registerSetting(openSetting)

		on<ClientTickEvent.End> { repoHold.ensure() }
	}

	override fun onDisabled() = repoHold.release()

	private fun pressed() {
		if (!SkyBlockLocation.inSkyBlock) return
		val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return
		val slot = (screen as ContainerOrigin).dhenHoveredSlot() ?: return
		val stack = slot.item
		if (stack.isEmpty) return
		val url = resolve(withoutCodes(screen.title.string), slot.index, stack)
		if (url == null) {
			Dhen.announce(NO_ARTICLE)
			return
		}
		launch { WikiLinks.open(url) }
	}

	internal fun resolve(title: String, slotId: Int, stack: ItemStack): String? {
		val name = withoutCodes(stack.hoverName.string)
		val item = SkyBlockItems.of(stack)
		if (claimsVisitor(title, slotId, stack)) return WikiLinks.page(name)
		if (claimsPet(item, name)) return petPage(name)
		if (claimsBook(stack, item)) return bookPage(item)
		if (claimsEvent(title, slotId, stack)) return eventPage(name)
		return WikiLinks.article(item.marketId.ifEmpty { item.id })
	}

	private fun claimsVisitor(title: String, slotId: Int, stack: ItemStack): Boolean =
		slotId in VISITOR_FIRST_SLOT..VISITOR_LAST_SLOT &&
			!stack.`is`(Items.STAINED_GLASS_PANE.black()) &&
			visitorTitle.reset(title).matches()

	private fun claimsEvent(title: String, slotId: Int, stack: ItemStack): Boolean =
		slotId in CALENDAR_FIRST_SLOT..CALENDAR_LAST_SLOT &&
			!stack.`is`(Items.STAINED_GLASS_PANE.black()) &&
			title == CALENDAR_TITLE

	private fun claimsPet(item: SkyBlockItem, name: String): Boolean =
		item.pet != null || petName.reset(name).matches()

	private fun claimsBook(stack: ItemStack, item: SkyBlockItem): Boolean =
		stack.`is`(Items.ENCHANTED_BOOK) && item.enchantments.size == 1

	private fun petPage(name: String): String? {
		if (!petName.reset(name + PET_SUFFIX).matches()) return null
		return WikiLinks.page(petName.group(1))
	}

	private fun bookPage(item: SkyBlockItem): String {
		val enchantment = item.enchantments.keys.first()
			.removePrefix(ULTIMATE_PREFIX)
			.replace('_', ' ')
			.trim()
		return WikiLinks.page(titleCased("$enchantment $ENCHANTMENT_SUFFIX"))
	}

	private fun eventPage(name: String): String? {
		if (!eventName.reset(name).matches()) return null
		val event = eventName.group(1).trim()
		return WikiLinks.page(
			when {
				event == ELECTION_OVER || event == ELECTION_OPENS -> MAYORS_PAGE
				event.startsWith(BONUS_PREFIX) -> event.substring(BONUS_PREFIX.length)
				else -> event
			}
		)
	}

	private fun titleCased(text: String): String =
		text.split(' ').joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) }

	private const val NO_ARTICLE = "No wiki page for that."
	private const val PET_SUFFIX = " Pet"
	private const val ULTIMATE_PREFIX = "ultimate_"
	private const val ENCHANTMENT_SUFFIX = "enchantment"
	private const val CALENDAR_TITLE = "Calendar and Events"
	private const val ELECTION_OVER = "Election Over!"
	private const val ELECTION_OPENS = "Election Booth Opens"
	private const val MAYORS_PAGE = "Mayors"
	private const val BONUS_PREFIX = "Bonus "
	private const val VISITOR_FIRST_SLOT = 10
	private const val VISITOR_LAST_SLOT = 43
	private const val CALENDAR_FIRST_SLOT = 10
	private const val CALENDAR_LAST_SLOT = 25
}
