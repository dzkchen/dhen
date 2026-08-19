package io.github.dzkchen.dhen.data

object SkyBlockLocation {
	var onHypixel: Boolean = false
		private set

	var inSkyBlock: Boolean = false
		private set

	var island: Island = Island.NONE
		private set

	var area: String? = null
		private set

	var serverName: String? = null
		private set

	var mode: String? = null
		private set

	internal fun greeted() {
		onHypixel = true
	}

	internal fun located(serverName: String, skyBlock: Boolean, mode: String?, map: String?) {
		onHypixel = true
		this.serverName = serverName
		this.mode = mode
		inSkyBlock = skyBlock
		if (!skyBlock) {
			island = Island.NONE
			area = null
		} else if (mode != null) {
			island = Island.ofMode(mode)
			area = map
		}
	}

	internal fun reset() {
		onHypixel = false
		inSkyBlock = false
		island = Island.NONE
		area = null
		serverName = null
		mode = null
	}
}
