package io.github.dzkchen.dhen.privacy

import java.net.InetAddress

object LocalAddresses {
	fun interface Resolver {
		fun resolve(host: String): Array<InetAddress>
	}

	val SYSTEM = Resolver { InetAddress.getAllByName(it) }

	private const val IPV4_BYTES = 4
	private const val IPV6_BYTES = 16
	private const val OCTET = 0xff
	private const val UNIQUE_LOCAL_MASK = 0xfe
	private const val UNIQUE_LOCAL_PREFIX = 0xfc
	private const val SHARED_FIRST_OCTET = 100
	private const val SHARED_SECOND_MASK = 0xc0
	private const val SHARED_SECOND_OCTET = 0x40

	fun isLocal(address: InetAddress): Boolean =
		address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress ||
			address.isLinkLocalAddress || isUniqueLocal(address) || isSharedAddressSpace(address)

	fun isLocal(host: String, resolver: Resolver = SYSTEM): Boolean = resolver.resolve(host).any { isLocal(it) }

	private fun isUniqueLocal(address: InetAddress): Boolean {
		val bytes = address.address
		return bytes.size == IPV6_BYTES && (bytes[0].toInt() and UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX
	}

	private fun isSharedAddressSpace(address: InetAddress): Boolean {
		val bytes = address.address
		return bytes.size == IPV4_BYTES && (bytes[0].toInt() and OCTET) == SHARED_FIRST_OCTET &&
			(bytes[1].toInt() and SHARED_SECOND_MASK) == SHARED_SECOND_OCTET
	}
}
