package io.github.dzkchen.dhen.data

object SkyBlockLocation {
	private const val GUEST_TITLE = "GUEST"

	private var unconfirmedIsland: Island? = null

	@Volatile
	var onHypixel: Boolean = false
		private set

	private var onIsland: Boolean = false

	internal var simulated: () -> Boolean = { false }

	val inSkyBlock: Boolean
		get() = onIsland || simulated()

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

	internal fun located(
		serverName: String?,
		skyBlock: Boolean,
		mode: String?,
		map: String?,
		resolvedIsland: Island? = mode?.let(Island::ofMode)
	) {
		onHypixel = true
		this.serverName = serverName
		this.mode = mode
		onIsland = skyBlock
		isGuest = false
		unconfirmedIsland = null
		if (!skyBlock) {
			island = Island.NONE
			area = null
		} else if (resolvedIsland != null) {
			val guestHost = resolvedIsland.guestHost
			area = map
			if (guestHost != null) {
				isGuest = true
				island = resolvedIsland
			} else if (resolvedIsland.guest != null) {
				unconfirmedIsland = resolvedIsland
			} else {
				island = resolvedIsland
			}
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
		onIsland = false
		island = Island.NONE
		isGuest = false
		unconfirmedIsland = null
		area = null
		serverName = null
		mode = null
	}
}
