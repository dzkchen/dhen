package io.github.dzkchen.dhen.data

object SkyBlockLocation {
	private const val GUEST_TITLE = "GUEST"

	private var unconfirmedIsland: Island? = null

	var onHypixel: Boolean = false
		private set

	var inSkyBlock: Boolean = false
		private set

	var island: Island = Island.NONE
		private set

	var isGuest: Boolean = false
		private set

	var area: String? = null
		private set

	var serverName: String? = null
		private set

	var mode: String? = null
		private set

	val awaitingGuestTitle: Boolean get() = unconfirmedIsland != null

	internal fun greeted() {
		onHypixel = true
	}

	internal fun located(serverName: String, skyBlock: Boolean, mode: String?, map: String?) {
		onHypixel = true
		this.serverName = serverName
		this.mode = mode
		inSkyBlock = skyBlock
		isGuest = false
		unconfirmedIsland = null
		if (!skyBlock) {
			island = Island.NONE
			area = null
		} else if (mode != null) {
			val located = Island.ofMode(mode)
			area = map
			if (located.guest != null) unconfirmedIsland = located else island = located
		}
	}

	internal fun titled(title: String) {
		val located = unconfirmedIsland ?: return
		unconfirmedIsland = null
		isGuest = title.trim().endsWith(GUEST_TITLE)
		island = if (isGuest) located.guest ?: located else located
	}

	internal fun reset() {
		onHypixel = false
		inSkyBlock = false
		island = Island.NONE
		isGuest = false
		unconfirmedIsland = null
		area = null
		serverName = null
		mode = null
	}
}
