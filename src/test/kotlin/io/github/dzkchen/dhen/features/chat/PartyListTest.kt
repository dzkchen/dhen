package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.data.party.PartyChat
import io.github.dzkchen.dhen.data.party.PartyRole
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PartyListTest {
	@Test
	fun `each party line maps to the role it announces`() {
		assertEquals(PartyRole.LEADER, PartyChat.listRole("Party Leader: [MVP+] Alice ● "))
		assertEquals(PartyRole.MOD, PartyChat.listRole("Party Moderators: Bob ● "))
		assertEquals(PartyRole.MEMBER, PartyChat.listRole("Party Members: Carol ● "))
		assertNull(PartyChat.listRole("Party Members (3)"))
		assertNull(PartyChat.listRole("You are not currently in a party."))
	}

	@Test
	fun `the count header matches only the bracketed total`() {
		assertTrue(PARTY_LIST_HEADER.matches("Party Members (3)"))
		assertFalse(PARTY_LIST_HEADER.matches("Party Members: Carol ● "))
		assertFalse(PARTY_LIST_HEADER.matches("Party Members (three)"))
	}

	@Test
	fun `a ranked leader keeps its coloured prefix out of the name`() {
		val rows = read("§9Party Leader: §b[MVP§d+§b] Alice §a● ", PartyRole.LEADER)

		assertEquals(1, rows.size)
		assertEquals("Alice", rows[0].name)
		assertEquals("§b[MVP§d+§b] ", rows[0].rank)
		assertEquals(PartyRole.LEADER, rows[0].role)
		assertTrue(rows[0].online)
	}

	@Test
	fun `an unranked name never swallows the colour code in front of it`() {
		val rows = read("§9Party Members: §7Bob §c● §b[MVP§b] Carol §a● ", PartyRole.MEMBER)

		assertEquals(listOf("Bob", "Carol"), rows.map { it.name })
		assertEquals(listOf("§7", "§b[MVP§b] "), rows.map { it.rank })
		assertEquals(listOf(false, true), rows.map { it.online })
	}

	@Test
	fun `reset codes between the name and its dot do not break the read`() {
		val rows = read("§9Party Members: §r§7Bob§r §c●§r §r§fCarol§r §a●§r ", PartyRole.MEMBER)

		assertEquals(listOf("Bob", "Carol"), rows.map { it.name })
		assertEquals(listOf(false, true), rows.map { it.online })
	}

	@Test
	fun `the space between two entries never survives into the second rank`() {
		val rows = read("§9Party Members: §r§7Bob§r §c●§r §r§fCarol§r §a●§r ", PartyRole.MEMBER)

		assertEquals(listOf("§7", "§f"), rows.map { it.rank })
		assertTrue(rows.none { it.rank.contains(' ') })
	}

	@Test
	fun `a name that starts with a digit survives the colour code before it`() {
		val rows = read("§9Party Members: §77Bob §a● ", PartyRole.MEMBER)

		assertEquals(listOf("7Bob"), rows.map { it.name })
		assertEquals(listOf("§7"), rows.map { it.rank })
	}

	@Test
	fun `an empty party line adds nobody`() {
		assertTrue(read("§9Party Members: ", PartyRole.MEMBER).isEmpty())
	}

	@Test
	fun `rows print leader first with nothing to click for a plain member`() {
		val printed = partyListComponent(
			listOf(
				row("Bob", PartyRole.MEMBER, online = false),
				row("Alice", PartyRole.LEADER, online = true)
			),
			leading = false,
			self = "Bob"
		)

		assertEquals(
			"§9§m----------------------------------\n" +
				"  §aParty Members (2)\n" +
				"\n  §a● §r§b[MVP] Alice §e(Leader)" +
				"\n  §c● §r§b[MVP] Bob" +
				"\n§9§m----------------------------------",
			printed.string
		)
		assertTrue(commands(printed).isEmpty())
	}

	@Test
	fun `the leader gets party buttons and per-row buttons on everyone but itself`() {
		val printed = partyListComponent(
			listOf(
				row("Alice", PartyRole.LEADER, online = true),
				row("Bob", PartyRole.MEMBER, online = true)
			),
			leading = true,
			self = "Alice"
		)

		assertEquals(
			listOf("/p warp", "/p settings allinvite", "/p disband", "/p kick Bob", "/p transfer Bob"),
			commands(printed)
		)
		assertTrue(printed.string.contains("§9[Warp] §e[Invite] §4[Disband]"))
	}

	@Test
	fun `the disband button warns before it runs`() {
		val printed = partyListComponent(listOf(row("Alice", PartyRole.LEADER, online = true)), true, "Alice")
		val disband = printed.siblings.single {
			(it.style.clickEvent as? ClickEvent.RunCommand)?.command == "/p disband"
		}

		assertEquals("§c§lBE CAREFUL", (disband.style.hoverEvent as HoverEvent.ShowText).value().string)
	}

	private fun read(styled: String, role: PartyRole): List<PartyListRow> =
		mutableListOf<PartyListRow>().also { readPartyRows(styled, role, it) }

	private fun row(name: String, role: PartyRole, online: Boolean) =
		PartyListRow("§b[MVP] ", name, role, online)

	private fun commands(printed: Component): List<String> =
		mutableListOf<String>().also { collectCommands(printed, it) }

	private fun collectCommands(component: Component, into: MutableList<String>) {
		(component.style.clickEvent as? ClickEvent.RunCommand)?.let { into += it.command }
		for (sibling in component.siblings) collectCommands(sibling, into)
	}
}
