package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The reward chests' enchanted books: priced by the enchantment in their SkyBlock data (never by the id every book has), shown as "Ultimate Wise
 * I" in the game's colors, also when Croesus lists only the enchantment; a book the markets do not know stays without a price.
 */
class ChestBooksTest {
	private static final Map<String, Double> PRICES = Map.of("ENCHANTMENT_ULTIMATE_WISE_1", 310_000.0, "ENCHANTMENT_FEATHER_FALLING_6", 95_000.0,
		"ENCHANTMENT_ULTIMATE_WISDOM_1", 220_000.0);

	/** A book's custom data as the server sends it: its SkyBlock id and its one enchantment. */
	private static CompoundTag book(String enchantment, int level) {
		CompoundTag data = new CompoundTag();
		data.putString("id", "ENCHANTED_BOOK");
		CompoundTag enchantments = new CompoundTag();
		enchantments.putInt(enchantment, level);
		data.put("enchantments", enchantments);
		return data;
	}

	@Test
	void theIdFromTheBooksData() {
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_1", ChestBooks.idFromData(book("ultimate_wise", 1)));
		assertEquals("ENCHANTMENT_FEATHER_FALLING_6", ChestBooks.idFromData(book("feather_falling", 6)));
		CompoundTag two = book("smite", 6);
		two.getCompound("enchantments").orElseThrow().putInt("looting", 4);
		assertNull(ChestBooks.idFromData(two), "a book of two enchantments is no single bazaar product");
		assertNull(ChestBooks.idFromData(new CompoundTag()));
	}

	@Test
	void pricedByTheEnchantmentNotTheBook() {
		// inside a chest the book's id is learned for its name: the enchantment's, never ENCHANTED_BOOK
		Map<String, String> learned = Map.of("Enchanted Book (Ultimate Wise I)", ChestBooks.idFromData(book("ultimate_wise", 1)),
			"Enchanted Book (Feather Falling VI)", ChestBooks.idFromData(book("feather_falling", 6)), "Enchanted Book (Rejuvenate I)",
			ChestBooks.idFromData(book("rejuvenate", 1)));
		ChestValue.Chest chest = ChestValue.of("Obsidian", List.of("Enchanted Book (Ultimate Wise I)", "Enchanted Book (Feather Falling VI)",
			"Enchanted Book (Rejuvenate I)"), List.of(), learned::get, id -> PRICES.getOrDefault(id, 0.0), 0);
		assertEquals(310_000.0, chest.items().get(0).value());
		assertEquals(95_000.0, chest.items().get(1).value());
		assertFalse(chest.items().get(2).priced(), "not on the bazaar nor the auction house: no price");
		assertEquals("ENCHANTMENT_REJUVENATE_1", chest.items().get(2).id());
	}

	@Test
	void theBooksOwnIdNeverPricesIt() {
		Map<String, String> wrong = Map.of("Enchanted Book (Ultimate Wise I)", "ENCHANTED_BOOK");
		ChestValue.Chest chest = ChestValue.of("Obsidian", List.of("Enchanted Book (Ultimate Wise I)"), List.of(), wrong::get,
			id -> PRICES.getOrDefault(id, 0.0), 0);
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_1", chest.items().getFirst().id());
		assertTrue(chest.items().getFirst().priced());
	}

	@Test
	void croesusListsOnlyTheEnchantment() {
		ChestValue.Chest chest = ChestValue.of("Bedrock", List.of("Ultimate Wise I", "Wisdom I"), List.of(), name -> null,
			id -> PRICES.getOrDefault(id, 0.0), 0);
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_1", chest.items().get(0).id());
		assertEquals("ENCHANTMENT_ULTIMATE_WISDOM_1", chest.items().get(1).id(), "Wisdom is an ultimate enchantment: the one the market knows");
	}

	@Test
	void shownAndColoredAsTheGameDoes() {
		assertEquals("Ultimate Wise I", ChestMarks.shownName("Enchanted Book (Ultimate Wise I)"));
		assertEquals("Feather Falling VI", ChestBooks.display("ENCHANTMENT_FEATHER_FALLING_6"));
		assertEquals(ChestBooks.ULTIMATE_RGB, ChestBooks.color("ENCHANTMENT_ULTIMATE_WISE_1"));
		assertEquals(ChestBooks.NORMAL_RGB, ChestBooks.color("ENCHANTMENT_FEATHER_FALLING_6"));
	}
}
