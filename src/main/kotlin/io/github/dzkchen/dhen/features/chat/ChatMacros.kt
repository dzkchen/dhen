package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.CommandAliasCommands
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.MessageSendEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.util.ServerClock
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Util
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ChatMacros : Module(
	name = "Chat Macros",
	category = Category.CHAT,
	description = "Answers ! commands in party, guild and direct chat, binds keys to commands, and rewrites aliases."
), ChatCommandReplies, CommandAliasCommands {
	private val COMMAND_KEYS = listOf(
		"Pets" to "pets",
		"Storage" to "storage",
		"Armor Wardrobe" to "armor",
		"Equipment Wardrobe" to "equipment",
		"Loadouts" to "loadout",
		"Stats" to "stats",
		"Dungeon Hub" to "warp dungeon_hub",
		"Potion Bag" to "potionbag"
	)

	private var partyCommands by BooleanSetting(
		"Party Commands",
		default = true,
		description = "Answers ! commands typed in party chat."
	)

	private var guildCommands by BooleanSetting(
		"Guild Commands",
		description = "Answers ! commands typed in guild chat."
	)

	private var privateCommands by BooleanSetting(
		"Private Commands",
		default = true,
		description = "Answers ! commands sent to you as a direct message."
	)

	private var leaderOnly by BooleanSetting(
		"Leader Only",
		default = true,
		description = "Party commands that move the party only answer while you lead it."
	)

	private var chatEmotes by BooleanSetting(
		"Chat Emotes",
		description = "Replaces :star:, <3 and the rest with symbols as you send them."
	)

	private var storedAliases by StringSetting("Command Aliases").hide()

	init {
		for ((name, command) in COMMAND_KEYS) registerSetting(commandKey(name, command))
	}

	private var showCommandToggles by BooleanSetting(
		"Show Command Toggles",
		description = "Reveals the switch for every individual ! command."
	)

	private val coordsOn by commandToggle("Coords")
	private val pingOn by commandToggle("Ping")
	private val tpsOn by commandToggle("Tps")
	private val fpsOn by commandToggle("Fps")
	private val timeOn by commandToggle("Time", default = false)
	private val locationOn by commandToggle("Location")
	private val holdingOn by commandToggle("Holding")
	private val warpOn by commandToggle("Warp")
	private val allInviteOn by commandToggle("Allinvite")
	private val transferOn by commandToggle("Party Transfer", default = false)
	private val promoteOn by commandToggle("Promote", default = false)
	private val demoteOn by commandToggle("Demote", default = false)
	private val kickOn by commandToggle("Kick")
	private val kickOfflineOn by commandToggle("Kick Offline", alsoNeeds = { kickOn })
	private val queueOn by commandToggle("Queue Commands")
	private val downtimeOn by commandToggle("Downtime")
	private val reinviteOn by commandToggle("Reinvite", default = false)
	private val directInviteOn by commandToggle("Direct Invite")
	private val autoConfirmOn by commandToggle("Auto Confirm", default = false, alsoNeeds = { directInviteOn })
	private val partyInviteOn by commandToggle("Party Invite")

	private val aliases = AliasBook { storedAliases }
	private val downtime = linkedMapOf<String, String>()
	private var lastSentMillis = 0L

	val downtimeHeld: Boolean
		get() = downtime.isNotEmpty()

	override fun onEnabled() = installedCommands()

	override fun onDisabled() {
		downtime.clear()
		forgetAliasNodes()
	}

	override fun reply(channel: ChatChannel, sender: String, message: String) = send(
		when (channel) {
			ChatChannel.PARTY -> "pc $message"
			ChatChannel.GUILD -> "gc $message"
			ChatChannel.PRIVATE -> "msg $sender $message"
		}
	)

	override fun send(command: String) {
		val now = Util.getMillis()
		if (now - lastSentMillis < SEND_INTERVAL_MILLIS) return
		lastSentMillis = now
		Minecraft.getInstance().connection?.sendCommand(command)
	}

	override fun add(alias: String, replacement: String): String {
		val fault = aliasFault(alias, replacement)
		if (fault != null) return fault
		val key = alias.lowercase(Locale.ROOT)
		val command = replacement.removePrefix("/")
		storedAliases = formatAliases(aliases.all() + (key to command))
		installedCommands()
		if (!enabled) return "/$key will send /$command once Chat Macros is switched on."
		return "/$key now sends /$command."
	}

	override fun remove(alias: String): String {
		val key = alias.lowercase(Locale.ROOT)
		val current = aliases.all()
		if (key !in current) return "No alias named '$key'."
		storedAliases = formatAliases(current - key)
		if (enabled) Minecraft.getInstance().connection?.commands?.let { unregisterNode(it.root, key) }
		return "Removed the /$key alias."
	}

	override fun list(): List<String> {
		val current = aliases.all()
		if (current.isEmpty()) return listOf("No aliases yet. Add one with /dhen alias add <name> <command>.")
		return current.map { "/${it.key} sends /${it.value}" }
	}

	override fun aliases(): List<String> = aliases.all().keys.toList()

	private fun chatted(event: ChatReceiveEvent) {
		if (!SkyBlockLocation.onHypixel) return
		val stripped = event.stripped
		if (downtime.isNotEmpty() && END_OF_RUN.matches(stripped)) {
			flushDowntime()
			return
		}
		val line = chatCommandLine(stripped) ?: return
		if (!answers(line.channel)) return
		inTicks(ANSWER_TICKS) { ChatCommandRegistry.answer(line, PartyState.isLeader, leaderOnly, this) }
	}

	private fun sending(event: MessageSendEvent) {
		if (event.isCommand) aliases.rewrite(event.message)?.let { event.message = it }
		if (!chatEmotes || !emotable(event.message, event.isCommand)) return
		emoted(event.message)?.let { event.message = it }
	}

	private fun installedCommands() {
		if (!enabled) return
		val dispatcher = Minecraft.getInstance().connection?.commands ?: return
		installAliases(dispatcher, aliases.all().keys)
	}

	private fun forgetAliasNodes() {
		val root = Minecraft.getInstance().connection?.commands?.root ?: return
		for (alias in aliases.all().keys) unregisterNode(root, alias)
	}

	private fun answers(channel: ChatChannel): Boolean = when (channel) {
		ChatChannel.PARTY -> partyCommands
		ChatChannel.GUILD -> guildCommands
		ChatChannel.PRIVATE -> privateCommands
	}

	private fun flushDowntime() = inTicks(FLUSH_TICKS) {
		val self = Minecraft.getInstance().user.name
		val names = downtime.keys.joinToString(", ")
		downtime[self]?.let { send("pc Downtime needed: $it") }
		DhenAlert.show("Downtime!", "Players needing DT: $names")
		Dhen.announce(
			"DT reasons — " + downtime.entries
				.groupBy({ it.value }, { it.key })
				.entries
				.joinToString("; ") { (reason, players) -> "${players.joinToString(", ")}: $reason" }
		)
		downtime.clear()
	}

	private fun odinPack() = CommandPack(
		"Odin", listOf(
			entry("help", "h") { it.reply("Commands: " + ChatCommandRegistry.answered(it.channel).joinToString(", ")) },
			entry("coords", "co", enabled = { coordsOn }, run = ::coords),
			entry("ping", enabled = { pingOn }) { it.reply("Ping: ${ping()}ms") },
			entry("tps", enabled = { tpsOn }) { it.reply("TPS: " + String.format(Locale.ROOT, TPS_FORMAT, ServerClock.tps)) },
			entry("fps", enabled = { fpsOn }) { it.reply("FPS: ${Minecraft.getInstance().fps}") },
			entry("time", enabled = { timeOn }) { it.reply("Current Time: ${CLOCK_FORMAT.format(ZonedDateTime.now())}") },
			entry("location", enabled = { locationOn }) { it.reply("Current Location: ${areaName()}") },
			entry("holding", enabled = { holdingOn }) { it.reply("Holding: ${heldItemName()}") },
			partyEntry("warp", "w", enabled = { warpOn }) { it.send("p warp") },
			partyEntry("allinvite", "allinv", enabled = { allInviteOn }, run = ::allInvite),
			partyEntry("pt", "ptme", "transfer", enabled = { transferOn }, run = ::transfer),
			partyEntry("promote", enabled = { promoteOn }) { it.send("p promote ${target(it)}") },
			partyEntry("demote", enabled = { demoteOn }) { it.send("p demote ${target(it)}") },
			partyEntry("kick", "k", enabled = { kickOn }, run = ::kick),
			partyEntry("kickoffline", "ko", enabled = { kickOfflineOn }) { it.send("p kickoffline") },
			queueEntry('f'),
			queueEntry('m'),
			queueEntry('t'),
			entry("downtime", "dt", channels = PARTY_ONLY, enabled = { downtimeOn }, run = ::downtimeAdd),
			entry("undowntime", "undt", channels = PARTY_ONLY, enabled = { downtimeOn }, run = ::downtimeDrop),
			partyEntry("reinv", "reinvite", enabled = { reinviteOn }, run = ::reinvite),
			entry("invite", "inv", channels = DIRECT_ONLY, enabled = { directInviteOn }, run = ::directInvite)
		)
	)

	private fun noammPack() = CommandPack(
		"NoammAddons", listOf(
			entry("cords", enabled = { coordsOn }, run = ::coords),
			partyEntry("ai", enabled = { allInviteOn }, run = ::allInvite),
			partyEntry("invite", "inv", "kidnap", enabled = { partyInviteOn }, run = ::partyInvite)
		)
	)

	private fun coords(context: ChatCommandContext) {
		val player = Minecraft.getInstance().player ?: return
		context.reply("x: ${player.blockX}, y: ${player.blockY}, z: ${player.blockZ}")
	}

	private fun allInvite(context: ChatCommandContext) = context.send("p settings allinvite")

	private fun transfer(context: ChatCommandContext) {
		val target = target(context)
		if (target.equals(Minecraft.getInstance().user.name, ignoreCase = true)) return
		context.send("p transfer $target")
	}

	private fun kick(context: ChatCommandContext) {
		val partial = context.argument ?: return
		val target = member(partial) ?: return
		context.send("p kick $target")
	}

	private fun queueEntry(mark: Char) =
		ChatCommandEntry(queueCommandNames(mark), PARTY_ONLY, leaderOnly = true, enabled = { queueOn }, run = ::queue)

	private fun queue(context: ChatCommandContext) {
		val instance = queueInstance(context.words[0].lowercase(Locale.ROOT), context.argument) ?: return
		context.send("joininstance $instance")
	}

	private fun downtimeAdd(context: ChatCommandContext) {
		if (context.sender in downtime) {
			Dhen.announce("${context.sender} already has a downtime reminder.")
			return
		}
		downtime[context.sender] = context.arguments.ifBlank { NO_REASON }
		Dhen.announce("Downtime reminder set for ${context.sender}; auto requeue is off for this run.")
	}

	private fun downtimeDrop(context: ChatCommandContext) {
		if (downtime.remove(context.sender) == null) {
			Dhen.announce("${context.sender} has no downtime reminder.")
			return
		}
		Dhen.announce("Removed ${context.sender}'s downtime reminder.")
	}

	private fun reinvite(context: ChatCommandContext) {
		Dhen.announce("Reinviting ${context.sender} in ${REINVITE_TICKS / TICKS_PER_SECOND} seconds.")
		inTicks(REINVITE_TICKS) { send("p invite ${context.sender}") }
	}

	private fun directInvite(context: ChatCommandContext) {
		if (autoConfirmOn) {
			context.send("p invite ${context.sender}")
			return
		}
		val client = Minecraft.getInstance()
		val command = "/p invite ${context.sender}"
		client.player?.sendSystemMessage(
			DhenType.overWorld("Click here to invite ${context.sender} to your party.").copy().withStyle { style ->
				style
					.withClickEvent(ClickEvent.RunCommand(command))
					.withHoverEvent(HoverEvent.ShowText(DhenType.overWorld(command)))
			}
		)
		client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f))
	}

	private fun partyInvite(context: ChatCommandContext) {
		val invited = context.argument ?: return
		context.send("p invite $invited")
	}

	private fun target(context: ChatCommandContext): String =
		context.argument?.let { member(it) ?: it } ?: context.sender

	private fun member(partial: String): String? =
		PartyState.members.firstOrNull { it.contains(partial, ignoreCase = true) }

	private fun ping(): Int {
		val connection = Minecraft.getInstance().connection ?: return 0
		return connection.getPlayerInfo(Minecraft.getInstance().user.name)?.latency ?: 0
	}

	private fun areaName(): String =
		SkyBlockLocation.area ?: SkyBlockLocation.island.displayName ?: UNKNOWN_AREA

	private fun heldItemName(): String {
		val stack = Minecraft.getInstance().player?.mainHandItem ?: return NOTHING_HELD
		if (stack.isEmpty) return NOTHING_HELD
		return withoutCodes(stack.hoverName.string)
	}

	private fun commandKey(name: String, command: String): KeybindSetting =
		KeybindSetting(name, description = "Sends /$command while you are in SkyBlock.").onPress {
			if (SkyBlockLocation.inSkyBlock) Minecraft.getInstance().connection?.sendCommand(command)
		}

	private fun commandToggle(name: String, default: Boolean = true, alsoNeeds: () -> Boolean = { true }) =
		BooleanSetting(name, default).withDependency { showCommandToggles && alsoNeeds() }

	private fun entry(
		vararg names: String,
		channels: Set<ChatChannel> = EVERY_CHANNEL,
		enabled: () -> Boolean = { true },
		run: (ChatCommandContext) -> Unit
	) = ChatCommandEntry(names.toList(), channels, leaderOnly = false, enabled = enabled, run = run)

	private fun partyEntry(
		vararg names: String,
		enabled: () -> Boolean = { true },
		run: (ChatCommandContext) -> Unit
	) = ChatCommandEntry(names.toList(), PARTY_ONLY, leaderOnly = true, enabled = enabled, run = run)

	private val EVERY_CHANNEL = setOf(ChatChannel.PARTY, ChatChannel.GUILD, ChatChannel.PRIVATE)
	private val PARTY_ONLY = setOf(ChatChannel.PARTY)
	private val DIRECT_ONLY = setOf(ChatChannel.PRIVATE)
	private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

	private const val TPS_FORMAT = "%.1f"
	private const val NO_REASON = "No reason given"
	private const val NOTHING_HELD = "nothing"
	private const val UNKNOWN_AREA = "somewhere unknown"
	private const val SEND_INTERVAL_MILLIS = 250L
	private const val ANSWER_TICKS = 4
	private const val FLUSH_TICKS = 30
	private const val REINVITE_TICKS = 100
	private const val TICKS_PER_SECOND = 20

	init {
		ChatCommandRegistry.register(odinPack())
		ChatCommandRegistry.register(noammPack())
		on<ChatReceiveEvent> { chatted(it) }
		on<MessageSendEvent>(BEFORE_FEATURES) { sending(it) }
		on<PacketReceiveEvent.Post> { if (it.packet is ClientboundCommandsPacket) installedCommands() }
	}
}
