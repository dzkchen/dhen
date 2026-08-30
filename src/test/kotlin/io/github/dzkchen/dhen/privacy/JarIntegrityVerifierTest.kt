package io.github.dzkchen.dhen.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat

class JarIntegrityVerifierTest {
	@Test
	fun `matching release digest is clean`() {
		val actual = sha256("official")

		val result = JarIntegrityVerifier.verify(release(actual), MC_VERSION, MOD_VERSION, actual, RELEASE_URL)

		assertInstanceOf(IntegrityResult.Clean::class.java, result)
	}

	@Test
	fun `different release digest is tampered`() {
		val expected = sha256("official")
		val actual = sha256("changed")

		val result = JarIntegrityVerifier.verify(release(expected), MC_VERSION, MOD_VERSION, actual, RELEASE_URL)

		val tampered = assertInstanceOf(IntegrityResult.Tampered::class.java, result)
		assertEquals(expected, tampered.expected)
		assertEquals(actual, tampered.actual)
		assertEquals(RELEASE_URL, tampered.releaseUrl)
	}

	@Test
	fun `missing matching asset digest skips`() {
		val actual = sha256("official")

		val missing = JarIntegrityVerifier.verify("""{"assets":[]}""", MC_VERSION, MOD_VERSION, actual, RELEASE_URL)
		val malformed = JarIntegrityVerifier.verify(release("bad"), MC_VERSION, MOD_VERSION, actual, RELEASE_URL)

		assertInstanceOf(IntegrityResult.Skipped::class.java, missing)
		assertInstanceOf(IntegrityResult.Skipped::class.java, malformed)
	}

	@Test
	fun `inclusive Minecraft range matches numerically`() {
		val actual = sha256("official")
		val json = release(actual, "25.10-26.3")

		val result = JarIntegrityVerifier.verify(json, MC_VERSION, MOD_VERSION, actual, RELEASE_URL)

		assertInstanceOf(IntegrityResult.Clean::class.java, result)
	}

	private fun release(digest: String, range: String = MC_VERSION): String =
		"""{"assets":[{"name":"dhen-$range+v$MOD_VERSION.jar","digest":"sha256:$digest"}]}"""

	private fun sha256(value: String): String =
		HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

	private companion object {
		const val MC_VERSION = "26.2"
		const val MOD_VERSION = "1.0.0"
		const val RELEASE_URL = "https://github.com/dzkchen/dhen/releases/tag/v1.0.0"
	}
}
