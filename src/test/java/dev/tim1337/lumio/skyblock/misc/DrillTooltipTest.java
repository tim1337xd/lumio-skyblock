package dev.tim1337.lumio.skyblock.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.EnchantTiers;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** A Glacial Divan's Drill's tooltip, from a player's screenshot, and others with a glint: the game's line the lore repeats, and every enchant's tier, with the levels as the item repository has them. */
class DrillTooltipTest {
	private static final List<String> DRILL = List.of("Glacial Divan's Drill", "Efficiency X", "Breaking Power 10", "", "Damage: +75", "Mining Speed: +3,190 [+500] [+50] (+240)", "", "Flowstate III", "Compact X", "Efficiency X", "Experience V", "Fortune IV",
		"Lapidary V", "Paleontologist V", "Prismatic V", "Smelting Touch I", "", "Perfectly-Cut Fuel Tank");

	private static EnchantTiers tiers() {
		EnchantTiers tiers = EnchantTiers.of(JsonParser.parseString("""
			{"enchants_xp_cost":{"compact":[1],"efficiency":[1,2,3,4,5],"experience":[1,2,3,4],"fortune":[1,2,3],"lapidary":[1,2,3,4,5],"paleontologist":[1,2,3,4,5],"smelting_touch":[1]},
			"max_xp_table_levels":{"efficiency":5,"experience":3,"fortune":3,"smelting_touch":1},"enchants":{"PICKAXE":["ultimate_flowstate"]}}""").getAsJsonObject());
		tiers.addBooks(Map.of("compact", 1, "efficiency", 5, "experience", 5, "fortune", 4, "lapidary", 5, "paleontologist", 5, "smelting_touch", 1));
		return tiers;
	}

	@Test
	void theGamesLineTheLoreRepeatsGoes() {
		assertEquals(List.of(1), GameEnchantLines.repeated(DRILL, List.of("Efficiency X")));
	}

	@Test
	void aWeaponWithTwoAndALineOfSeveral() {
		List<String> lines = List.of("Withered Hyperion", "Sharpness VII", "Smite VII", "Damage: +260", "", "Sharpness VII, Smite VII");
		assertEquals(List.of(2, 1), GameEnchantLines.repeated(lines, List.of("Sharpness VII", "Smite VII")));
	}

	@Test
	void aBookAndArmor() {
		assertEquals(List.of(1), GameEnchantLines.repeated(List.of("Enchanted Book", "Sharpness VII", "Sharpness VII", "", "Increases melee damage"), List.of("Sharpness VII")));
		assertEquals(List.of(1), GameEnchantLines.repeated(List.of("Necron's Chestplate", "Protection VII", "Health: +260", "", "Protection VII"), List.of("Protection VII")));
	}

	@Test
	void aBookWhoseEnchantIsHiddenKeepsItsLore() {
		assertTrue(GameEnchantLines.repeated(List.of("Enchanted Book", "Sharpness VII", "", "Increases melee damage"), List.of("Sharpness VII")).isEmpty(), "the only line of it stays");
	}

	@Test
	void whatTheLoreDoesNotSayStays() {
		assertTrue(GameEnchantLines.repeated(List.of("Plain Pickaxe", "Efficiency V", "Breaking Power 4"), List.of("Efficiency V")).isEmpty());
		assertTrue(GameEnchantLines.repeated(List.of("Golden Dragon", "Combat Pet"), List.of()).isEmpty());
		assertTrue(GameEnchantLines.repeated(List.of("Drill", "Efficiency X", "Efficiency XI"), List.of("Efficiency X")).isEmpty(), "another level is not the same enchant");
	}

	@Test
	void aLoreLineWithANumberCounts() {
		assertEquals(List.of(1), GameEnchantLines.repeated(List.of("Drill", "Compact X", "Compact X (12,345)"), List.of("Compact X")));
	}

	@Test
	void everyEnchantOfTheDrillIsAtItsMost() {
		EnchantTiers tiers = tiers();
		for (String enchant : List.of("Compact X", "Efficiency X", "Experience V", "Fortune IV", "Lapidary V", "Paleontologist V", "Prismatic V", "Smelting Touch I")) {
			List<EnchantLore.Span> spans = EnchantLore.parse(enchant, tiers);
			assertEquals(1, spans.size(), enchant);
			assertEquals(EnchantTiers.Tier.PERFECT, spans.getFirst().tier(), enchant);
			assertEquals(enchant.length(), spans.getFirst().end(), "the whole of it, and the text stays");
		}
	}

	@Test
	void theLowerLevelsHaveTheirTiers() {
		EnchantTiers tiers = tiers();
		assertEquals(EnchantTiers.Tier.GOOD, EnchantLore.parse("Prismatic IV", tiers).getFirst().tier());
		assertEquals(EnchantTiers.Tier.GOOD, EnchantLore.parse("Compact V", tiers).getFirst().tier());
		assertEquals(EnchantTiers.Tier.POOR, EnchantLore.parse("Fortune I", tiers).getFirst().tier());
	}

	@Test
	void otherLinesOfTheDrillAreNoEnchants() {
		EnchantTiers tiers = tiers();
		for (String line : List.of("Breaking Power 10", "Damage: +75", "Mining Speed: +3,190 [+500] [+50] (+240)", "Perfectly-Cut Fuel Tank", "Glacial Divan's Drill")) {
			assertTrue(EnchantLore.parse(line, tiers).isEmpty(), line);
		}
	}

	@Test
	void ownColorsStayReadableHoweverDarkTheyAre() {
		// a player's own colors, which were unreadable on a tooltip
		for (int color : new int[]{0x1A00FF, 0x8700FF, 0x0D0035, 0x000000}) {
			assertTrue(EnchantPaint.contrast(EnchantPaint.readable(color), EnchantPaint.LIGHTEST_BACKGROUND) >= EnchantPaint.LEAST_CONTRAST, Integer.toHexString(color));
		}
		assertEquals(0xFF7AD9, EnchantPaint.readable(0xFF7AD9), "a color that reads stays as it is");
	}
}
