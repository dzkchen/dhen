package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PetMenuTest {
	private val channels = PetHooks.Channels()

	@BeforeEach
	@AfterEach
	fun reset() {
		CurrentPet.reset()
		PetStorage.reset()
	}

	@Test
	fun `the item whose lore is three from the end says despawn becomes the active pet`() {
		val stacks = listOf(
			pet("§8[Lvl 100] §6Mosquito", "§7Held Item: None", "§eClick to summon!", "§8Right-click to view"),
			pet("§8[Lvl 200] §6Golden Dragon", "§eClick to despawn!", "§8Right-click to view", "§6§lLEGENDARY PET")
		)

		channels.petsMenu("Pets", stacks)

		assertEquals("§6Golden Dragon", CurrentPet.name)
		assertEquals("Golden Dragon", CurrentPet.bareName)
		assertEquals(1, CurrentPet.menuSlot)
	}

	@Test
	fun `the active pet carries the uuid out of its pet info, not the item's own uuid`() {
		val stacks = listOf(pet("§8[Lvl 100] §6Mosquito", "§eClick to despawn!", "a", "b"))

		channels.petsMenu("Pets", stacks)

		assertEquals(PET_UUID, CurrentPet.uuid)
	}

	@Test
	fun `a later open with no despawn line re-finds the slot by the remembered uuid`() {
		channels.petsMenu("Pets", listOf(pet("§8[Lvl 100] §6Mosquito", "§eClick to despawn!", "a", "b")))
		CurrentPet.deselect()

		channels.petsMenu(
			"Pets",
			listOf(
				ItemStack.EMPTY,
				pet("§8[Lvl 100] §6Mosquito", "§eClick to summon!", "a", "b")
			)
		)

		assertEquals(1, CurrentPet.menuSlot)
		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `a container that is not the Pets menu clears the slot and touches nothing else`() {
		channels.petsMenu("Pets", listOf(pet("§8[Lvl 100] §6Mosquito", "§eClick to despawn!", "a", "b")))

		channels.petsMenu("Your Backpack", listOf(pet("§8[Lvl 90] §5Rabbit", "§eClick to despawn!", "a", "b")))

		assertEquals(CurrentPet.NO_SLOT, CurrentPet.menuSlot)
		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `an item with too little lore is never the active pet`() {
		channels.petsMenu("Pets", listOf(pet("§8[Lvl 100] §6Mosquito", "§eClick to despawn!")))

		assertFalse(CurrentPet.summoned)
		assertEquals(CurrentPet.NO_SLOT, CurrentPet.menuSlot)
	}

	@Test
	fun `a loadout click reads the pet off the lore line the game paints grey`() {
		channels.loadout("(1/1) Loadouts", 24, lored("§7Pet: [Lvl 200] Golden Dragon"))

		assertEquals("Golden Dragon", CurrentPet.name)
	}

	@Test
	fun `a loadout click outside the loadout slots is ignored`() {
		channels.loadout("(1/1) Loadouts", 13, lored("§7Pet: [Lvl 200] Golden Dragon"))

		assertFalse(CurrentPet.summoned)
	}

	@Test
	fun `a click on a screen that is not Loadouts is ignored`() {
		channels.loadout("Pets", 24, lored("§7Pet: [Lvl 200] Golden Dragon"))

		assertFalse(CurrentPet.summoned)
	}

	@Test
	fun `an empty loadout slot leaves the pet standing`() {
		channels.loadout("(1/1) Loadouts", 24, lored("§7Pet: [Lvl 200] Golden Dragon"))

		channels.loadout("(1/1) Loadouts", 25, lored("§7Armor: None", "§7Equipment: None"))

		assertEquals("Golden Dragon", CurrentPet.name)
	}

	@Test
	fun `pet and exp sharing menus retain the pet data used by the HUD`() {
		val stored = pet("§8[Lvl 100] §6Mosquito", "§eClick to summon!", "a", "b")
		val pets = MutableList(44) { ItemStack.EMPTY }
		pets[10] = stored
		PetStorage.observe("Pets", pets)
		val sharing = MutableList(33) { ItemStack.EMPTY }
		sharing[30] = stored

		PetStorage.observe("Exp Sharing", sharing)

		val record = PetStorage.expShare(0)!!
		assertEquals(PET_UUID, record.uuid)
		assertEquals("Mosquito", record.bareName)
		assertEquals(100, record.level)
		assertTrue(PetStorage.expShareActive(0, sharingIsCaring = false))
		assertFalse(PetStorage.expShareActive(1, sharingIsCaring = false))
		assertTrue(PetStorage.expShareActive(1, sharingIsCaring = true))
	}

	@Test
	fun `a chat assertion resolves the retained pet record immediately`() {
		val pets = MutableList(44) { ItemStack.EMPTY }
		pets[10] = pet("§8[Lvl 100] §6Mosquito", "§eClick to summon!", "a", "b")
		PetStorage.observe("Pets", pets)

		channels.chatted("§aYou summoned your §6Mosquito§a!")

		assertEquals(PET_UUID, CurrentPet.uuid)
		assertEquals(100, CurrentPet.level)
		assertEquals("LEGENDARY", CurrentPet.tier)
		assertFalse(CurrentPet.stack.isEmpty)
	}

	@Test
	fun `a tab assertion uses its fresh level while retaining the stored icon`() {
		val pets = MutableList(44) { ItemStack.EMPTY }
		pets[10] = pet("§8[Lvl 100] §6Mosquito", "§eClick to summon!", "a", "b")
		PetStorage.observe("Pets", pets)

		CurrentPet.tab("§6Mosquito", 82, "LEGENDARY", 35.0, 1_000L)

		assertEquals(82, CurrentPet.level)
		assertEquals(PET_UUID, CurrentPet.uuid)
		assertFalse(CurrentPet.stack.isEmpty)
	}

	private fun pet(hoverName: String, vararg lore: String): ItemStack =
		ItemFixture.stack {
			putString("id", "PET")
			putString("uuid", ITEM_UUID)
			putString("petInfo", PET_INFO)
		}.also {
			it.set(DataComponents.CUSTOM_NAME, Component.literal(hoverName))
			it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
		}

	private fun lored(vararg lore: String): ItemStack =
		ItemFixture.stack { putString("id", "PET_LOADOUT") }
			.also { it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal))) }

	private companion object {
		const val ITEM_UUID = "11111111-1111-1111-1111-111111111111"
		const val PET_UUID = "22222222-2222-2222-2222-222222222222"
		const val PET_INFO =
			"{\"type\":\"MOSQUITO\",\"tier\":\"LEGENDARY\",\"exp\":1.0,\"candyUsed\":0,\"uniqueId\":\"$PET_UUID\"}"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
