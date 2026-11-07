package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.TabCard;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Sections that list names become cards with one row per name, with the status as the server sends it. */
class TabNameSectionsTest {
	private static Component bold(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withBold(true));
	}

	private static List<String> rows(TabCard card) {
		List<String> rows = new ArrayList<>();
		for (TabCard.Row row : card.rows()) {
			rows.add(row.value() == null ? row.label().getString() : row.label().getString() + " | " + row.value().getString());
		}
		return rows;
	}

	@Test
	void guestsWithABoldHeading() {
		List<TabCard> cards = TabInfoCards.read(List.of(bold("Guests (1)"), Component.literal(" [302] Steve123 Online"), Component.empty()), false);
		assertEquals(1, cards.size());
		assertEquals("Guests (1)", cards.getFirst().title());
		assertEquals(List.of("[302] Steve123 Online"), rows(cards.getFirst()));
	}

	@Test
	void aHeadingThatIsNotBoldIsOneToo() {
		List<TabCard> cards = TabInfoCards.read(List.of(Component.literal("Guests (2)"), Component.literal("[302] Steve123"), Component.literal("[18] Bob"), Component.empty()), false);
		assertEquals("Guests (2)", cards.getFirst().title());
		assertEquals(List.of("[302] Steve123", "[18] Bob"), rows(cards.getFirst()));
	}

	@Test
	void coopWithOnlineAndOfflineMembers() {
		List<TabCard> cards = TabInfoCards.read(List.of(bold("Coop (3)"), Component.literal(" [302] Steve123: Online"), Component.literal(" [18] Bob: Last online 2d ago"), Component.literal(" Eve Offline"), Component.empty()), false);
		assertEquals("Coop (3)", cards.getFirst().title());
		assertEquals(List.of("[302] Steve123 | Online", "[18] Bob | Last online 2d ago", "Eve Offline"), rows(cards.getFirst()));
	}

	@Test
	void aNameKeepsItsRankColors() {
		Component line = Component.literal(" [MVP+] ").withStyle(Style.EMPTY.withColor(0x55FFFF)).append(Component.literal("Steve123").withStyle(Style.EMPTY.withColor(0x55FFFF)));
		TabCard.Row row = TabInfoCards.read(List.of(bold("Party (1)"), line), false).getFirst().rows().getFirst();
		assertEquals("[MVP+] Steve123", row.label().getString());
		assertNull(row.value());
		boolean[] colored = {false};
		row.label().visit((style, part) -> {
			colored[0] |= style.getColor() != null;
			return java.util.Optional.empty();
		}, Style.EMPTY);
		assertTrue(colored[0], "the server's colors stay");
	}

	@Test
	void anEmptySectionMakesNoCard() {
		assertTrue(TabInfoCards.read(List.of(bold("Guests (0)"), Component.empty()), false).isEmpty());
	}

	@Test
	void theNextSectionStartsItsOwnCard() {
		List<TabCard> cards = TabInfoCards.read(List.of(bold("Guests (1)"), Component.literal(" [9] Bob"), bold("Area:"), Component.literal(" Island: Hub")), false);
		assertEquals(2, cards.size());
		assertEquals("Area", cards.get(1).title());
	}
}
