package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.bootstrapMinecraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class CustomSoundPackTest {
	@TempDir
	lateinit var directory: Path

	@AfterEach
	fun release() = CustomSoundPack.uninstall()

	@Test
	fun `a scan publishes legal ogg files and serves one stable manifest`() {
		val sounds = directory.resolve(CustomSoundPack.DIRECTORY)
		Files.createDirectories(sounds)
		Files.write(sounds.resolve("horn.ogg"), byteArrayOf(1, 2, 3))
		Files.write(sounds.resolve("Bad.ogg"), byteArrayOf(4))
		Files.write(sounds.resolve("wrong.wav"), byteArrayOf(5))
		Files.write(sounds.resolve("broken.ogg"), byteArrayOf(6))
		install()
		assertTrue(CustomSoundPack.identifiers().isEmpty())

		val result = CustomSoundPack.refresh { file ->
			if (file.fileName.toString() == "broken.ogg") throw IOException("broken")
		}
		val event = Dhen.id("custom/horn")
		val audio = Dhen.id("sounds/custom/horn.ogg")

		assertTrue(result.published)
		assertEquals(1, result.count)
		assertNull(result.failure)
		assertEquals(listOf(event), CustomSoundPack.identifiers())
		assertEquals("ogg", CustomSoundPack.formatOf(event))
		assertNull(CustomSoundPack.formatOf(Dhen.id("custom/missing")))
		assertEquals("OGG", CustomSoundPack.acceptedFormats())

		val resources = pack().open()
		val manifest = resources.getResource(PackType.CLIENT_RESOURCES, Dhen.id("sounds.json"))!!.get().use {
			String(it.readAllBytes(), StandardCharsets.UTF_8)
		}
		assertTrue(manifest.contains("custom/horn"))
		assertTrue(manifest.contains("stream"))
		assertArrayEquals(byteArrayOf(1, 2, 3), resources.getResource(PackType.CLIENT_RESOURCES, audio)!!.get().use { it.readAllBytes() })
		assertNotNull(resources.getRootResource("pack.mcmeta")!!.get().use { it.readAllBytes() })
		val listed = mutableListOf<Identifier>()
		resources.listResources(PackType.CLIENT_RESOURCES, Dhen.MOD_ID, "sounds") { id, _ -> listed += id }
		assertEquals(listOf(audio), listed)
		assertEquals(setOf(Dhen.MOD_ID), resources.getNamespaces(PackType.CLIENT_RESOURCES))
		assertTrue(resources.getNamespaces(PackType.SERVER_DATA).isEmpty())
	}

	@Test
	fun `the repository source contributes a required fixed top pack`() {
		val pack = pack()
		assertTrue(CustomSoundPack.isOwnPack(pack))
		assertTrue(pack.isRequired)
		assertTrue(pack.isFixedPosition)
		assertEquals(Pack.Position.TOP, pack.defaultPosition)
	}

	@Test
	fun `a newer scan wins and uninstall invalidates an overlapping scan`() {
		val sounds = directory.resolve(CustomSoundPack.DIRECTORY)
		Files.createDirectories(sounds)
		Files.write(sounds.resolve("first.ogg"), byteArrayOf(1))
		install()
		val started = CountDownLatch(1)
		val release = CountDownLatch(1)
		val first = AtomicReference<CustomSoundPack.ScanResult>()
		val thread = Thread {
			first.set(CustomSoundPack.refresh {
				started.countDown()
				release.await()
			})
		}
		thread.start()
		started.await()
		Files.write(sounds.resolve("second.ogg"), byteArrayOf(2))

		assertEquals(2, CustomSoundPack.refresh {}.count)
		release.countDown()
		thread.join()

		assertFalse(first.get().published)
		assertEquals(listOf(Dhen.id("custom/first"), Dhen.id("custom/second")), CustomSoundPack.identifiers())

		CustomSoundPack.uninstall()
		assertTrue(CustomSoundPack.identifiers().isEmpty())
	}

	@Test
	fun `a folder failure publishes an empty pack without escaping`() {
		Files.write(directory.resolve(CustomSoundPack.DIRECTORY), byteArrayOf(1))
		install()

		val result = CustomSoundPack.refresh {}

		assertTrue(result.published)
		assertEquals(0, result.count)
		assertNotNull(result.failure)
		assertTrue(CustomSoundPack.identifiers().isEmpty())
	}

	private fun install() = CustomSoundPack.install(directory, CoroutineScope(Dispatchers.Unconfined))

	private fun pack(): Pack {
		val packs = mutableListOf<Pack>()
		CustomSoundPack.repositorySource().loadPacks(packs::add)
		return packs.single()
	}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
