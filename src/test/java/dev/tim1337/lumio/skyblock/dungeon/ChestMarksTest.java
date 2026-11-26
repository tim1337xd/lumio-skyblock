package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The chest menus besides their rewards: books by their enchantment, buttons left out, the opened chest and the Kismet Feather, the best still open. */
class ChestMarksTest {
	private static final Map<String, Double> PRICES = Map.of("ENCHANTMENT_WISDOM_5", 1_200_000.0, "ENCHANTMENT_ULTIMATE_WISE_2", 300_000.0,
		"ESSENCE_WITHER", 2_000.0);

	@Test
	void aBookIsNamedAndPricedByItsEnchantment() {
		String name = ChestMarks.bookName("Enchanted Book", List.of("Wisdom V", "", "Gain more Combat Wisdom"));
		assertEquals("Enchanted Book (Wisdom V)", name);
		assertEquals("Wisdom V", ChestMarks.shownName(name));
		assertEquals("Ultimate Wise II", ChestMarks.shownName(ChestMarks.bookName("Enchanted Book", List.of("Ultimate Wise II"))));
		ChestValue.Chest chest = ChestValue.of("Obsidian", List.of(name, "Enchanted Book (Ultimate Wise II)", "Wither Essence x10"),
			List.of("500,000 Coins"), ignored -> null, id -> PRICES.getOrDefault(id, 0.0), 0);
		assertTrue(chest.items().stream().allMatch(ChestValue.Item::priced));
		assertEquals(1_200_000 + 300_000 + 20_000, chest.contents());
		assertEquals(chest.items().stream().mapToDouble(ChestValue.Item::value).sum(), chest.contents(), "Contents is the sum of the rows");
	}

	@Test
	void buttonsAreNoItems() {
		assertTrue(ChestMarks.isButton("Reroll Chest", List.of("Reroll the items of this chest", "", "You already rerolled a chest!")));
		assertTrue(ChestMarks.isButton("Open Reward Chest", List.of()));
		assertTrue(ChestMarks.isButton("Go Back", List.of("To Croesus")));
		assertTrue(ChestMarks.isButton("Some Button", List.of("Click to do something")));
		assertFalse(ChestMarks.isButton("Necron's Handle", List.of("A piece of Necron", "LEGENDARY")));
	}

	@Test
	void openedAndKismetFromTheGame() {
		assertEquals("Obsidian", ChestMarks.openedKind(List.of("Floor VII", "", "Opened Chest: Obsidian")));
		assertNull(ChestMarks.openedKind(List.of("Floor VII", "No chests opened yet!")));
		assertTrue(ChestMarks.rerolled(List.of("Reroll the items", "", "You already rerolled a chest!")));
		assertFalse(ChestMarks.rerolled(List.of("Reroll the items", "", "Click to reroll!")));
	}

	@Test
	void onlyUnopenedChestsAreGreen() {
		Set<String> opened = Set.of("Obsidian");
		for (String kind : List.of("Wood", "Gold", "Diamond", "Emerald", "Bedrock")) {
			assertTrue(ChestMarks.unopened(kind, opened), kind + " not opened yet: boxed in green");
		}
		assertFalse(ChestMarks.unopened("Obsidian", opened), "opened: no green");
	}

	@Test
	void noBestChestLineAnywhere() throws java.io.IOException {
		assertFalse(dev.tim1337.lumio.api.TooltipLineProvider.class.isAssignableFrom(DungeonChestsFeature.class), "no tooltip lines of its own");
		String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/tim1337/lumio/skyblock/dungeon/DungeonChestsFeature.java"));
		assertFalse(source.contains("\"Best chest"), "no best-chest line in the overlay or in Croesus");
	}

	@Test
	void theBorderOfARunsChestFollowsTheProfit() {
		// every chest is unopened at first: green for all of them said nothing
		assertEquals(ChestMarks.Verdict.PROFIT, ChestMarks.verdict(false, 250_000, 4, 0));
		assertEquals(ChestMarks.Verdict.PROFIT, ChestMarks.verdict(false, 0, 4, 0));
		assertEquals(ChestMarks.Verdict.LOSS, ChestMarks.verdict(false, -1_200_000, 4, 0));
	}

	@Test
	void withoutAPriceTheBorderIsGrey() {
		// nothing in it has a price
		assertEquals(ChestMarks.Verdict.UNKNOWN, ChestMarks.verdict(false, -500_000, 3, 3));
		// a loss with an item without a price may be none
		assertEquals(ChestMarks.Verdict.UNKNOWN, ChestMarks.verdict(false, -500_000, 3, 1));
		// a profit stays one: the item without a price can only add to it
		assertEquals(ChestMarks.Verdict.PROFIT, ChestMarks.verdict(false, 500_000, 3, 1));
	}

	@Test
	void anOpenedChestHasNoColoredBorder() {
		assertEquals(ChestMarks.Verdict.OPENED, ChestMarks.verdict(true, 250_000, 4, 0));
		assertEquals(ChestMarks.Verdict.OPENED, ChestMarks.verdict(true, -250_000, 4, 4));
	}
}
