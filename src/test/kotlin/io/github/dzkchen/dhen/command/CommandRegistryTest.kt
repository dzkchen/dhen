package io.github.dzkchen.dhen.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.item.ItemFixture.HELD_UUID
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyRole
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoSync
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.diagnostic.Diagnostics
import io.github.dzkchen.dhen.event.ActionBarEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.Event
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import org.lwjgl.glfw.GLFW

class CommandRegistryTest {
	private fun registry(): CommandRegistry<Any> =
		CommandRegistry(ModuleManager()) { _, message -> captured += message }

	private val captured = mutableListOf<String>()

	@TempDir
	lateinit var repo: Path

	@BeforeEach
	@AfterEach
	fun restoreEffectsDefault() {
		Effects.reduced = false
	}

	@Test
	fun `duplicate name is rejected`() {
		val registry = registry()
		registry.register("foo", owner = "test") {}

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("foo", owner = "other") {}
		}
	}

	@Test
	fun `alias colliding with an existing literal is rejected`() {
		val registry = registry()
		registry.register("alpha", "beta", owner = "test") {}

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("gamma", "beta", owner = "other") {}
		}
	}

	@Test
	fun `reserved core names are rejected`() {
		val registry = registry()

		assertThrows(IllegalArgumentException::class.java) {
			registry.register("dhen", owner = "test") {}
		}
		assertThrows(IllegalArgumentException::class.java) {
			registry.register("cmd", "dh", owner = "test") {}
		}
	}

	@Test
	fun `handle unregisters the command`() {
		val registry = registry()
		val handle = registry.register("solo", owner = "test") {
			executes { Command.SINGLE_SUCCESS }
		}
		assertEquals(listOf("solo"), registry.commands.map { it.name })

		handle.unsubscribe()

		assertTrue(registry.commands.isEmpty())

		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)
		assertNull(dispatcher.root.getChild("solo"))
		assertNotNull(dispatcher.root.getChild("dhen"))
	}

	@Test
	fun `a registry built with only a manager and feedback defaults every callback`() {
		val feedback: (Any, String) -> Unit = { _, message -> captured += message }
		val registry = CommandRegistry(ModuleManager(), feedback = feedback)
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen", Any())

		assertTrue(captured.single().contains("Dhen commands"))
	}

	@Test
	fun `commands stop parsing once the registry reports unavailable`() {
		var available = true
		val registry = CommandRegistry<Any>(
			ModuleManager(),
			available = { available }
		) { _, message -> captured += message }
		var runs = 0
		registry.register("greet", owner = "test") {
			executes { runs++; Command.SINGLE_SUCCESS }
		}
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen", Any())
		dispatcher.execute("greet", Any())
		assertEquals(1, runs)
		assertEquals(1, captured.size)

		available = false

		assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("dhen", Any()) }
		assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("greet", Any()) }
		assertEquals(1, runs)
		assertEquals(1, captured.size)
	}

	@Test
	fun `registered command installs and executes under all its literals`() {
		val registry = registry()
		var runs = 0
		registry.register("greet", "g", owner = "test") {
			executes { runs++; Command.SINGLE_SUCCESS }
		}
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		assertNotNull(dispatcher.root.getChild("greet"))
		assertNotNull(dispatcher.root.getChild("g"))
		dispatcher.execute("greet", Any())
		dispatcher.execute("g", Any())
		assertEquals(2, runs)
	}

	@Test
	fun `module suggestions filter by the typed prefix`() {
		val manager = ModuleManager()
		manager.register(TestModule())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		fun suggestions(input: String): List<String> =
			dispatcher.getCompletionSuggestions(dispatcher.parse(input, Any())).get().list.map { it.text }

		assertEquals(listOf("Test_Module"), suggestions("dhen module Te"))
		assertTrue(suggestions("dhen module Zz").isEmpty())
	}

	@Test
	fun `module toggle flips state and reports through both roots`() {
		val manager = ModuleManager()
		val module = manager.register(TestModule())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen module Test_Module toggle", Any())
		assertTrue(module.enabled)
		assertEquals("Toggled Test Module: enabled", captured.last())

		dispatcher.execute("dh module Test_Module toggle", Any())
		assertFalse(module.enabled)
		assertEquals("Toggled Test Module: disabled", captured.last())
	}

	@Test
	fun `unknown module name reports without throwing`() {
		val registry = registry()
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen module Nope toggle", Any())

		assertEquals("No module named 'Nope'.", captured.last())
	}

	@Test
	fun `edit opens the HUD editor through both roots`() {
		var opened = 0
		val registry = CommandRegistry<Any>(ModuleManager(), { opened++ }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen edit", Any())
		dispatcher.execute("dh edit", Any())

		assertEquals(2, opened)
		assertEquals("Opening the HUD editor.", captured.last())
	}

	@Test
	fun `reset-all resets the HUD layout through both roots and reports the count`() {
		var resets = 0
		var pending = 3
		val registry = CommandRegistry<Any>(
			ModuleManager(),
			{},
			{},
			{
				resets++
				val reset = pending
				pending = 0
				reset
			}
		) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen reset-all", Any())
		assertEquals("Reset 3 HUD elements to the declared layout.", captured.last())

		dispatcher.execute("dh reset-all", Any())
		assertEquals("Every HUD element is already at its declared layout.", captured.last())
		assertEquals(2, resets)
	}

	@Test
	fun `reset-all reports a single element without pluralizing`() {
		val registry = CommandRegistry<Any>(ModuleManager(), {}, {}, { 1 }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen reset-all", Any())

		assertEquals("Reset 1 HUD element to the declared layout.", captured.last())
	}

	@Test
	fun `the world-render probe reports the state the toggle returns`() {
		var probe = false
		val registry = CommandRegistry<Any>(
			ModuleManager(),
			toggleWorldRender = { probe = !probe; probe }
		) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug worldrender", Any())
		assertTrue(probe)
		assertEquals("World-render probe on.", captured.last())

		dispatcher.execute("dh debug worldrender", Any())
		assertFalse(probe)
		assertEquals("World-render probe off.", captured.last())
	}

	@Test
	fun `effects toggles the glass tier and persists every change`() {
		var persisted = 0
		val registry = CommandRegistry<Any>(ModuleManager(), {}, { persisted++ }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen effects", Any())
		assertTrue(Effects.reduced)
		assertEquals("Glass effects disabled.", captured.last())

		dispatcher.execute("dh effects", Any())
		assertFalse(Effects.reduced)
		assertEquals("Glass effects enabled.", captured.last())

		dispatcher.execute("dhen effects off", Any())
		assertTrue(Effects.reduced)

		dispatcher.execute("dh effects on", Any())
		assertFalse(Effects.reduced)
		assertEquals(4, persisted)
	}

	@Test
	fun `theme lists, switches, exports and reloads through both roots`() {
		val exported = mutableListOf<String?>()
		var reloads = 0
		val registry = CommandRegistry<Any>(
			ModuleManager(),
			{},
			{},
			{ 0 },
			object : ThemeCommands {
				override fun names() = listOf("Default", "ocean")
				override fun summary() = "Themes: Default (active), ocean."
				override fun select(name: String) = "Theme set to '$name'."
				override fun export(name: String?, notify: (String) -> Unit) {
					exported += name
					notify("Exported.")
				}

				override fun reload(notify: (String) -> Unit) {
					reloads++
					notify("Read 2 themes.")
				}
			}
		) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen theme", Any())
		assertEquals("Themes: Default (active), ocean.", captured.last())

		dispatcher.execute("dh theme list", Any())
		assertEquals("Themes: Default (active), ocean.", captured.last())

		dispatcher.execute("dhen theme use ocean", Any())
		assertEquals("Theme set to 'ocean'.", captured.last())

		dispatcher.execute("dhen theme export", Any())
		dispatcher.execute("dh theme export mine", Any())
		assertEquals(listOf(null, "mine"), exported)
		assertEquals("Exported.", captured.last())

		dispatcher.execute("dh theme reload", Any())
		assertEquals(1, reloads)
		assertEquals("Read 2 themes.", captured.last())
	}

	@Test
	fun `theme suggestions filter by the typed prefix`() {
		val registry = CommandRegistry<Any>(
			ModuleManager(),
			{},
			{},
			{ 0 },
			object : ThemeCommands by ThemeCommands.NONE {
				override fun names() = listOf("Default", "ocean")
			}
		) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		fun suggestions(input: String): List<String> =
			dispatcher.getCompletionSuggestions(dispatcher.parse(input, Any())).get().list.map { it.text }

		assertEquals(listOf("ocean"), suggestions("dhen theme use oc"))
		assertEquals(listOf("Default", "ocean"), suggestions("dhen theme use "))
		assertTrue(suggestions("dhen theme use zz").isEmpty())
	}

	@Test
	fun `a registry with no theme wiring says so instead of pretending`() {
		val registry = registry()
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen theme", Any())
		dispatcher.execute("dhen theme use ocean", Any())
		dispatcher.execute("dhen theme export", Any())
		dispatcher.execute("dhen theme reload", Any())

		assertEquals(4, captured.size)
		assertTrue(captured.all { it == "Themes are not available." }, captured.toString())
	}

	@Test
	fun `debug reports live module counters and handler timing`() {
		val times = ArrayDeque(listOf(10L, 60L))
		val manager = ModuleManager(nanoClock = { times.removeFirst() })
		val module = manager.register(DebugModule())
		manager.enable(module)
		manager.eventBus.type<DebugEvent>().dispatch(DebugEvent())
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug", Any())

		assertEquals(
			"Dhen debug: deep profiling off, modules.json v${ModulePersistence.version}",
			captured.single { it.startsWith("Dhen debug:") }
		)
		assertEquals(
			listOf(
				"Packets", "Screens", "Containers", "Input", "World", "Entity render", "Interactions",
				"World render", "Ticks", "Location", "Scoreboard", "Tab list", "Tab list widgets",
				"Party", "Player stats", "Hypixel Mod API"
			).map { "$it: no feed, off until restart" },
			captured.filter { it.endsWith("no feed, off until restart") }
		)
		val reported = listOf("Server tick:", "Item repo:", "Prices:", "Debug Module:", "  DebugEvent:")
		assertEquals(
			listOf(
				"Server tick: no feed, the server tick feed is off until restart",
				"Item repo: state=IDLE, items=0, needed by 0",
				"Prices: needed by 0, bazaar=0 products",
				"Debug Module: subscriptions=1, keybinds=1, hud=0, errors=1",
				"  DebugEvent: calls=1, rollingAvg=50ns, rollingMax=50ns, samples=1"
			),
			captured.filter { line -> reported.any(line::startsWith) }
		)
	}

	@Test
	fun `debug reports the island and area the location feed is holding`() {
		val manager = ModuleManager()
		HypixelLocationHooks.install(manager.eventBus)
		try {
			HypixelLocationHooks.located("mini1A", skyBlock = true, mode = "dungeon", map = "Dungeon")
			val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
			val dispatcher = CommandDispatcher<Any>()
			registry.install(dispatcher)

			dispatcher.execute("dhen debug", Any())
		} finally {
			HypixelLocationHooks.uninstall()
			SkyBlockLocation.reset()
		}

		assertEquals(
			"Location: hypixel=true, skyblock=true, island=CATACOMBS, area=Dungeon, " +
				"mode=dungeon, server=mini1A, islandChanges=1, areaChanges=1, " +
				"guest=false, awaitingGuestTitle=false",
			captured.first { it.startsWith("Location: hypixel=") }
		)
	}

	@Test
	fun `debug scoreboard reports the sidebar and tab list the parsers are holding`() {
		val manager = ModuleManager()
		ScoreboardHooks.install(manager.eventBus) { null }
		TablistHooks.install(manager.eventBus) { listOf(Component.literal("Info")) }
		try {
			TablistHooks.refresh()
			val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
			val dispatcher = CommandDispatcher<Any>()
			registry.install(dispatcher)

			dispatcher.execute("dhen debug scoreboard", Any())
		} finally {
			ScoreboardHooks.uninstall()
			TablistHooks.uninstall()
		}

		assertEquals(
			listOf(
				"Scoreboard title: '' (objective none)",
				"Tab list header: ''",
				"Tab list footer: ''",
				"  Info"
			),
			captured
		)
	}

	@Test
	fun `debug repo reports the item repo without downloading anything`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug repo", Any())

		assertEquals(
			listOf(
				"Item repo: state=IDLE, items=0, needed by 0, commit=none",
				"  constants: reforgeStones=0, starredItems=0"
			),
			captured
		)
	}

	@Test
	fun `debug repo download toggles the requirement on and back off`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)
		ItemRepo.install(CoroutineScope(Dispatchers.Unconfined), repo, RepoSync(DataFixture.NEU, repo, DataFixture.OFFLINE))
		try {
			dispatcher.execute("dhen debug repo download", Any())
			val asked = ItemRepo.required
			dispatcher.execute("dhen debug repo download", Any())

			assertEquals(1, asked)
			assertEquals(0, ItemRepo.required)
		} finally {
			ItemRepo.uninstall()
		}

		assertEquals(
			listOf("Item repo: asked for, downloading in the background.", "Item repo: no longer asked for by this toggle."),
			captured.filter { it.startsWith("Item repo: ") && !it.startsWith("Item repo: state=") }
		)
	}

	@Test
	fun `debug repo with an id reports that an unloaded repo has no such item`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug repo ASPECT_OF_THE_END", Any())

		assertEquals(listOf("Item repo: nothing named 'ASPECT_OF_THE_END' (state=IDLE, items=0)"), captured)
	}

	@Test
	fun `debug item reports an empty hand rather than reading a stack`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager, diagnostics = Diagnostics(manager) { null }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug item", Any())

		assertEquals(listOf("Held item: nothing in your main hand"), captured)
	}

	@Test
	fun `debug item reports the SkyBlock attributes of the held stack`() {
		ItemFixture.bootstrap()
		val stack = ItemFixture.stack {
			putString("id", "SPIRIT_SCEPTRE")
			putString("uuid", HELD_UUID)
			putInt("upgrade_level", 5)
			putInt("rarity_upgrades", 1)
		}
		stack.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§d§lMYTHIC DUNGEON SWORD"))))
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager, diagnostics = Diagnostics(manager) { stack }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug item", Any())

		assertTrue(captured.any { it.contains("id=SPIRIT_SCEPTRE") && it.contains("marketId=SPIRIT_SCEPTRE") && it.contains("uuid=$HELD_UUID") })
		assertTrue(captured.any { it.contains("rarity=MYTHIC") && it.contains("upgradeLevel=5") && it.contains("rarityUpgrades=1") })
	}

	@Test
	fun `debug item reports the type and tier of a held pet`() {
		ItemFixture.bootstrap()
		val stack = ItemFixture.stack {
			putString("id", "PET")
			putString("petInfo", """{"type":"GOLDEN_DRAGON","tier":"LEGENDARY","candyUsed":2}""")
		}
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager, diagnostics = Diagnostics(manager) { stack }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug item", Any())

		assertTrue(captured.any { it.contains("marketId=PET-GOLDEN_DRAGON-LEGENDARY") })
		assertTrue(captured.any { it.contains("pet: type=GOLDEN_DRAGON, tier=LEGENDARY") && it.contains("candy=2") })
	}

	@Test
	fun `debug value reports an empty hand rather than valuing a stack`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager, diagnostics = Diagnostics(manager) { null }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug value", Any())

		assertEquals(listOf("Held item: nothing in your main hand"), captured)
	}

	@Test
	fun `debug value breaks a held stack down even while nothing is priced`() {
		ItemFixture.bootstrap()
		val stack = ItemFixture.stack { putString("id", "HYPERION"); putInt("rarity_upgrades", 1) }
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager, diagnostics = Diagnostics(manager) { stack }) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug value", Any())

		assertTrue(captured[0].contains("the item catalog is not ready yet (IDLE)"))
		assertTrue(captured[1].endsWith(": 0.0 from BAZAAR_INSTANT_SELL (base 0.0)"))
		assertEquals(listOf("  HYPERION: no price", "  RECOMBOBULATOR_3000: no price"), captured.drop(2))
	}

	@Test
	fun `debug stats reports the action bar stats the parser is holding`() {
		val manager = ModuleManager()
		PlayerStatsHooks.install(manager.eventBus, { true }, { false }, { -1f }, { 0.4 })
		try {
			val event = ActionBarEvent()
			event.text = Component.literal("§c1,530/1,530❤     §a1,204❈ Defense     §b1,050/1,050✎ Mana")
			manager.eventBus.type<ActionBarEvent>().dispatch(event)
			manager.eventBus.type<ClientTickEvent.End>().dispatch(ClientTickEvent.End)
			val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
			val dispatcher = CommandDispatcher<Any>()
			registry.install(dispatcher)

			dispatcher.execute("dhen debug stats", Any())
		} finally {
			PlayerStatsHooks.uninstall()
		}

		assertEquals(
			listOf(
				"Player stats: health=1530/1530, defense=1204, ehp=19890",
				"  mana=1050/1050, overflow=0, speed=400",
				"  vitality=0/0, shown=false",
				"  stacks=0, salvation=0, secrets=0/0",
				"  hidden from the action bar: nothing"
			),
			captured
		)
	}

	@Test
	fun `debug tablist reports the widgets the grouping is holding`() {
		val manager = ModuleManager()
		TablistHooks.install(manager.eventBus) {
			listOf(Component.literal("Info"), Component.literal(" Area: Hub"))
		}
		TabWidgetHooks.install(manager.eventBus) { true }
		try {
			TablistHooks.refresh()
			val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
			val dispatcher = CommandDispatcher<Any>()
			registry.install(dispatcher)

			dispatcher.execute("dhen debug tablist", Any())
		} finally {
			TabWidgetHooks.uninstall()
			TablistHooks.uninstall()
		}

		assertEquals(
			listOf(
				"Tab list widgets: 2 active of ${TabWidget.entries.size}",
				"  INFO",
				"    Info",
				"  AREA",
				"     Area: Hub"
			),
			captured
		)
	}

	@Test
	fun `debug party reports the roster the party feed is holding`() {
		val manager = ModuleManager()
		PartyHooks.install(manager.eventBus, self = { "Me" }, request = {})
		try {
			PartyHooks.reconciled(true, "Alice", mapOf("Alice" to PartyRole.LEADER, "Me" to PartyRole.MEMBER), 2)
			val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
			val dispatcher = CommandDispatcher<Any>()
			registry.install(dispatcher)

			dispatcher.execute("dhen debug party", Any())
		} finally {
			PartyHooks.uninstall()
		}

		assertEquals(
			listOf(
				"Party: inParty=true, leader=Alice, members=2, you=Me, youLead=false, packet=available",
				"  Alice: role=LEADER",
				"  Me: role=MEMBER"
			),
			captured
		)
	}

	@Test
	fun `debug lists every registered command with its owner`() {
		val registry = CommandRegistry<Any>(ModuleManager()) { _, message -> captured += message }
		registry.register("waypoints", "wp", owner = "dhen-dungeons") { }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug", Any())

		assertEquals("/waypoints, /wp — dhen-dungeons", captured.last())
	}

	@Test
	fun `debug command controls deep profiling explicitly`() {
		val manager = ModuleManager()
		val registry = CommandRegistry<Any>(manager) { _, message -> captured += message }
		val dispatcher = CommandDispatcher<Any>()
		registry.install(dispatcher)

		dispatcher.execute("dhen debug deep on", Any())
		assertTrue(manager.profiler.deepMode)
		assertEquals("Deep profiling enabled.", captured.last())

		dispatcher.execute("dh debug deep off", Any())
		assertFalse(manager.profiler.deepMode)
		assertEquals("Deep profiling disabled.", captured.last())
	}

	private class TestModule : Module(
		name = "Test Module",
		category = Category.DEV,
		description = "Toggle target for command tests."
	)

	private class DebugEvent : Event

	private class DebugModule : Module(
		name = "Debug Module",
		category = Category.DEV,
		description = "Debug fixture."
	) {
		@Suppress("unused")
		private val keybind by KeybindSetting("Action", GLFW.GLFW_KEY_K)

		init {
			on<DebugEvent> { error("count this failure") }
		}
	}
}
