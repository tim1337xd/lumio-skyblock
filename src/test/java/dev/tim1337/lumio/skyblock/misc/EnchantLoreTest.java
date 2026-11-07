package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.skyblock.data.EnchantTiers;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The enchants in sample lore, each tier, an unknown one, an ultimate and a growing one with extra text; and the moving colors. */
class EnchantLoreTest {
	private static final EnchantTiers TIERS = EnchantTiers.of(JsonParser.parseString("""
		{"enchants_xp_cost":{"sharpness":[1,2,3,4,5,6,7],"smite":[1,2,3,4,5,6,7],"cubism":[1,2,3,4,5,6],"ender_slayer":[1,2,3,4,5,6,7],"compact":[1]},
		"max_xp_table_levels":{"sharpness":5,"smite":5,"cubism":5,"ender_slayer":5},"enchants":{"SWORD":["ultimate_wise"]}}""").getAsJsonObject());
	private static final int[] COLORS = {0xFF0000, 0x00FF00, 0x0000FF};

	private static String textOf(String line, EnchantLore.Span span) {
		return line.substring(span.start(), span.end());
	}

	private static EnchantTiers withBooks() {
		EnchantTiers tiers = EnchantTiers.of(JsonParser.parseString("""
			{"enchants_xp_cost":{"sharpness":[1,2,3,4,5,6,7],"blessing":[1,2,3,4,5]},"max_xp_table_levels":{"sharpness":5,"blessing":5},"enchants":{"SWORD":["ultimate_wise"]}}""").getAsJsonObject());
		tiers.addBooks(java.util.Map.of("sharpness", 7, "blessing", 6, "arcane", 6, "telekinesis", 1, "compact", 1, "ultimate_wise", 5));
		return tiers;
	}

	@Test
	void gravityAndDrainAtTheirMost() {
		EnchantTiers tiers = withBooks();
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Gravity", 6));
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Drain", 5));
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Gravity", 5));
		assertEquals(EnchantTiers.Tier.POOR, tiers.tier("Drain", 3));
	}

	@Test
	void theRepositorysBooksSayHowManyLevels() {
		EnchantTiers tiers = withBooks();
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Blessing", 6));
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Blessing", 5), "the costs end at five, the books at six");
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Arcane", 6), "no enchanting costs at all");
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Arcane", 5));
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Telekinesis", 1));
	}

	@Test
	void nothingBelowItsMostIsPerfect() {
		EnchantTiers tiers = withBooks();
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Sharpness", 7));
		assertEquals(EnchantTiers.Tier.GREAT, tiers.tier("Sharpness", 6));
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Sharpness", 5));
		assertEquals(EnchantTiers.Tier.POOR, tiers.tier("Sharpness", 4));
	}

	@Test
	void anUltimateKeepsItsLook() {
		assertEquals(EnchantTiers.Tier.ULTIMATE, withBooks().tier("Ultimate Wise", 5));
		assertEquals(EnchantTiers.Tier.ULTIMATE, withBooks().tier("Ultimate Wise", 1));
	}

	@Test
	void whatGrowsWithUseIsNotOneLevelOnly() {
		EnchantTiers tiers = withBooks();
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Compact", 10));
		assertEquals(EnchantTiers.Tier.POOR, tiers.tier("Compact", 1));
	}

	@Test
	void anUnknownEnchantIsLeftAloneAndNotedOnce() {
		EnchantTiers tiers = withBooks();
		assertEquals(null, tiers.tier("Brand New", 3));
		assertTrue(tiers.firstUnknown("Brand New"));
		assertEquals(false, tiers.firstUnknown("Brand New"));
		assertEquals(false, tiers.firstUnknown("Sharpness"));
		String line = "Brand New III, Sharpness VII";
		List<EnchantLore.Span> spans = EnchantLore.parse(line, tiers);
		assertEquals(1, spans.size());
		assertEquals("Sharpness VII", textOf(line, spans.get(0)));
	}

