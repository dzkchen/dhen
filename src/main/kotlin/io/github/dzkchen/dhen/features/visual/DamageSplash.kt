package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.trackedName
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.digits
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.random.Random

object DamageSplash : Module(
	name = "Damage Splash",
	category = Category.VISUAL,
	description = "Reformats SkyBlock's damage indicators, or hides them outright."
) {
	internal val hideDamageSetting = BooleanSetting(
		"Hide Damage Nametag",
		description = "Hides every damage indicator."
	)
	internal val uppercaseSetting = BooleanSetting(
		"Uppercase Formatting",
		description = "Writes the shortened damage number in uppercase."
	).withDependency { !hideDamageSetting.on }

	private var hideDamage by hideDamageSetting
	private var uppercase by uppercaseSetting

	init {
		on<PacketReceiveEvent.Pre> { received(it) }
	}

	private fun received(event: PacketReceiveEvent.Pre) {
		if (!SkyBlockLocation.inSkyBlock) return
		val packet = event.packet as? ClientboundSetEntityDataPacket ?: return
		val stand = Minecraft.getInstance().level?.getEntity(packet.id) as? ArmorStand ?: return
		for (entry in packet.packedItems) {
			val name = trackedName(entry) ?: continue
			val formatted = legacyCodes(name)
			if (SECTION !in formatted) continue
			val damage = DAMAGE.matchEntire(withoutCodes(formatted))?.groupValues?.get(1) ?: continue
			if (hideDamage) {
				stand.remove(Entity.RemovalReason.DISCARDED)
				event.cancelled = true
				return
			}
			stand.entityData.assignValues(packet.packedItems)
			stand.customName = Component.literal(splash(damage, formatted))
			event.cancelled = true
			return
		}
	}

	internal fun splash(damage: String, formatted: String): String {
		val shortened = shorten(digits(damage)).let { if (uppercase) it.uppercase() else it }
		return if (formatted.any { it in CRIT_MARKERS }) "§f✧${scattered(shortened)}§f✧" else "§3$shortened"
	}

	internal fun shorten(value: Long): String {
		if (value < THOUSAND) return value.toString()
		var divisor = THOUSAND
		var suffix = 0
		while (suffix < SUFFIXES.length - 1 && value >= divisor * THOUSAND) {
			divisor *= THOUSAND
			suffix++
		}
		val tenths = value / (divisor / 10)
		val unit = SUFFIXES[suffix]
		return if (tenths < 100 && tenths % 10 != 0L) "${tenths / 10.0}$unit" else "${tenths / 10}$unit"
	}

	private fun scattered(text: String): String {
		val result = StringBuilder(text.length * SCATTER_WIDTH)
		var last = -1
		for (character in text) {
			last = if (last < 0) Random.nextInt(COLORS.size) else (last + 1 + Random.nextInt(COLORS.size - 1)) % COLORS.size
			result.append(COLORS[last]).append(character).append(RESET)
		}
		return result.toString()
	}

	private val DAMAGE = Regex("""[✧✯]?(\d{1,3}(?:,\d{3})*[⚔+✧❤♞☄✷ﬗ✯]*)""")
	private val COLORS = arrayOf("§6", "§c", "§e", "§f")
	private const val CRIT_MARKERS = "✧✯"
	private const val RESET = "§r"
	private const val SECTION = '§'
	private const val SCATTER_WIDTH = 6
	private const val THOUSAND = 1000L
	private const val SUFFIXES = "kmbtpe"
}
