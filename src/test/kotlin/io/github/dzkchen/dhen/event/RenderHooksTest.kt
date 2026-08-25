package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.uninitialized
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.item.ItemEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RenderHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		RenderHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		RenderHooks.uninstall()
	}

	@Test
	fun `an untouched glow keeps the colour vanilla computed`() {
		val event = EntityGlowEvent()

		event.seed(TEAM_OUTLINE)

		assertTrue(event.glowing)
		assertEquals(TEAM_OUTLINE, event.color)
		assertEquals(TEAM_OUTLINE, event.outline())
	}

	@Test
	fun `an entity vanilla would not outline stays unoutlined`() {
		val event = EntityGlowEvent()

		event.seed(EntityRenderState.NO_OUTLINE)

		assertFalse(event.glowing)
		assertEquals(EntityRenderState.NO_OUTLINE, event.outline())
	}

	@Test
	fun `a recolour leaves the vanilla glow decision alone`() {
		val event = EntityGlowEvent()

		event.seed(TEAM_OUTLINE)
		event.color = RED

		assertEquals(ARGB.opaque(RED), event.outline())
	}

	@Test
	fun `turning a glow off writes the no-outline value`() {
		val event = EntityGlowEvent()

		event.seed(TEAM_OUTLINE)
		event.glowing = false

		assertEquals(EntityRenderState.NO_OUTLINE, event.outline())
	}

	@Test
	fun `turning a glow on without a colour outlines in white`() {
		val event = EntityGlowEvent()

		event.seed(EntityRenderState.NO_OUTLINE)
		event.glowing = true

		assertEquals(ARGB.white(0xFF), event.outline())
	}

	@Test
	fun `a colour handed over without alpha still outlines`() {
		val event = EntityGlowEvent()

		event.seed(EntityRenderState.NO_OUTLINE)
		event.glowing = true
		event.color = RED

		assertEquals(ARGB.opaque(RED), event.outline())
	}

	@Test
	fun `a boss bar carries the vanilla bar and renders by default`() {
		var seen: BossEvent? = null
		bus.subscribe<BossBarUpdateEvent> { seen = it.bossBar }
		val bar = bossBar("The Watcher")

		assertFalse(RenderHooks.bossBarCancelled(bar))
		assertSame(bar, seen)
	}

	@Test
	fun `cancelling a boss bar hides it`() {
		bus.subscribe<BossBarUpdateEvent> { it.cancel() }

		assertTrue(RenderHooks.bossBarCancelled(bossBar("Maxor")))
	}

	@Test
	fun `one cancelled boss bar leaves the next one alone`() {
		val seen = mutableListOf<BossBarUpdateEvent>()
		bus.subscribe<BossBarUpdateEvent> {
			seen += it
			if (it.bossBar.name.string == "Maxor") it.cancel()
		}

		assertTrue(RenderHooks.bossBarCancelled(bossBar("Maxor")))
		assertFalse(RenderHooks.bossBarCancelled(bossBar("Storm")))
		assertSame(seen[0], seen[1])
	}

	@Test
	fun `a boss bar with no subscriber never touches the shared event`() {
		var seen: BossEvent? = null
		val handle = bus.subscribe<BossBarUpdateEvent> { seen = it.bossBar }
		val first = bossBar("Goldor")

		RenderHooks.bossBarCancelled(first)
		handle.unsubscribe()
		RenderHooks.bossBarCancelled(bossBar("Necron"))

		assertSame(first, seen)
	}

	@Test
	fun `a glow event releases its entity when dispatch returns`() {
		var seen: EntityGlowEvent? = null
		bus.subscribe<EntityGlowEvent> { seen = it }
		RenderHooks.entityOutline(uninitialized<ItemEntity>(), EntityRenderState.NO_OUTLINE)

		assertThrows(NullPointerException::class.java) { seen!!.entity }
	}

	@Test
	fun `an entity render event releases its entity when dispatch returns`() {
		var seen: EntityRenderEvent? = null
		bus.subscribe<EntityRenderEvent> { seen = it }
		RenderHooks.entityRenderCancelled(uninitialized<ItemEntity>())

		assertThrows(NullPointerException::class.java) { seen!!.entity }
	}

	@Test
	fun `a glow event exposes its entity while dispatch is active`() {
		val entity = uninitialized<ItemEntity>()
		var seen: ItemEntity? = null
		bus.subscribe<EntityGlowEvent> { seen = it.entity as ItemEntity }
		RenderHooks.entityOutline(entity, EntityRenderState.NO_OUTLINE)

		assertSame(entity, seen)
	}

	@Test
	fun `a boss bar event releases its bar when dispatch returns`() {
		var seen: BossBarUpdateEvent? = null
		bus.subscribe<BossBarUpdateEvent> { seen = it }

		RenderHooks.bossBarCancelled(bossBar("Maxor"))

		assertThrows(NullPointerException::class.java) { seen!!.bossBar }
	}

	@Test
	fun `uninstalling stops the render events`() {
		var seen = false
		bus.subscribe<BossBarUpdateEvent> { seen = true }

		RenderHooks.uninstall()

		assertFalse(RenderHooks.bossBarCancelled(bossBar("The Watcher")))
		assertFalse(seen)
		assertFalse(RenderHooks.active())
		assertFalse(bus.type<WorldChangeEvent>().hasSubscribers)
	}

	@Test
	fun `a throwing handler latches render events off`() {
		bus.subscribe<BossBarUpdateEvent> { error("boom") }

		assertFalse(RenderHooks.bossBarCancelled(bossBar("Thorn")))

		assertFalse(RenderHooks.active())
	}

	@Test
	fun `a throwing glow handler hands back the outline vanilla computed`() {
		bus.subscribe<EntityGlowEvent> { error("boom") }

		assertEquals(TEAM_OUTLINE, RenderHooks.entityOutline(uninitialized<ItemEntity>(), TEAM_OUTLINE))

		assertFalse(RenderHooks.active())
	}

	private fun bossBar(name: String): BossEvent = LerpingBossEvent(
		UUID.nameUUIDFromBytes(name.toByteArray()),
		Component.literal(name),
		1f,
		BossEvent.BossBarColor.RED,
		BossEvent.BossBarOverlay.PROGRESS,
		false,
		false,
		false
	)

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()

		private val TEAM_OUTLINE = ARGB.opaque(0x55D6C2)
		private const val RED = 0xFF0000
	}
}
