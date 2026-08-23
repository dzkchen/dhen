package io.github.dzkchen.dhen.data.mayor

import io.github.dzkchen.dhen.data.CachedFeed
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.MayorChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.world.item.ItemStack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object MayorService {
	private const val ELECTION_URL = "https://api.hypixel.net/v2/resources/skyblock/election"
	private const val PERKPOCALYPSE_MAYOR = "Jerry"
	private const val PERKPOCALYPSE_HEAD = "Mayor Jerry"
	private const val PERKPOCALYPSE_HEADER = "Perkpocalypse Perks:"
	private const val PERK_LINES_BELOW_HEADER = 2
	private const val PERKPOCALYPSE_TERMS_PER_YEAR = 21
	private const val NANOS_PER_MILLI = 1_000_000L

	private val POLL = 1.minutes
	private val REFRESH = 20.minutes
	private val PERKPOCALYPSE_TERM = 6.hours

	private val feed = CachedFeed("mayor", ELECTION_URL, REFRESH, ::readable, MayorReply::parse)
	private val failsafe = Failsafe("Dhen {} failed, its mayor data is off until restart")
	private val requirements = AtomicInteger()
	private val pumpLock = Any()
	private val calendarTitle =
		Pattern.compile("Calendar and Events|(?:Early |Late )?(?:Spring|Summer|Autumn|Winter), Year \\d+").matcher("")
	private val electionClosed =
		Pattern.compile("The election room is now closed\\. Clerk Seraphine is doing a final count of the votes\\.\\.\\.")
			.matcher("")

	private var subscriptions: Array<Handle> = emptyArray()

	@Volatile
	private var host: Host? = null

	@Volatile
	private var extraMayorPerk: String? = null

	@Volatile
	private var extraMayorUntil = 0L

	val required: Int get() = requirements.get()

	internal val polling: Boolean get() = host?.pump?.isActive == true

	val mayor: Mayor? get() = seated?.mayor

	val minister: String? get() = seated?.minister

	val ministerPerk: String? get() = seated?.ministerPerk

	val date: SkyBlockDate get() = SkyBlockCalendar.dateAt(now())

	val electedYear: Int get() = SkyBlockCalendar.electionYearAt(now())

	val nextElectionAt: Long get() = SkyBlockCalendar.nextElectionAt(now())

	val perkpocalypsePerk: String? get() = extraMayorPerk?.takeIf { now() < extraMayorUntil }

	fun isPerkActive(perk: String): Boolean {
		val reply = seated
		if (reply != null && (perk in reply.mayor.perks || perk == reply.ministerPerk)) return true
		return perk == perkpocalypsePerk
	}

	fun require(): Handle {
		val host = host ?: return Handle {}
		if (!adjust(host, 1)) return Handle {}
		val released = AtomicBoolean()
		return Handle { if (released.compareAndSet(false, true)) adjust(host, -1) }
	}

	private fun adjust(host: Host, delta: Int): Boolean = synchronized(pumpLock) {
		if (this.host !== host) return false
		if (requirements.addAndGet(delta) == 0) {
			host.pump?.cancel()
			host.pump = null
		} else if (host.pump?.isActive != true) {
			host.pump = host.scope.launch { poll(host) }
		}
		true
	}

	fun active(): Boolean = host != null

	internal val failedRefreshes: Int get() = feed.failures

	internal fun install(
		scope: CoroutineScope,
		bus: EventBus,
		clientDispatcher: CoroutineDispatcher,
		web: WebSource = WebClient(),
		epochMillis: () -> Long = System::currentTimeMillis,
		onHypixel: () -> Boolean = { SkyBlockLocation.onHypixel },
		inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock }
	) {
		uninstall()
		host = Host(scope, bus, clientDispatcher, web, epochMillis, onHypixel, inSkyBlock)
		subscriptions = arrayOf(
			bus.subscribe<ContainerReadyEvent> { event -> guarded("calendar mayor read") { readExtraMayor(it, event) } },
			bus.subscribe<ChatReceiveEvent> { event -> guarded("election chat") { forgetOnElectionClose(it, event) } }
		)
	}

	internal fun uninstall() = synchronized(pumpLock) {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		host?.pump?.cancel()
		host = null
		requirements.set(0)
		extraMayorPerk = null
		extraMayorUntil = 0L
		feed.reset()
	}

	private val seated: MayorReply?
		get() {
			val reply = feed.value ?: return null
			return if (elected(reply)) reply else null
		}

	private fun readable(reply: MayorReply): Int = if (elected(reply)) 1 else 0

	private fun elected(reply: MayorReply): Boolean = reply.lastUpdated >= SkyBlockCalendar.electedAt(now())

	private fun now(): Long {
		val host = host ?: return System.currentTimeMillis()
		return host.epochMillis()
	}

	private suspend fun poll(host: Host) = coroutineScope {
		while (isActive) {
			if (requirements.get() > 0 && host.onHypixel()) refresh(host)
			delay(POLL)
		}
	}

	private suspend fun refresh(host: Host) = coroutineScope {
		val previous = seated?.mayor?.name
		val stamp = host.epochMillis() * NANOS_PER_MILLI
		if (!feed.stale(stamp, POLL)) return@coroutineScope
		if (previous != null && !feed.stale(stamp)) return@coroutineScope
		if (!feed.refresh(host.web, stamp) { this@MayorService.host === host && isActive }) return@coroutineScope
		val current = seated?.mayor?.name
		if (current == previous) return@coroutineScope
		val change = host.bus.type<MayorChangeEvent>()
		if (change.hasSubscribers) launch(host.clientDispatcher) { change.dispatch(MayorChangeEvent(current, previous)) }
	}

	private fun readExtraMayor(host: Host, event: ContainerReadyEvent) {
		if (!host.inSkyBlock() || mayor?.name != PERKPOCALYPSE_MAYOR) return
		if (!calendarTitle.reset(withoutCodes(event.title.string)).matches()) return
		for (stack in event.stacks) {
			if (withoutCodes(stack.hoverName.string) != PERKPOCALYPSE_HEAD) continue
			val perk = perkBelowHeader(stack) ?: continue
			extraMayorPerk = perk
			extraMayorUntil = extraMayorExpiry(host.epochMillis())
			return
		}
	}

	private fun perkBelowHeader(stack: ItemStack): String? {
		val lore = SkyBlockItems.lore(stack)
		for (index in lore.indices) {
			if (withoutCodes(lore[index].string) != PERKPOCALYPSE_HEADER) continue
			val perk = lore.getOrNull(index + PERK_LINES_BELOW_HEADER) ?: return null
			return withoutCodes(perk.string).trim().ifEmpty { null }
		}
		return null
	}

	private fun extraMayorExpiry(now: Long): Long {
		val ends = SkyBlockCalendar.nextElectionAt(now)
		val termStarted = SkyBlockCalendar.electedAt(now)
		for (term in 1..PERKPOCALYPSE_TERMS_PER_YEAR) {
			val rotates = termStarted + term * PERKPOCALYPSE_TERM.inWholeMilliseconds
			if (rotates > now) return minOf(rotates, ends)
		}
		return ends
	}

	private fun forgetOnElectionClose(host: Host, event: ChatReceiveEvent) {
		if (!host.inSkyBlock() || !electionClosed.reset(event.stripped).matches()) return
		val previous = seated?.mayor?.name
		feed.reset()
		extraMayorPerk = null
		extraMayorUntil = 0L
		if (previous != null) host.bus.type<MayorChangeEvent>().dispatch(MayorChangeEvent(null, previous))
	}

	private inline fun guarded(label: String, block: (Host) -> Unit) {
		val host = host ?: return
		try {
			block(host)
		} catch (throwable: Throwable) {
			uninstall()
			failsafe.fail(label, throwable)
		}
	}

	private class Host(
		val scope: CoroutineScope,
		val bus: EventBus,
		val clientDispatcher: CoroutineDispatcher,
		val web: WebSource,
		val epochMillis: () -> Long,
		val onHypixel: () -> Boolean,
		val inSkyBlock: () -> Boolean
	) {
		@Volatile
		var pump: Job? = null
	}
}
