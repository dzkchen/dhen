package io.github.dzkchen.dhen.event

internal enum class ContainerSignal { NONE, READY, UPDATED }

internal class ContainerTracker {
	private var pendingUpdate = false

	var windowId: Int = NO_WINDOW
		private set

	var slotCount: Int = 0
		private set

	var ready: Boolean = false
		private set

	val tracking: Boolean get() = windowId != NO_WINDOW

	fun opened(windowId: Int, slotCount: Int) {
		this.windowId = windowId
		this.slotCount = slotCount
		ready = false
		pendingUpdate = false
	}

	fun contentSet(windowId: Int, itemCount: Int): ContainerSignal {
		if (!follows(windowId)) return ContainerSignal.NONE
		if (ready) return updated()
		return if (itemCount >= slotCount) completed() else ContainerSignal.NONE
	}

	fun slotSet(windowId: Int, slot: Int): ContainerSignal {
		if (!follows(windowId)) return ContainerSignal.NONE
		if (ready) return updated()
		return if (slot >= slotCount - 1) completed() else ContainerSignal.NONE
	}

	fun closed(windowId: Int): Boolean {
		if (!follows(windowId)) return false
		reset()
		return true
	}

	fun flushUpdate(): Boolean {
		if (!pendingUpdate) return false
		pendingUpdate = false
		return true
	}

	fun reset() {
		windowId = NO_WINDOW
		slotCount = 0
		ready = false
		pendingUpdate = false
	}

	private fun follows(windowId: Int): Boolean = tracking && windowId == this.windowId

	private fun completed(): ContainerSignal {
		ready = true
		pendingUpdate = false
		return ContainerSignal.READY
	}

	private fun updated(): ContainerSignal {
		pendingUpdate = true
		return ContainerSignal.UPDATED
	}

	internal companion object {
		const val NO_WINDOW = -1
	}
}
