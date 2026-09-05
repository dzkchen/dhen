package io.github.dzkchen.dhen.data.sack

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj

internal object SackState {
	private var store: ConfigStore? = null
	private var profile: () -> String? = { ProfileHooks.profile }
	private var profiles: JsonObject = JsonObject()

	private var key = ""
	private val counts = HashMap<String, Long>()
	private val openRows = ArrayList<SackRow>()

	private var full = 0L

	var sackTitle: String = ""
		private set

	val inSack: Boolean
		get() = sackTitle.isNotEmpty()

	val rows: List<SackRow>
		get() = openRows

	internal fun editableRows(): MutableList<SackRow> = openRows

	fun isFull(slot: Int): Boolean = slot in 0 until FULL_SLOTS && full and (1L shl slot) != 0L

	internal fun markFull(rows: List<SackRow>) {
		full = 0L
		for (row in rows) if (row.full && row.slot in 0 until FULL_SLOTS) full = full or (1L shl row.slot)
	}

	internal fun enterSack(title: String) {
		sackTitle = title
		openRows.clear()
	}

	internal fun leaveSack() {
		sackTitle = ""
		openRows.clear()
		full = 0L
	}

	val contents: Map<String, Long>
		get() {
			load()
			return counts
		}

	internal fun install(store: ConfigStore, profile: () -> String? = this.profile) {
		this.store = store
		this.profile = profile
		profiles = store.load().obj(PROFILES) ?: JsonObject()
		key = ""
		counts.clear()
	}

	internal fun uninstall() {
		store = null
		profiles = JsonObject()
		key = ""
		counts.clear()
		leaveSack()
	}

	fun record(amounts: Map<String, Long>) {
		if (amounts.isEmpty()) return
		load()
		if (key.isEmpty()) return
		var changed = false
		for ((marketId, amount) in amounts) {
			if (counts.put(marketId, amount) != amount) changed = true
		}
		if (changed) save()
	}

	private fun load() {
		val owner = profile() ?: return
		if (owner == key) return
		key = owner
		counts.clear()
		val stored = profiles.obj(owner) ?: return
		for (marketId in stored.keys()) {
			val amount = stored.number(marketId)?.toLong() ?: continue
			if (amount > 0L) counts[marketId] = amount
		}
	}

	private fun save() {
		val stored = JsonObject()
		for ((marketId, amount) in counts) if (amount > 0L) stored.addProperty(marketId, amount)
		profiles.add(key, stored)
		val document = JsonObject()
		document.add(PROFILES, profiles.deepCopy())
		store?.save(document)
	}

	private const val FULL_SLOTS = 64
	private const val PROFILES = "profiles"

	val authoritative = setOf(PROFILES)
}
