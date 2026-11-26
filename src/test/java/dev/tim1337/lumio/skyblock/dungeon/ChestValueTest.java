package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** Recorded F7 reward chests valued with fixed prices: contents, cost, profit, essence, books, items without a price. */
class ChestValueTest {
	private static final Map<String, String> IDS = Map.of("Necron's Handle", "NECRON_HANDLE", "Fuming Potato Book", "FUMING_POTATO_BOOK");
	private static final Map<String, Double> PRICES = Map.of("NECRON_HANDLE", 4_620_000.0, "ESSENCE_WITHER", 6_550.0, "ENCHANTMENT_ULTIMATE_SOUL_EATER_1",
		212_000.0, "FUMING_POTATO_BOOK", 1_030_000.0, "DUNGEON_CHEST_KEY", 1_200_000.0);

	private static ChestValue.Chest chest(String kind, List<String> contents, List<String> cost) {
		return ChestValue.of(kind, contents, cost, IDS::get, id -> PRICES.getOrDefault(id, 0.0), PRICES.get("DUNGEON_CHEST_KEY"));
	}

	@Test
	void anObsidianChest() {
		ChestValue.Chest obsidian = chest("Obsidian", List.of("Necron's Handle", "Wither Essence x18", "Enchanted Book (Soul Eater I)", "Fuming Potato Book"),
			List.of("500,000 Coins"));
		assertEquals(4, obsidian.items().size());
		assertEquals("ESSENCE_WITHER", obsidian.items().get(1).id());
		assertEquals(18, obsidian.items().get(1).count());
		assertEquals(117_900, obsidian.items().get(1).value(), 0.01);
		assertEquals("ENCHANTMENT_ULTIMATE_SOUL_EATER_1", obsidian.items().get(2).id());
		assertEquals(4_620_000 + 117_900 + 212_000 + 1_030_000, obsidian.contents(), 0.01);
		assertEquals(500_000, obsidian.cost(), 0.01);
		assertEquals(5_479_900, obsidian.profit(), 0.01);
		assertFalse(obsidian.key());
	}

	@Test
	void aBedrockChestWithAKeyAndAnItemWithoutAPrice() {
		ChestValue.Chest bedrock = chest("Bedrock", List.of("Mystery Shard x2", "Wither Essence x5"), List.of("2,000,000 Coins", "Dungeon Chest Key"));
		ChestValue.Item shard = bedrock.items().getFirst();
		assertNull(shard.id());
		assertFalse(shard.priced());
		assertEquals(0, shard.value());
		assertTrue(bedrock.key());
		assertEquals(3_200_000, bedrock.cost(), 0.01);
		assertTrue(bedrock.profit() < 0);
	}

	@Test
	void aFreeWoodChest() {
		ChestValue.Chest wood = chest("Wood", List.of("Wither Essence x2"), List.of("FREE"));
		assertEquals(0, wood.cost());
		assertEquals(13_100, wood.profit(), 0.01);
	}

	@Test
	void titlesAndCoins() {
		assertEquals("Obsidian", ChestValue.kindOf("Obsidian Chest"));
		assertNull(ChestValue.kindOf("Large Chest"));
		assertEquals("+5.48M", ChestValue.coins(5_479_900));
		assertEquals("−80.0k", ChestValue.coins(-80_000));
		assertEquals("+390k", ChestValue.coins(390_000));
	}

	/** Hypixel titles a reward chest's menu "Obsidian Chest" or only "Obsidian"; both are the chest, nothing else is. */
	@Test
	void chestTitles() {
		for (String title : new String[] {"Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest"}) {
			org.junit.jupiter.api.Assertions.assertEquals(title.replace(" Chest", ""), ChestValue.kindOf(title), title);
			org.junit.jupiter.api.Assertions.assertTrue(ChestValue.namedChest(title));
		}
		for (String title : new String[] {"Wood", "Gold", "Obsidian", "Bedrock"}) {
			org.junit.jupiter.api.Assertions.assertEquals(title, ChestValue.kindOf(title), title);
			org.junit.jupiter.api.Assertions.assertFalse(ChestValue.namedChest(title));
		}
		for (String title : new String[] {"Large Chest", "Chest", "Golden Chest", "Croesus", "Catacombs - Floor VII", "Wood Chest Chest"}) {
			org.junit.jupiter.api.Assertions.assertNull(ChestValue.kindOf(title), title);
		}
	}
}
