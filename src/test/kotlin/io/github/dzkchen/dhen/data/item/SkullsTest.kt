package io.github.dzkchen.dhen.data.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class SkullsTest {
	@Test
	fun `a name the table does not carry resolves to nothing`() {
		assertNull(Skulls.texture("NOT_A_SKULL"))
		assertNull(Skulls.texture(""))
		assertNull(Skulls.texture("fire_freeze_skulls"))
	}

	@Test
	fun `every named skull carries the skin its source names it by`() {
		assertEquals("194a5e8feaf959a727d6f07f8382dc39280e466c4d8830eb0abc38c464bfd7bc", skinOf("FIRE_FREEZE_SKULLS"))
		assertEquals("7b3328d3e9d710420322555b17239307f12270adf81bf63afc50faa04b5c06e1", skinOf("THUNDER_SPARK"))
		assertEquals("37d83674926b89714e6b5a55470501c04b066dd87bf6c335cddc6e60a1a1a5f5", skinOf("TENTACLE"))
		assertEquals("2f24ed6875304fa4a1f0c785b2cb6a6a72563e9f3e24ea55e18178452119aa66", skinOf("DUNGEONS_SOUL_WEAVER"))
		assertEquals("5ee4bb4821d0f5ed865c21090a80b5ee7d52268476ee25d389710f7cc9f110d6", skinOf("DUNGEONS_ABILITY_ORB"))
		assertEquals("1578b4af3fdd9151b850b13c67c4580224c7f60052713f2d151f7c15dc0d7b34", skinOf("DUNGEONS_SUPPORT_ORB"))
		assertEquals("db896580c7c34463298741b6607ad563332a1a4d0e89f7d75acdf79937a0e772", skinOf("LESSER_ORB"))
	}

	private fun skinOf(name: String): String {
		val encoded = Skulls.texture(name)
		assertNotNull(encoded)
		return String(Base64.getDecoder().decode(encoded)).substringAfterLast(TEXTURE_URL).substringBefore('"')
	}

	private companion object {
		const val TEXTURE_URL = "http://textures.minecraft.net/texture/"
	}
}
