package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.TabCard;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The fixed places of the tab list's cards by kind, and that no card is without a title. */
class TabCardOrderTest {
	private static TabCard card(String title, String... lines) {
		List<TabCard.Row> rows = new java.util.ArrayList<>();
		for (String line : lines) {
			rows.add(new TabCard.Row(Component.literal(line), null, -1f));
		}
		return new TabCard(title, rows);
	}

	@Test
	void theKnownCardsHaveTheirFixedOrder() {
		String[] order = {"Area", "Profile", "Pet", "Dungeons", "Essence", "RNG Meter", "Party"};
		int before = -1;
		for (String title : order) {
			int slot = TabCardOrder.slot(title);
			assertTrue(slot > before, title + " comes after the one before it");
			before = slot;
		}
		assertEquals(0, TabCardOrder.slot("Area"));
		// the title's case and a count in it make no other card
		assertEquals(TabCardOrder.slot("Party"), TabCardOrder.slot("Party (3)"));
		assertEquals(TabCardOrder.slot("RNG Meter"), TabCardOrder.slot("rng meter"));
	}

	@Test
	void aCardsPlaceDoesNotDependOnTheOtherCards() {
		// Party alone, or after six other cards: the same place, so the same column
		List<TabCard> alone = TabCardOrder.placed(List.of(card("Party", "Nobody")), false);
		List<TabCard> among = TabCardOrder.placed(List.of(card("Area", "Hub"), card("Profile", "Apple"), card("Pet", "Bee"), card("Dungeons", "x"),
			card("Essence", "y"), card("RNG Meter", "z"), card("Party", "Nobody")), false);
		assertEquals(alone.getFirst().slot(), among.getLast().slot());
		assertEquals(TabCardOrder.slot("Party"), alone.getFirst().slot());
	}

	@Test
	void unknownCardsHaveNoPlaceOfTheirOwn() {
		List<TabCard> placed = TabCardOrder.placed(List.of(card("Mystery Section", "a line"), card("Area", "Hub")), false);
		// the list gives it one after the known cards, in the order first seen
		assertEquals(-1, placed.getFirst().slot());
		assertEquals("Mystery Section", placed.getFirst().title());
	}

	@Test
	void noCardIsWithoutATitle() {
		// a line under no heading (the stray "9jcuiw4ny4" was one): into the Other card at the end, as it was sent
		List<TabCard> placed = TabCardOrder.placed(List.of(card("", "9jcuiw4ny4"), card("Area", "Dungeon Hub"), card(null, "another stray")), false);
		for (TabCard card : placed) {
			assertFalse(card.title() == null || card.title().isBlank());
		}
		TabCard other = placed.getLast();
		assertEquals(TabStats.OTHER, other.title());
		assertEquals(TabCardOrder.OTHER_SLOT, other.slot());
		assertEquals(2, other.rows().size());
		assertEquals("9jcuiw4ny4", other.rows().getFirst().label().getString());
	}

	@Test
	void thereIsOnlyOneOtherCardAndItStandsLast() {
		// the Stats card's rows that are no stats are in an Other card already: the stray lines join it
		List<TabCard> placed = TabCardOrder.placed(List.of(card("Other", "Goblin: 3"), card("", "stray"), card("Skills", "Mining 60")), false);
		assertEquals(2, placed.size());
		assertEquals("Skills", placed.getFirst().title());
		assertEquals(TabStats.OTHER, placed.getLast().title());
		assertEquals(2, placed.getLast().rows().size());
		assertTrue(TabCardOrder.OTHER_SLOT > TabCardOrder.slot("Skills"));
		// without stray lines and without an Other card there is none
		assertEquals(1, TabCardOrder.placed(List.of(card("Area", "Hub")), false).size());
	}
}