	@Test
	void gravityAndDrainInALine() {
		String line = "Gravity VI, Drain V, Gravity V";
		List<EnchantLore.Span> spans = EnchantLore.parse(line, withBooks());
		assertEquals(3, spans.size());
		assertEquals(EnchantTiers.Tier.PERFECT, spans.get(0).tier());
		assertEquals(EnchantTiers.Tier.PERFECT, spans.get(1).tier());
		assertEquals(EnchantTiers.Tier.GOOD, spans.get(2).tier());
	}

	@Test
	void eachTierInALine() {
		String line = "Ultimate Wise V, Sharpness VII, Smite VI, Cubism V, Ender Slayer III";
		List<EnchantLore.Span> spans = EnchantLore.parse(line, TIERS);
		assertEquals(5, spans.size());
		assertEquals(EnchantTiers.Tier.ULTIMATE, spans.get(0).tier());
		assertEquals("Ultimate Wise V", textOf(line, spans.get(0)));
		assertEquals(EnchantTiers.Tier.PERFECT, spans.get(1).tier());
		assertEquals(EnchantTiers.Tier.GREAT, spans.get(2).tier());
		assertEquals(EnchantTiers.Tier.GOOD, spans.get(3).tier());
		assertEquals(EnchantTiers.Tier.POOR, spans.get(4).tier());
		assertEquals("Ender Slayer III", textOf(line, spans.get(4)));
	}

	@Test
	void anUnknownEnchantKeepsItsColor() {
		String line = "Made Up IV, Sharpness VII";
		List<EnchantLore.Span> spans = EnchantLore.parse(line, TIERS);
		assertEquals(1, spans.size());
		assertEquals("Sharpness VII", textOf(line, spans.get(0)));
	}

	@Test
	void extraTextStaysOutOfTheColor() {
		String line = "Compact X (12,345), Smite VII";
		List<EnchantLore.Span> spans = EnchantLore.parse(line, TIERS);
		assertEquals(2, spans.size());
		assertEquals("Compact X", textOf(line, spans.get(0)));
		assertEquals(EnchantTiers.Tier.PERFECT, spans.get(0).tier());
		assertEquals("Compact V", textOf("Compact V 1,234", EnchantLore.parse("Compact V 1,234", TIERS).get(0)));
	}

	@Test
	void aLineCutAfterAComma() {
		assertEquals(1, EnchantLore.parse("Sharpness VII,", TIERS).size());
		assertEquals(1, EnchantLore.parse("  Sharpness VII", TIERS).size());
	}

	@Test
	void descriptionsStayAsTheyAre() {
		assertTrue(EnchantLore.parse("Increases melee damage dealt by 50%", TIERS).isEmpty());
		assertTrue(EnchantLore.parse("Sharpness VII is great", TIERS).isEmpty());
		assertTrue(EnchantLore.parse("", TIERS).isEmpty());
		assertTrue(EnchantLore.parse("RARE SWORD", TIERS).isEmpty());
	}

	@Test
	void chromaMovesThroughTheLettersAndTime() {
		assertNotEquals(EnchantPaint.chroma(1f, 0), EnchantPaint.chroma(1f, 5));
		assertNotEquals(EnchantPaint.chroma(1f, 0), EnchantPaint.chroma(3f, 0));
		assertEquals(EnchantPaint.chroma(2f, 3), EnchantPaint.chroma(2f, 3));
	}

	@Test
	void ownColorsFlowThroughTheirOnesOnly() {
		// at the very start the first color shows, and only the colors in use are mixed
		assertEquals(COLORS[0], EnchantPaint.flow(COLORS, 3, 1f, 0f, 0));
		int two = EnchantPaint.flow(COLORS, 2, 1f, 1f, 0);
		assertEquals(0, two & 0xFF, "the third color is not used");
		assertNotEquals(EnchantPaint.flow(COLORS, 3, 1f, 1f, 0), EnchantPaint.flow(COLORS, 3, 1f, 1f, 4));
		assertNotEquals(EnchantPaint.flow(COLORS, 3, 0.5f, 1f, 0), EnchantPaint.flow(COLORS, 3, 2f, 1f, 0), "the speed counts");
	}
}
