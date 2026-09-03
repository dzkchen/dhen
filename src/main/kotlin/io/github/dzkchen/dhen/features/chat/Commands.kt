package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.CommandUtilities
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.MessageSendEvent
import io.github.dzkchen.dhen.event.TabCompletionEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.util.Util
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.ceil

object Commands : Module(
	name = "Commands",
	category = Category.CHAT,
	description = "Completes SkyBlock command arguments and rewrites the commands you send."
), CommandUtilities {
	private var islandPlayerNames by BooleanSetting(
		"Island Players",
		default = true,
		description = "Suggests the names of the players standing on your island."
	)

	private var partyMemberNames by BooleanSetting(
		"Party Members",
		default = true,
		description = "Suggests the names of your party members."
	)

	private var warpSuggestions by BooleanSetting(
		"Warps",
		default = true,
		description = "Suggests warp names after /warp."
	)

	private var sackSuggestions by BooleanSetting(
		"Sack Items",
		default = true,
		description = "Suggests sack contents after /gfs."
	)

	private var showItemSuggestions by BooleanSetting(
		"Show Item",
		default = true,
		description = "Suggests what /show can put in chat."
	)

	private var recipeSuggestions by BooleanSetting(
		"Recipe Items",
		default = true,
		description = "Suggests craftable item names after /viewrecipe."
	)

	private var warpIsland by BooleanSetting(
		"Warp Is",
		description = "Turns /warp is into /is."
	)

	private var shortWarps by BooleanSetting(
		"Shorten Warp",
		description = "Lets you type /castle instead of /warp castle."
	)

	private var acceptLastInvite by BooleanSetting(
		"Accept Last Invite",
		default = true,
		description = "A bare /p accept joins the party you were last invited to."
	)

	private var rememberStorage by BooleanSetting(
		"Open Last Storage",
		default = true,
		description = "Lets /ec - and /bp - reopen the page you last had open."
	)

	private var transferCooldown by BooleanSetting(
		"Transfer Cooldown",
		description = "Holds a warp typed right after a server change and sends it when the cooldown ends."
	)

	private var transferCooldownMessage by BooleanSetting(
		"Transfer Cooldown Message",
		description = "Says in chat when the transfer cooldown has ended."
	).withDependency { transferCooldown }

	private var preventEarlyCommands by BooleanSetting(
		"Prevent Early Commands",
		description = "Repeats a command the server refused until its cooldown has passed."
	)

	private var lowerCaseRecipes by BooleanSetting(
		"Lower Case View Recipe",
		default = true,
		description = "Lets /viewrecipe take an item name typed in plain words."
	)

	private var openWikiLinks by BooleanSetting(
		"Open Wiki Links",
		description = "Opens a wiki page in your browser instead of putting a link in chat."
	)

	private var linkAddress by StringSetting(
		"Link",
		description = "The address /dhen link opens."
	)

	private val sackNames = RepoNames {
		ItemRepo.constants.sackItemIds
			.mapNotNull { ItemRepo.item(it)?.displayName?.takeIf(String::isNotEmpty) }
			.map { withoutCodes(it).uppercase(Locale.ROOT).replace(' ', '_') }
			.distinct()
	}
	private val recipeNames = RepoNames {
		ItemRepo.ids.filter { ItemRepo.item(it)?.recipes?.isNotEmpty() == true }
	}

	private val tree = SuggestionTree(
		listOf(
			SuggestionCommand(listOf("f", "friend"), branches = listOf(branch("accept", "add", "deny", offers = ::everyone))),
			SuggestionCommand(listOf("g", "guild"), branches = listOf(branch("invite", offers = ::everyone))),
			SuggestionCommand(
				listOf("p", "party"),
				branches = listOf(
					branch("accept", "invite", offers = ::islandPlayers),
					branch("kick", "demote", "promote", "transfer", offers = ::partyMembers)
				),
				offers = ::partyWords
			),
			SuggestionCommand(listOf("w", "msg", "tell", "boop", "boo"), offers = ::everyone),
			SuggestionCommand(listOf("visit", "invite", "ah"), offers = ::everyone),
			SuggestionCommand(listOf("pv"), offers = ::everyoneAndSelf),
			SuggestionCommand(listOf("trade"), offers = ::islandPlayers),
			SuggestionCommand(listOf("warp"), offers = ::warps),
			SuggestionCommand(listOf("gfs", "getfromsacks"), matchesAnywhere = true, offers = ::sackItems),
			SuggestionCommand(listOf("show", "showitem", "showoff"), offers = ::shownParts),
			SuggestionCommand(listOf(VIEW_RECIPE), offers = ::recipeItems)
		)
	)

	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)
	private var lastInviter: String? = null
	private var lastStorage: String? = null
	private var lastStoragePage: Int? = null
	private var heldWarp: String? = null
	private var holdingWarps = false
	private var lastCommand: String? = null
	private var worldChangedMillis = 0L

	override fun onEnabled() = repoHold.ensure()

	override fun onDisabled() {
		repoHold.release()
		lastInviter = null
		heldWarp = null
		holdingWarps = false
		lastCommand = null
	}

	override fun sendCoordinates(message: String): String {
		val player = Minecraft.getInstance().player ?: return NOT_IN_WORLD
		val coordinates = "x: ${player.blockX}, y: ${player.blockY}, z: ${player.blockZ}"
		Minecraft.getInstance().connection?.sendChat(if (message.isEmpty()) coordinates else "$coordinates $message")
		return "Sent your coordinates."
	}

	override fun openLastStorage(): String {
		if (!enabled || !rememberStorage) return SWITCHED_OFF
		return openStoredPage(lastStorage ?: FALLBACK_STORAGE)
	}

	override fun wiki(search: String): String {
		if (!enabled) return SWITCHED_OFF
		if (search.isEmpty()) return offerLink("Open the SkyBlock wiki.", WIKI_ROOT)
		val encoded = URLEncoder.encode(search, StandardCharsets.UTF_8)
		return offerLink("Look up $search on the SkyBlock wiki.", "$WIKI_SEARCH$encoded$WIKI_SCOPE")
	}

	override fun heldItemWiki(): String {
		val stack = Minecraft.getInstance().player?.mainHandItem
		if (stack == null || stack.isEmpty) return "You are not holding anything."
		return wiki(withoutCodes(stack.hoverName.string))
	}

	override fun link(): String {
		val address = linkAddress
		if (address.isBlank()) return "No link is set. Put one in the Commands module's Link setting."
		val client = Minecraft.getInstance()
		client.execute {
			client.gui.setScreen(
				ConfirmLinkScreen({ confirmed ->
					if (confirmed) Util.getPlatform().openUri(address)
					client.gui.setScreen(null)
				}, address, true)
			)
		}
		return "Opening $address."
	}

	private fun completing(event: TabCompletionEvent) {
		if (!SkyBlockLocation.onHypixel) return
		if (event.command.indexOf(' ') < 0) {
			if (shortWarps && SkyBlockLocation.inSkyBlock) {
				event.suggest(warps().filter { it.startsWith(event.command, ignoreCase = true) })
			}
			return
		}
		event.suggest(tree.suggestions(event.command))
	}

	private fun sending(event: MessageSendEvent) {
		if (!event.isCommand || !SkyBlockLocation.onHypixel) return
		val message = event.message
		val words = message.split(' ')
		val name = words[0].lowercase(Locale.ROOT)
		val onIsland = SkyBlockLocation.inSkyBlock
		if (acceptLastInvite && name in PARTY_COMMANDS && words.size == 2 && words[1].equals(ACCEPT, ignoreCase = true)) {
			acceptInvite(event)
			return
		}
		if (onIsland) {
			if (warpIsland && message.equals(WARP_ISLAND, ignoreCase = true)) event.message = ISLAND
			if (rememberStorage && name in STORAGE_COMMANDS && storage(event, words)) return
			if (lowerCaseRecipes && name == VIEW_RECIPE) {
				viewRecipeCommand(message, ::itemId)?.let { event.message = it }
			}
			if (sackSuggestions && name in SACK_COMMANDS) {
				sackCommand(message, ::knownSackItem)?.let { event.message = it }
			}
			if (shortWarps) shortenedWarp(message, ItemRepo.constants.warps, blockedWarp())?.let { event.message = it }
			if (holdingWarps) {
				val held = heldWarpCommand(event.message.split(' '))
				if (held != null) {
					heldWarp = held
					event.cancelled = true
					return
				}
			}
		}
		if (preventEarlyCommands) lastCommand = event.message
	}

	private fun chatted(event: ChatReceiveEvent) {
		if (!SkyBlockLocation.onHypixel) return
		val line = event.stripped
		if (acceptLastInvite && line.contains(INVITE_MARK)) {
			PARTY_INVITE.find(line)?.let {
				lastInviter = it.groupValues[1]
				return
			}
			INVITE_EXPIRED.find(line)?.let {
				if (lastInviter == it.groupValues[1]) lastInviter = null
				return
			}
		}
		if (!preventEarlyCommands || !line.contains(COOLDOWN_MARK)) return
		COMMAND_COOLDOWN.find(line)?.let { repeatAfterCooldown(event, it.groupValues[1]) }
	}

	private fun worldChanged(event: WorldChangeEvent) {
		if (event.phase != WorldChange.JOIN) return
		repoHold.ensure()
		worldChangedMillis = Util.getMillis()
		lastCommand = null
		if (!transferCooldown || holdingWarps) return
		holdingWarps = true
		inTicks(COOLDOWN_TICKS) { releaseHeldWarp() }
	}

	private fun releaseHeldWarp() {
		holdingWarps = false
		if (transferCooldownMessage && SkyBlockLocation.inSkyBlock) Dhen.announce("The transfer cooldown has ended.")
		val warp = heldWarp ?: return
		heldWarp = null
		Minecraft.getInstance().connection?.sendCommand(warp)
	}

	private fun repeatAfterCooldown(event: ChatReceiveEvent, seconds: String) {
		val command = lastCommand ?: return
		val remaining = seconds.toLong() * MILLIS_PER_SECOND - (Util.getMillis() - worldChangedMillis)
		val ticks = ceil(remaining.toDouble() / MILLIS_PER_TICK).toInt().coerceAtLeast(1)
		val wait = ceil(ticks / TICKS_PER_SECOND).toInt()
		event.cancelled = true
		Dhen.announce("Cannot run /$command yet. Running it in $wait second${if (wait == 1) "" else "s"}.")
		inTicks(ticks) { Minecraft.getInstance().connection?.sendCommand(command) }
	}

	private fun acceptInvite(event: MessageSendEvent) {
		event.cancelled = true
		val inviter = lastInviter
		if (inviter == null) {
			Dhen.announce("There is no party invite to accept.")
			return
		}
		lastInviter = null
		Minecraft.getInstance().connection?.sendCommand("party accept $inviter")
	}

	private fun storage(event: MessageSendEvent, words: List<String>): Boolean {
		val type = STORAGE_COMMANDS.getValue(words[0].lowercase(Locale.ROOT))
		val argument = words.getOrNull(1)
		if (argument == REOPEN_MARK) {
			event.cancelled = true
			Dhen.announce(openStoredPage(type))
			return true
		}
		val page = if (argument == null) FIRST_PAGE else argument.toIntOrNull() ?: return false
		lastStorage = type
		lastStoragePage = page.takeIf { it in pages(type) }
		return false
	}

	private fun openStoredPage(type: String): String {
		val page = lastStoragePage
			?: return "No page has been opened yet. " + runStorage(FALLBACK_STORAGE, FIRST_PAGE)
		return runStorage(type, page)
	}

	private fun runStorage(type: String, page: Int): String {
		Minecraft.getInstance().connection?.sendCommand("$type $page")
		return "Opening /$type $page."
	}

	private fun pages(type: String): IntRange = if (type == FALLBACK_STORAGE) ENDER_CHEST_PAGES else BACKPACK_PAGES

	private fun offerLink(text: String, url: String): String {
		if (openWikiLinks) {
			Util.getPlatform().openUri(url)
			return "Opening the wiki."
		}
		Minecraft.getInstance().player?.sendSystemMessage(DhenType.linkedOverWorld(text, URI.create(url)))
		return "Click the line above to open it."
	}

	private fun blockedWarp(): String? = when (SkyBlockLocation.island) {
		Island.PRIVATE_ISLAND -> JERRY_WARP
		Island.GARDEN -> BARN_WARP
		else -> null
	}

	private fun islandPlayers(): List<String> {
		if (!islandPlayerNames) return emptyList()
		val level = Minecraft.getInstance().level ?: return emptyList()
		val names = ArrayList<String>()
		for (player in level.players()) {
			if (player is RemotePlayer && player.uuid.version() == REAL_PLAYER_UUID_VERSION) names += player.name.string
		}
		return names
	}

	private fun partyMembers(): List<String> = if (partyMemberNames) PartyState.members else emptyList()

	private fun everyone(): List<String> = islandPlayers() + partyMembers()

	private fun everyoneAndSelf(): List<String> = everyone() + Minecraft.getInstance().user.name

	private fun partyWords(): List<String> =
		if (PartyState.members.isEmpty()) islandPlayers() else IN_PARTY_WORDS + islandPlayers()

	private fun warps(): Collection<String> =
		if (warpSuggestions && SkyBlockLocation.inSkyBlock) ItemRepo.constants.warps else emptySet()

	private fun sackItems(): List<String> =
		if (sackSuggestions && SkyBlockLocation.inSkyBlock) sackNames.get() else emptyList()

	private fun recipeItems(): List<String> =
		if (recipeSuggestions && SkyBlockLocation.inSkyBlock) recipeNames.get() else emptyList()

	private fun shownParts(): List<String> =
		if (showItemSuggestions && SkyBlockLocation.inSkyBlock) SHOWN_PARTS else emptyList()

	private fun itemId(name: String): String? = ItemRepo.idFor(name)

	private fun knownSackItem(name: String): Boolean = name.uppercase(Locale.ROOT).replace(' ', '_') in sackNames.get()

	private fun branch(vararg words: String, offers: () -> List<String>) = SuggestionBranch(words.toList(), offers)

	private val PARTY_COMMANDS = setOf("p", "party")
	private val IN_PARTY_WORDS = listOf(
		"chat", "disband", "kickoffline", "leave", "list", "mute", "poll", "private", "settings", "warp"
	)
	private val SHOWN_PARTS = listOf(
		"item", "helmet", "chestplate", "leggings", "boots", "necklace", "cloak", "belt", "bracelet", "gloves", "pet"
	)
	private val ENDER_CHEST_PAGES = 1..9
	private val BACKPACK_PAGES = 0..18

	private const val ACCEPT = "accept"
	private const val WARP_ISLAND = "warp is"
	private const val ISLAND = "is"
	private const val REOPEN_MARK = "-"
	private const val FALLBACK_STORAGE = "ec"
	private const val FIRST_PAGE = 1
	private const val JERRY_WARP = "jerry"
	private const val BARN_WARP = "barn"
	private const val INVITE_MARK = "invite"
	private const val COOLDOWN_MARK = "only use this command"
	private const val SWITCHED_OFF = "Switch the Commands module on to use this."
	private const val NOT_IN_WORLD = "You are not in a world."
	private const val WIKI_ROOT = "https://hypixelskyblock.minecraft.wiki"
	private const val WIKI_SEARCH = "https://hypixelskyblock.minecraft.wiki/index.php?search="
	private const val WIKI_SCOPE = "&scope=internal"
	private const val REAL_PLAYER_UUID_VERSION = 4
	private const val COOLDOWN_TICKS = 60
	private const val MILLIS_PER_SECOND = 1000L
	private const val MILLIS_PER_TICK = 50.0
	private const val TICKS_PER_SECOND = 20.0

	init {
		on<TabCompletionEvent> { completing(it) }
		on<MessageSendEvent> { sending(it) }
		on<ChatReceiveEvent> { chatted(it) }
		on<WorldChangeEvent> { worldChanged(it) }
	}
}

private class RepoNames(private val build: () -> List<String>) {
	private var source: String? = null
	private var names: List<String> = emptyList()

	fun get(): List<String> {
		val commit = ItemRepo.commit
		if (commit != null && commit == source) return names
		source = commit
		names = build()
		return names
	}
}
