package io.github.dzkchen.dhen.privacy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

private fun answering(vararg addresses: String) =
	LocalAddresses.Resolver { Array(addresses.size) { InetAddress.getByName(addresses[it]) } }

class LocalAddressesTest {
	@Test
	fun `every private family reads as local`() {
		assertTrue(LocalAddresses.isLocal("any", answering("0.0.0.0")))
		assertTrue(LocalAddresses.isLocal("loopback", answering("127.0.0.1")))
		assertTrue(LocalAddresses.isLocal("loopback v6", answering("::1")))
		assertTrue(LocalAddresses.isLocal("site", answering("192.168.1.20")))
		assertTrue(LocalAddresses.isLocal("site 10", answering("10.4.4.4")))
		assertTrue(LocalAddresses.isLocal("site 172", answering("172.16.9.9")))
		assertTrue(LocalAddresses.isLocal("link", answering("169.254.7.7")))
		assertTrue(LocalAddresses.isLocal("link v6", answering("fe80::1")))
	}

	@Test
	fun `the two private ranges the four vanilla predicates miss are local too`() {
		assertTrue(LocalAddresses.isLocal("unique local", answering("fd7a:115c:a1e0::1")))
		assertTrue(LocalAddresses.isLocal("unique local edge", answering("fc00::1")))
		assertTrue(LocalAddresses.isLocal("shared", answering("100.64.0.1")))
		assertTrue(LocalAddresses.isLocal("shared edge", answering("100.127.255.254")))
	}

	@Test
	fun `an address just outside those ranges stays public`() {
		assertFalse(LocalAddresses.isLocal("above shared", answering("100.128.0.1")))
		assertFalse(LocalAddresses.isLocal("below shared", answering("100.63.255.255")))
		assertFalse(LocalAddresses.isLocal("global v6", answering("fe00::1")))
	}

	@Test
	fun `a public address is not local`() {
		assertFalse(LocalAddresses.isLocal("public", answering("93.184.216.34")))
		assertFalse(LocalAddresses.isLocal("public v6", answering("2606:2800:220:1:248:1893:25c8:1946")))
	}

	@Test
	fun `one private answer among public ones is enough`() {
		assertTrue(LocalAddresses.isLocal("rebinding", answering("93.184.216.34", "8.8.8.8", "127.0.0.1")))
	}

	@Test
	fun `an unresolvable host fails the download instead of passing`() {
		val resolver = LocalAddresses.Resolver { throw UnknownHostException(it) }
		assertThrows(UnknownHostException::class.java) { LocalAddresses.isLocal("nowhere", resolver) }
	}
}
