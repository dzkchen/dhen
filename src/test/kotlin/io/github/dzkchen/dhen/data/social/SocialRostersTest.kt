package io.github.dzkchen.dhen.data.social

import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SocialRostersTest {
	@BeforeEach
	@AfterEach
	fun clear() = SocialRosters.forget()

	@Test
	fun `a guild list reads every ranked member out of the dotted rows`() {
		guildList()

		assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), SocialRosters.guild)
	}

	@Test
	fun `an offline members line throws the page away because it is not the full list`() {
		feed("§b§m-----------------------------------------------------")
		feed("§6Guild Name: Testers")
		feed("§e-- Guild Master --")
		feed("§a● §a[MVP+] Alpha")
		feed("§eOffline Members: 12")
		feed("§b§m-----------------------------------------------------")

		assertEquals(emptyList<String>(), SocialRosters.guild)
	}

	@Test
	fun `a second guild list replaces the first rather than adding to it`() {
		guildList()
		feed("§6Guild Name: Testers")
		feed("§a● §aEcho")
		feed("§b§m-----------------------------------------------------")

		assertEquals(listOf("Echo"), SocialRosters.guild)
	}

	@Test
	fun `a dotted line outside a guild list adds nobody`() {
		feed("§9Party Members: §r§a● §r§aAlpha §r§a● §r§aBravo")

		assertEquals(emptyList<String>(), SocialRosters.guild)
	}

	@Test
	fun `a friends page reads each name off its profile link`() {
		SocialRosters.read("Friends (Page 1 of 2)", friendsPage())

		assertEquals(listOf("Alpha", "Bravo"), SocialRosters.friends)
	}

	@Test
	fun `a page without the friends heading is left alone even when it links profiles`() {
		SocialRosters.read("Guild Members (Page 1 of 2)", friendsPage())

		assertEquals(emptyList<String>(), SocialRosters.friends)
	}

	@Test
	fun `a new friend is added and a removed one drops out`() {
		SocialRosters.read("Friends (Page 1 of 2)", friendsPage())
		feed("§aYou are now friends with §r§b[MVP§r§d+§r§b] Charlie")
		feed("§r§eYou removed §r§b[MVP§r§c+§r§b] Alpha§r§e from your friends list!§r§9§m")

		assertEquals(listOf("Bravo", "Charlie"), SocialRosters.friends)
	}

	private fun guildList() {
		feed("§b§m-----------------------------------------------------")
		feed("§6Guild Name: Testers")
		feed("§e-- Guild Master --")
		feed("§a● §a[MVP+] Alpha")
		feed("§e-- Member --")
		feed("§a● §aBravo §c● §7[VIP] Charlie §c● §7Delta")
		feed("§b§m-----------------------------------------------------")
	}

	private fun friendsPage(): Component = Component.empty()
		.append(Component.literal("Friends (Page 1 of 2)"))
		.append(profileLink("Alpha"))
		.append(Component.empty().append(profileLink("Bravo")))

	private fun profileLink(name: String): Component = Component.literal(name).setStyle(
		Style.EMPTY
			.withClickEvent(ClickEvent.RunCommand("/viewprofile 503450fc-72c2-4e87-8243-94e264977437"))
			.withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick here to view §b$name§e's profile")))
	)

	private fun feed(styled: String) = SocialRosters.read(withoutCodes(styled), Component.literal(styled))
}
