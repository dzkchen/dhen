package io.github.dzkchen.dhen.data

interface DungeonContext {
	val floorNumber: Int

	val inBossRoom: Boolean

	val roomName: String?
}

object Dungeons {
	@Volatile
	var context: DungeonContext? = null
}
