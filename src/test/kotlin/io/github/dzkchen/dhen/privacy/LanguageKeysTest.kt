package io.github.dzkchen.dhen.privacy

import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.function.BiConsumer
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources
import net.fabricmc.loader.api.metadata.ModMetadata
import net.minecraft.server.packs.PackResources
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageKeysTest {
	@AfterEach
	fun reset() {
		LanguageKeys.uninstall()
		ModRegistry.mode = ModRegistry.Mode.AUTO
	}

	@Test
	fun `a fake resource sequence classifies vanilla mod and server entries`() {
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("menu.disconnect")
		LanguageKeys.recordMod("example", "key.example.menu")
		LanguageKeys.recordServer("menu.disconnect", "Stay connected")
		LanguageKeys.commitReload()

		assertTrue(LanguageKeys.isVanillaKey("menu.disconnect"))
		assertTrue(LanguageKeys.isModKey("key.example.menu"))
		assertEquals("example", LanguageKeys.ownerOf("key.example.menu"))
		assertEquals("Stay connected", LanguageKeys.serverPackValue("menu.disconnect"))
	}

	@Test
	fun `pack classification tracks loaded mods and server packs but not user packs`() {
		val values = HashMap<String, String>()
		val output = BiConsumer<String, String> { key, value -> values[key] = value }
		LanguageKeys.beginReload()
		LanguageKeys.trackingConsumer(compositePack(modPack("example")), output).accept("key.example.open", "Open")
		LanguageKeys.trackingConsumer(pack("server/00000001/example"), output).accept("server.message", "Server")
		LanguageKeys.trackingConsumer(pack("file/user"), output).accept("user.message", "User")
		LanguageKeys.commitReload()

		assertEquals("example", LanguageKeys.ownerOf("key.example.open"))
		assertEquals("Server", LanguageKeys.serverPackValue("server.message"))
		assertNull(LanguageKeys.ownerOf("user.message"))
		assertNull(LanguageKeys.serverPackValue("user.message"))
		assertEquals("User", values["user.message"])
		ModRegistry.mode = ModRegistry.Mode.BLOCK_ALL
		assertFalse(LanguageKeys.isWhitelistedKey("key.example.open"))
		assertFalse(LanguageKeys.isWhitelistedKey("unknown"))
	}

	@Test
	fun `readers see the old snapshot until the reload commits`() {
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("old")
		LanguageKeys.commitReload()
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("new")

		assertTrue(LanguageKeys.isVanillaKey("old"))
		assertFalse(LanguageKeys.isVanillaKey("new"))
		LanguageKeys.commitReload()
		assertFalse(LanguageKeys.isVanillaKey("old"))
		assertTrue(LanguageKeys.isVanillaKey("new"))
	}

	@Test
	fun `aborted and restarted reloads never publish partial state`() {
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("stable")
		LanguageKeys.commitReload()

		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("partial")
		LanguageKeys.abortReload()

		assertTrue(LanguageKeys.isVanillaKey("stable"))
		assertFalse(LanguageKeys.isVanillaKey("partial"))
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("stale")
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("current")
		LanguageKeys.commitReload()

		assertFalse(LanguageKeys.isVanillaKey("stale"))
		assertTrue(LanguageKeys.isVanillaKey("current"))
	}

	@Test
	fun `clear empties every query and rejects another thread's stale commit`() {
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla("vanilla")
		LanguageKeys.recordMod("example", "mod")
		LanguageKeys.recordServer("server", "value")
		LanguageKeys.commitReload()
		val staged = CountDownLatch(1)
		val release = CountDownLatch(1)
		val reload = thread {
			LanguageKeys.beginReload()
			LanguageKeys.recordVanilla("stale")
			staged.countDown()
			release.await()
			LanguageKeys.commitReload()
		}
		staged.await()

		LanguageKeys.clearCache()
		release.countDown()
		reload.join()

		assertFalse(LanguageKeys.isVanillaKey("vanilla"))
		assertFalse(LanguageKeys.isModKey("mod"))
		assertNull(LanguageKeys.ownerOf("mod"))
		assertNull(LanguageKeys.serverPackValue("server"))
		assertFalse(LanguageKeys.isVanillaKey("stale"))
	}

	private fun pack(id: String): PackResources =
		Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PackResources::class.java)) { _, method, _ ->
			if (method.name == "packId") id else throw UnsupportedOperationException(method.name)
		} as PackResources

	private fun modPack(id: String): PackResources {
		val metadata = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ModMetadata::class.java)) { _, method, _ ->
			if (method.name == "getId") id else throw UnsupportedOperationException(method.name)
		}
		return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ModPackResources::class.java)) { _, method, _ ->
			if (method.name == "getFabricModMetadata") metadata else throw UnsupportedOperationException(method.name)
		} as PackResources
	}

	private fun compositePack(primary: PackResources): PackResources =
		Proxy.newProxyInstance(
			javaClass.classLoader,
			arrayOf(PackResources::class.java, CompositePackAccess::class.java)
		) { _, method, _ ->
			if (method.name == "dhenPrimaryPack") primary else throw UnsupportedOperationException(method.name)
		} as PackResources
}
