package io.github.dzkchen.dhen.data.pet

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PetStateTest {
	private val channels = PetHooks.Channels()

	@BeforeEach
	@AfterEach
	fun reset() {
		CurrentPet.reset()
	}

	@Test
	fun `a summon line names the pet and keeps its rarity colour`() {
		channels.chatted("§aYou summoned your §6Golden Dragon§a!")

		assertTrue(CurrentPet.summoned)
		assertEquals("§6Golden Dragon", CurrentPet.name)
		assertEquals("Golden Dragon", CurrentPet.bareName)
	}

	@Test
	fun `a summon line keeps the skin marker in the name and drops it from the bare name`() {
		channels.chatted("§aYou summoned your §dRabbit§9 ✦§a!")

		assertEquals("§dRabbit§9 ✦", CurrentPet.name)
		assertEquals("Rabbit", CurrentPet.bareName)
	}

	@Test
	fun `an autopet line names the pet without its level prefix`() {
		channels.chatted("§cAutopet §eequipped your §7[Lvl 100] §6Mosquito§e! §a§lVIEW RULE")

		assertEquals("§6Mosquito", CurrentPet.name)
		assertEquals("Mosquito", CurrentPet.bareName)
	}

	@Test
	fun `an autopet line still matches when Hypixel appends a rule count`() {
		channels.chatted("§cAutopet §eequipped your §7[Lvl 100] §6Mosquito§e! §a§lVIEW RULE (2)")

		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `an alternate skin bracket survives the level strip and leaves the rarity colour`() {
		channels.chatted("§cAutopet §eequipped your §7[Lvl 200] §6[122✦] Golden Dragon§e! §a§lVIEW RULE")

		assertEquals("§6[122✦] Golden Dragon", CurrentPet.name)
		assertEquals("Golden Dragon", CurrentPet.bareName)
	}

	@Test
	fun `a despawn line clears the pet and the highlighted slot`() {
		channels.chatted("§aYou summoned your §6Mosquito§a!")
		CurrentPet.select(20)

		channels.chatted("§aYou despawned your §6Mosquito§a!")

		assertFalse(CurrentPet.summoned)
		assertEquals("", CurrentPet.bareName)
		assertEquals(CurrentPet.NO_SLOT, CurrentPet.menuSlot)
	}

	@Test
	fun `an unrelated chat line leaves the pet alone`() {
		channels.chatted("§aYou summoned your §6Mosquito§a!")

		channels.chatted("§ePlayer §asummoned your friend!")

		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `a summon capture wins over an autopet capture on the same line`() {
		channels.chatted(
			"§aYou summoned your §6Mosquito§a!" +
				"§cAutopet §eequipped your §7[Lvl 100] §5Rabbit§e! §a§lVIEW RULE"
		)

		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `an empty capture never blanks a known pet`() {
		channels.chatted("§aYou summoned your §6Mosquito§a!")

		channels.chatted("§aYou summoned your §a!")

		assertEquals("§6Mosquito", CurrentPet.name)
	}

	@Test
	fun `the Pets menu title tolerates a page prefix a page suffix and a search`() {
		assertTrue(PetLines.petsMenu("Pets"))
		assertTrue(PetLines.petsMenu("(1/3) Pets"))
		assertTrue(PetLines.petsMenu("Pets (2/5)"))
		assertTrue(PetLines.petsMenu("Pets: \"drag\""))
		assertFalse(PetLines.petsMenu("Pets Loadouts"))
		assertFalse(PetLines.petsMenu("Your Equipment"))
	}

	@Test
	fun `the level prefix comes off a hover name without eating an alternate skin bracket`() {
		assertEquals("§6Golden Dragon", PetLines.withoutLevel("§8[Lvl 200] §6Golden Dragon"))
		assertEquals("§dEndermite§5 ✦", PetLines.withoutLevel("§8[Lvl 100] §dEndermite§5 ✦"))
		assertEquals("§6[122✦] Golden Dragon", PetLines.withoutLevel("§8[Lvl 200] §6[122✦] Golden Dragon"))
	}

	@Test
	fun `a loadout lore line yields the pet after its level`() {
		assertEquals("Golden Dragon", PetLines.loadoutPet("§7Pet: [Lvl 200] Golden Dragon"))
		assertNull(PetLines.loadoutPet("§7Pet: None"))
	}

	@Test
	fun `selecting and deselecting a menu slot leaves the pet name standing`() {
		channels.chatted("§aYou summoned your §6Mosquito§a!")

		CurrentPet.select(31)
		assertEquals(31, CurrentPet.menuSlot)

		CurrentPet.deselect()
		assertEquals(CurrentPet.NO_SLOT, CurrentPet.menuSlot)
		assertEquals("§6Mosquito", CurrentPet.name)
	}
}
