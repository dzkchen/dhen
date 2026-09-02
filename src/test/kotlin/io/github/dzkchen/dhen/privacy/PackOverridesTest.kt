package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.repo.PackModelRepo
import io.github.dzkchen.dhen.data.repo.PackModelTable
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PackOverridesTest {
	@AfterEach
	fun reset() {
		PackOverrides.reverting = false
		PackOverrides.whitelist = emptySet()
		PackOverrides.replacements = emptyMap()
		PackOverrides.glints = emptyMap()
		PackOverrides.packTextShaders = null
		PackOverrides.onCooldown = { false }
		PackOverrides.forgetProfiles()
		PackModelRepo.table = PackModelTable.EMPTY
	}

	@BeforeEach
	fun arm() {
		PackOverrides.reverting = true
		PackOverrides.onCooldown = { false }
		PackModelRepo.table = table(
			"HYPERION" to "minecraft:diamond_sword",
			"VOIDEDGE_KATANA" to "minecraft:iron_sword",
			"FIREDUST_DAGGER" to "minecraft:iron_sword",
			"MAWDUST_DAGGER" to "minecraft:iron_sword",
			"FUNGI_CUTTER" to "minecraft:iron_hoe"
		)
	}

	@Test
	fun `a pack model reverts to the table's vanilla model`() {
		assertEquals(model(Items.DIAMOND_SWORD), PackOverrides.model(packed("HYPERION"), PACK_MODEL))
	}

	@Test
	fun `a model outside the pack namespace is left alone`() {
		assertSame(VANILLA_MODEL, PackOverrides.model(packed("HYPERION"), VANILLA_MODEL))
	}

	@Test
	fun `an item with no entry in the table is left alone`() {
		assertSame(PACK_MODEL, PackOverrides.model(packed("TERMINATOR"), PACK_MODEL))
	}

	@Test
	fun `a whitelisted item keeps the pack's model`() {
		PackOverrides.whitelist = setOf("HYPERION")

		assertSame(PACK_MODEL, PackOverrides.model(packed("HYPERION"), PACK_MODEL))
	}

	@Test
	fun `a replacement wins over the whitelist and over the pack namespace check`() {
		PackOverrides.whitelist = setOf("HYPERION")
		PackOverrides.replacements = mapOf("HYPERION" to VANILLA_MODEL)

		assertSame(VANILLA_MODEL, PackOverrides.model(packed("HYPERION"), PACK_MODEL))
		assertSame(VANILLA_MODEL, PackOverrides.model(packed("HYPERION"), PACK_MODEL))
	}

	@Test
	fun `a quiver arrow reverts by its pseudo id rather than a SkyBlock id`() {
		val arrow = ItemFixture.stack { putByte("quiver_arrow", 1) }

		assertEquals("quiver_arrow", PackOverrides.identity(arrow))
		assertEquals(model(Items.ARROW), PackOverrides.model(arrow, PACK_MODEL))
	}

	@Test
	fun `a whitelisted quiver arrow keeps the pack's model`() {
		PackOverrides.whitelist = setOf("quiver_arrow")

		assertSame(PACK_MODEL, PackOverrides.model(ItemFixture.stack { putByte("quiver_arrow", 1) }, PACK_MODEL))
	}

	@Test
	fun `a fire dagger becomes a golden sword only in attunement mode one`() {
		assertEquals(model(Items.GOLDEN_SWORD), PackOverrides.model(attuned("FIREDUST_DAGGER", 1), PACK_MODEL))
		assertEquals(model(Items.IRON_SWORD), PackOverrides.model(attuned("FIREDUST_DAGGER", 3), PACK_MODEL))
		assertEquals(model(Items.IRON_SWORD), PackOverrides.model(packed("FIREDUST_DAGGER"), PACK_MODEL))
	}

	@Test
	fun `a maw dagger becomes a diamond sword only in attunement mode three`() {
		assertEquals(model(Items.DIAMOND_SWORD), PackOverrides.model(attuned("MAWDUST_DAGGER", 3), PACK_MODEL))
		assertEquals(model(Items.IRON_SWORD), PackOverrides.model(attuned("MAWDUST_DAGGER", 1), PACK_MODEL))
	}

	@Test
	fun `a katana becomes a golden sword only while it is on cooldown`() {
		assertEquals(model(Items.IRON_SWORD), PackOverrides.model(packed("VOIDEDGE_KATANA"), PACK_MODEL))

		PackOverrides.onCooldown = { true }

		assertEquals(model(Items.GOLDEN_SWORD), PackOverrides.model(packed("VOIDEDGE_KATANA"), PACK_MODEL))
	}

	@Test
	fun `a fungi cutter follows its mode and falls through on any other value`() {
		assertEquals(model(Items.RED_MUSHROOM), PackOverrides.model(fungi("RED"), PACK_MODEL))
		assertEquals(model(Items.BROWN_MUSHROOM), PackOverrides.model(fungi("BROWN"), PACK_MODEL))
		assertEquals(model(Items.IRON_HOE), PackOverrides.model(fungi("SPORE"), PACK_MODEL))
	}

	@Test
	fun `nothing resolves while the feature is off`() {
		PackOverrides.reverting = false

		assertSame(PACK_MODEL, PackOverrides.model(packed("HYPERION"), PACK_MODEL))
	}

	@Test
	fun `a glint override forces the shimmer only on the items it names`() {
		PackOverrides.glints = mapOf("HYPERION" to true, "TERMINATOR" to false)

		assertEquals(true, PackOverrides.foil(packed("HYPERION"), false))
		assertEquals(false, PackOverrides.foil(packed("TERMINATOR"), true))
		assertEquals(true, PackOverrides.foil(packed("AOTE"), true))
		assertEquals(false, PackOverrides.foil(packed("AOTE"), false))
	}

	@Test
	fun `no glint override leaves the shimmer alone`() {
		assertEquals(true, PackOverrides.foil(packed("HYPERION"), true))

		PackOverrides.reverting = false
		PackOverrides.glints = mapOf("HYPERION" to false)

		assertEquals(true, PackOverrides.foil(packed("HYPERION"), true))
	}

	@Test
	fun `a head profile is substituted by skin id first and by SkyBlock id second`() {
		PackModelRepo.table = table(
			models = mapOf("HYPERION" to "minecraft:player_head"),
			textures = mapOf("HYPERION" to "sword-blob", "SPOOKY_SKIN" to "skin-blob")
		)

		assertEquals("SPOOKY_SKIN", name(skinned("HYPERION", "SPOOKY_SKIN")))
		assertEquals("HYPERION", name(packed("HYPERION")))
	}

	@Test
	fun `a real player head and an item with no texture keep their own profile`() {
		PackModelRepo.table = table(models = mapOf("HYPERION" to "minecraft:player_head"), textures = emptyMap())

		assertNull(PackOverrides.headProfile(packed("HYPERION"), null))
		assertNull(PackOverrides.headProfile(ItemStack(Items.PLAYER_HEAD), null))
	}

	@Test
	fun `replacement pairs upper-case the SkyBlock id but keep the quiver pseudo id`() {
		val parsed = PackOverrides.readReplacements("hyperion=diamond_sword QUIVER_ARROW=stick")

		assertEquals(model(Items.DIAMOND_SWORD), parsed["HYPERION"])
		assertEquals(model(Items.STICK), parsed["quiver_arrow"])
	}

	@Test
	fun `a replacement naming an unknown item is dropped rather than resolving to air`() {
		val parsed = PackOverrides.readReplacements("HYPERION=not_an_item AOTE=minecraft:ender_pearl BROKEN= =X")

		assertEquals(mapOf("AOTE" to model(Items.ENDER_PEARL)), parsed)
	}

	@Test
	fun `glint pairs read on and off and ignore anything else`() {
		val parsed = PackOverrides.readGlints("HYPERION=on aote=OFF TERMINATOR=maybe quiver_arrow=on")

		assertEquals(mapOf("HYPERION" to true, "AOTE" to false, "quiver_arrow" to true), parsed)
	}

	@Test
	fun `a quiver arrow replacement resolves through the pseudo id`() {
		PackOverrides.replacements = PackOverrides.readReplacements("quiver_arrow=stick")

		assertEquals(model(Items.STICK), PackOverrides.model(ItemFixture.stack { putByte("quiver_arrow", 1) }, PACK_MODEL))
	}

	private fun name(stack: ItemStack): String? = PackOverrides.headProfile(stack, null)?.partialProfile()?.name

	private fun model(item: net.minecraft.world.item.Item): Identifier? =
		item.components().get(DataComponents.ITEM_MODEL)

	private fun packed(id: String): ItemStack = ItemFixture.identified(id)

	private fun skinned(id: String, skin: String): ItemStack =
		ItemFixture.stack { putString("id", id); putString("skin", skin) }

	private fun attuned(id: String, mode: Int): ItemStack =
		ItemFixture.stack { putString("id", id); putInt("td_attune_mode", mode) }

	private fun fungi(mode: String): ItemStack =
		ItemFixture.stack { putString("id", "FUNGI_CUTTER"); putString("fungi_cutter_mode", mode) }

	private fun table(vararg entries: Pair<String, String>): PackModelTable =
		table(entries.toMap(), emptyMap())

	private fun table(models: Map<String, String>, textures: Map<String, String>): PackModelTable =
		PackModelTable(models.mapValues { PackModelRepo.itemModel(it.value)!! }, textures)

	private companion object {
		private val PACK_MODEL: Identifier = Identifier.parse("hypixel_skyblock:item/hyperion")
		private val VANILLA_MODEL: Identifier = Identifier.parse("minecraft:stick")

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
