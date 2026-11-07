package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ids a drop's price is looked up by: a pet by its type and rarity (its name's color), as the auction data keys it ("ENDERMAN;4"), a book by
 * its bazaar and its auction id, an item with a level by its name and by its base with the level.
 */
class DropPriceIdsTest {
	private static final Function<String, String> NO_NAMES = name -> null;
	/** The rarities' name colors as the server writes them: common white to mythic pink. */
	private static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF};

	private static List<String> ids(String message, int color) {
		return DropPriceIds.ids(DropMessage.parse(message), color, NO_NAMES);
	}

	@Test
	void aPetOfEachRarity() {
		for (int rarity = 0; rarity < COLORS.length; rarity++) {
			assertEquals(List.of("ENDERMAN;" + rarity), ids("§5§lPET DROP! §r§6Enderman §b(+200% ✯ Magic Find)", COLORS[rarity]), "rarity " + rarity);
		}
		assertEquals(List.of("BABY_YETI;4"), ids("PET DROP! Baby Yeti (+200% ✯ Magic Find)", 0xFFFFAA00));
		assertEquals(List.of("GOLDEN_DRAGON;5"), ids("PET DROP! [Lvl 1] Golden Dragon", 0xFFFF55FF), "the level in front is not part of the type");
		assertEquals(List.of("PHOENIX;3"), ids("RARE DROP! [Lvl 1] Phoenix", 0xFFAA00AA), "a pet announced as a rare drop");
	}

	@Test
	void aPetWithoutItsRarityHasNoPrice() {
		assertTrue(ids("PET DROP! Enderman", 0xFF123456).isEmpty(), "never another rarity's price");
	}

	@Test
	void aBookByItsBazaarAndAuctionId() {
		assertEquals(List.of("ENCHANTMENT_ULTIMATE_WISE_5", "ULTIMATE_WISE;5"), ids("CRAZY RARE DROP! Enchanted Book (Ultimate Wise V)", 0xFFFFFFFF));
		assertEquals(List.of("ENCHANTMENT_MANA_STEAL_1", "MANA_STEAL;1"), ids("VERY RARE DROP! (Mana Steal I) (+437% ✯ Magic Find)", 0xFF5555FF));
	}

	@Test
	void anItemWithALevel() {
		assertEquals(List.of("SNOW_RUNE;3"), ids("RARE DROP! Snow Rune III", 0xFF5555FF));
		Function<String, String> known = name -> Map.of("Judgement Core", "JUDGEMENT_CORE").get(name);
		assertEquals(List.of("JUDGEMENT_CORE"), DropPriceIds.ids(DropMessage.parse("CRAZY RARE DROP! Judgement Core"), 0xFFFFAA00, known));
		assertTrue(ids("RARE DROP! Something Nobody Knows", 0xFF5555FF).isEmpty());
	}
}
