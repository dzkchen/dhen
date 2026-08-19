package io.github.dzkchen.dhen.data.party

enum class PartyRole {
	LEADER,
	MOD,
	MEMBER
}

object PartyState {
	private val roster = mutableListOf<String>()

	var inParty: Boolean = false
		internal set

	var leader: String? = null
		private set

	var roles: Map<String, PartyRole> = emptyMap()
		private set

	internal var self: String? = null

	val members: List<String> get() = roster

	val isLeader: Boolean get() = leader != null && leader == self

	internal fun add(name: String): Boolean {
		inParty = true
		if (name in roster) return false
		roster += name
		return true
	}

	internal fun remove(name: String): Boolean {
		if (!roster.remove(name)) return false
		if (roles.containsKey(name)) roles = roles - name
		return true
	}

	internal fun lead(name: String?): Boolean {
		if (leader == name) return false
		leader = name
		return true
	}

	internal fun assign(roles: Map<String, PartyRole>): Boolean {
		if (this.roles == roles) return false
		this.roles = roles
		return true
	}

	internal fun disband(): Boolean {
		if (!inParty && leader == null && roster.isEmpty() && roles.isEmpty()) return false
		roster.clear()
		roles = emptyMap()
		leader = null
		inParty = false
		return true
	}

	internal fun reset() {
		disband()
		self = null
	}
}
