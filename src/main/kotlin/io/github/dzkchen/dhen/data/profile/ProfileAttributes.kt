package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import java.util.Locale

class ShardHolding internal constructor(val amount: Int, val capturedAt: Long)

class ShardTrap internal constructor(
	val trapItem: String?,
	val mode: String?,
	val shard: String?,
	val location: String?,
	val captured: Boolean,
	val placedAt: Long,
	val capturedAt: Long,
	val toolkitIndex: Int?
)

class AttributesProfile internal constructor(
	val syphoned: Map<String, Int>,
	val shardsOwned: Map<String, ShardHolding>,
	val fusions: Int,
	val traps: List<ShardTrap>
)

internal object AttributesProfiles {
	fun of(member: JsonObject): AttributesProfile? {
		val stacks = member.obj("attributes")?.obj("stacks")
		val shards = member.obj("shards")
		if (stacks == null && shards == null) return null
		return AttributesProfile(
			syphoned = stacks.ints(),
			shardsOwned = owned(shards?.array("owned")),
			fusions = shards.int("fused"),
			traps = traps(shards?.obj("traps")?.array("active_traps"))
		)
	}

	private fun owned(owned: JsonArray?): Map<String, ShardHolding> = owned?.mapNotNull { entry ->
		val shard = entry as? JsonObject ?: return@mapNotNull null
		val type = shard.text("type")?.lowercase(Locale.ROOT) ?: return@mapNotNull null
		type to ShardHolding(
			amount = shard.int("amount_owned"),
			capturedAt = shard.long("captured")
		)
	}?.toMap() ?: emptyMap()

	private fun traps(active: JsonArray?): List<ShardTrap> = active?.mapNotNull { entry ->
		val trap = entry as? JsonObject ?: return@mapNotNull null
		ShardTrap(
			trapItem = trap.text("trap_item"),
			mode = trap.text("mode"),
			shard = trap.text("shard"),
			location = trap.text("location"),
			captured = trap.flag("captured"),
			placedAt = trap.long("placed_at"),
			capturedAt = trap.long("capture_time"),
			toolkitIndex = trap.number("hunting_toolkit_index")?.toInt()
		)
	} ?: emptyList()
}
