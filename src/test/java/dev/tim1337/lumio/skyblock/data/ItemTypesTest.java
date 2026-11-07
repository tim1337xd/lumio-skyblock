package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The item search reads rarity and type from an item's last lore line, and groups it for the filter chips. */
class ItemTypesTest {
	@Test
	void weapons() {
		ItemTypes.Info info = ItemTypes.parse("LEGENDARY DUNGEON SWORD", "HYPERION");
		assertEquals("LEGENDARY", info.rarity());
		assertEquals("DUNGEON SWORD", info.type());
		assertEquals(ItemTypes.Category.WEAPONS, info.category());
		assertEquals(ItemTypes.Category.WEAPONS, ItemTypes.parse("EPIC BOW", "X").category());
	}

	@Test
	void twoWordRarities() {
		ItemTypes.Info info = ItemTypes.parse("VERY SPECIAL HATCESSORY", "X");
		assertEquals("VERY SPECIAL", info.rarity());
		assertEquals(ItemTypes.Category.ACCESSORIES, info.category());
		assertEquals("UNCOMMON", ItemTypes.parse("UNCOMMON", "X").rarity());
	}

	@Test
	void recombobulatedMarks() {
		ItemTypes.Info info = ItemTypes.parse("a MYTHIC ACCESSORY a", "X");
		assertEquals("MYTHIC", info.rarity());
		assertEquals(ItemTypes.Category.ACCESSORIES, info.category());
	}

	@Test
	void groups() {
		assertEquals(ItemTypes.Category.ARMOR, ItemTypes.parse("RARE HELMET", "X").category());
		assertEquals(ItemTypes.Category.ARMOR, ItemTypes.parse("EPIC DUNGEON BOOTS", "X").category());
		assertEquals(ItemTypes.Category.TOOLS, ItemTypes.parse("EPIC DRILL", "X").category());
		assertEquals(ItemTypes.Category.TOOLS, ItemTypes.parse("RARE FISHING ROD", "X").category());
		assertEquals(ItemTypes.Category.PETS, ItemTypes.parse("LEGENDARY", "BEE;4").category());
		assertEquals(ItemTypes.Category.MATERIALS, ItemTypes.parse("RARE", "ENCHANTED_DIAMOND").category());
	}

	@Test
	void noRarityLine() {
		ItemTypes.Info info = ItemTypes.parse("Right-click to use", "X");
		assertEquals(-1, info.rank());
		assertEquals(ItemTypes.Category.MATERIALS, info.category());
		assertEquals("", info.label());
	}

	@Test
	void ranksSortRarities() {
		assertEquals(true, ItemTypes.parse("MYTHIC SWORD", "X").rank() > ItemTypes.parse("LEGENDARY SWORD", "X").rank());
	}
}
