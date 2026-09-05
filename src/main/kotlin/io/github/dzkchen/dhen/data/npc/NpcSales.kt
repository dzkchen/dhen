package io.github.dzkchen.dhen.data.npc

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.obj
import net.minecraft.client.Minecraft
import java.time.LocalDate
import java.time.ZoneOffset

object NpcSales {
	const val DAILY_LIMIT = 500_000_000L

	private var store: ConfigStore? = null
	private var account: () -> String = { Minecraft.getInstance().user.profileId.toString() }
	private var today: () -> Long = { LocalDate.now(ZoneOffset.UTC).toEpochDay() }
	private var accounts: JsonObject = JsonObject()

	private var key = ""
	private var day = 0L
	private var sold = 0L

	val soldToday: Long
		get() {
			roll()
			return sold
		}

	internal fun install(
		store: ConfigStore,
		account: () -> String = this.account,
		today: () -> Long = this.today
	) {
		this.store = store
		this.account = account
		this.today = today
		accounts = store.load().obj(ACCOUNTS) ?: JsonObject()
		key = ""
	}

	internal fun uninstall() {
		store = null
		accounts = JsonObject()
		key = ""
		day = 0L
		sold = 0L
	}

	fun record(coins: Long) {
		if (coins <= 0L) return
		roll()
		sold += coins
		save()
	}

	private fun roll() {
		val owner = account()
		if (owner != key) {
			key = owner
			val stored = accounts.obj(owner)
			day = stored.long(DAY, 0L)
			sold = stored.long(SOLD, 0L)
		}
		val now = today()
		if (day == now) return
		day = now
		sold = 0L
	}

	private fun save() {
		val record = JsonObject()
		record.addProperty(DAY, day)
		record.addProperty(SOLD, sold)
		accounts.add(key, record)
		val document = JsonObject()
		document.add(ACCOUNTS, accounts.deepCopy())
		store?.save(document)
	}

	private const val ACCOUNTS = "accounts"

	val authoritative = setOf(ACCOUNTS)
	private const val DAY = "day"
	private const val SOLD = "sold"
}
