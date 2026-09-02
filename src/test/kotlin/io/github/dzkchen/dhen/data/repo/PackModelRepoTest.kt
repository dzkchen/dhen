package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PackModelRepoTest {
	@TempDir
	lateinit var root: Path

	@Test
	fun `an unknown item id resolves to nothing rather than to air`() {
		assertNull(PackModelRepo.itemModel("minecraft:not_an_item"))
		assertNull(PackModelRepo.itemModel("a b c"))
		assertEquals(
			Items.DIAMOND_SWORD.components().get(DataComponents.ITEM_MODEL),
			PackModelRepo.itemModel("minecraft:diamond_sword")
		)
	}

	@Test
	fun `the table keeps models and textures and drops entries naming an unknown item`() {
		val table = PackModelRepo.read(
			write(
				"""
				{
				  "HYPERION": { "model": "minecraft:diamond_sword" },
				  "SPOOKY_HEAD": { "model": "minecraft:player_head", "texture": "blob" },
				  "GONE": { "model": "minecraft:skull" }
				}
				""".trimIndent()
			)
		)!!

		assertEquals(2, table.size)
		assertEquals(Items.DIAMOND_SWORD.components().get(DataComponents.ITEM_MODEL), table.model("HYPERION"))
		assertEquals("blob", table.texture("SPOOKY_HEAD"))
		assertNull(table.model("GONE"))
		assertNull(table.texture("HYPERION"))
	}

	@Test
	fun `a missing file reads as nothing`() {
		assertNull(PackModelRepo.read(root.resolve("absent.json")))
	}

	private fun write(json: String): Path =
		root.resolve("skyblock-items.json").also { Files.writeString(it, json) }

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
