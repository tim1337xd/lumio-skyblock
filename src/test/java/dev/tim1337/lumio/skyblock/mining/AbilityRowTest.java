package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ability's row with its name off and on, the longest name, and a width set too narrow; with letters as wide as the small bold text is, about. */
class AbilityRowTest {
	private static final float LETTER = 4.6f;
	private static final float STATUS = 24f;

	private static float widthOf(String name) {
		return name.length() * LETTER;
	}

	@Test
	void nameOffIsJustTheBarAndTheStatus() {
		AbilityRow off = AbilityRow.of(0f, STATUS, true, 0);
		AbilityRow on = AbilityRow.of(widthOf("Mining Speed Boost"), STATUS, true, 0);
		assertTrue(off.width() < on.width(), "narrower without a name");
		assertEquals(0f, off.nameRoom());
		assertEquals(AbilityRow.BAR, off.barWidth(), 0.5f);
		assertEquals(off.nameX(), off.barX(), "the bar follows the icon at once");
	}

	@Test
	void withoutTheIconItIsNarrowerStill() {
		AbilityRow icon = AbilityRow.of(0f, STATUS, true, 0);
		AbilityRow none = AbilityRow.of(0f, STATUS, false, 0);
		assertTrue(none.width() < icon.width());
		assertTrue(none.barX() < icon.barX());
	}

	@Test
	void nameOnGrowsToTheWholeNameOfAnyAbility() {
		for (String name : new String[]{"Mining Speed Boost", "Pickobulus", "Maniac Miner", "Gemstone Infusion", "Hazardous Miner", "Anomalous Desire", "Pickaxe ability"}) {
			AbilityRow row = AbilityRow.of(widthOf(name), STATUS, true, 0);
			assertFalse(row.cut(), name);
			assertEquals(1f, row.nameScale(), name);
			assertEquals(widthOf(name), row.nameRoom(), 0.01f, name);
			assertTrue(row.barWidth() >= AbilityRow.LEAST_BAR, name);
		}
	}

	@Test
	void aLongerNameMakesALongerRowWithTheSameBar() {
		AbilityRow shorter = AbilityRow.of(widthOf("Pickobulus"), STATUS, true, 0);
		AbilityRow longer = AbilityRow.of(widthOf("Mining Speed Boost"), STATUS, true, 0);
		assertTrue(longer.width() > shorter.width());
		assertEquals(shorter.barWidth(), longer.barWidth(), 1f);
	}

	@Test
	void aSetWidthWithRoomGivesTheRestToTheBar() {
		AbilityRow row = AbilityRow.of(widthOf("Pickobulus"), STATUS, true, 200);
		assertEquals(200, row.width());
		assertEquals(1f, row.nameScale());
		assertTrue(row.barWidth() > AbilityRow.BAR);
		assertEquals(row.statusEnd() - STATUS - 5f - row.barX(), row.barWidth(), 0.01f);
	}

	@Test
	void aNarrowSetWidthShrinksTheNameBeforeItIsCut() {
		float name = widthOf("Mining Speed Boost");
		AbilityRow auto = AbilityRow.of(name, STATUS, true, 0);
		// a little too narrow: smaller letters, all of them there
		AbilityRow tight = AbilityRow.of(name, STATUS, true, auto.width() - (int) AbilityRow.BAR + (int) AbilityRow.LEAST_BAR - 8);
		assertFalse(tight.cut());
		assertTrue(tight.nameScale() < 1f && tight.nameScale() >= AbilityRow.LEAST_SCALE, "scale " + tight.nameScale());
		assertEquals(AbilityRow.LEAST_BAR, tight.barWidth(), 0.5f, "the bar keeps its least");
		// far too narrow: only now an ellipsis
		AbilityRow narrow = AbilityRow.of(name, STATUS, true, 100);
		assertTrue(narrow.cut());
		assertEquals(AbilityRow.LEAST_SCALE, narrow.nameScale());
		assertEquals(AbilityRow.LEAST_BAR, narrow.barWidth(), 0.5f);
	}

	@Test
	void theStatusEndsInTheSamePlaceWhateverItSays() {
		// the slot is as wide as its longest text: "Ready" and "12s" share it
		AbilityRow ready = AbilityRow.of(widthOf("Pickobulus"), STATUS, true, 0);
		AbilityRow cooling = AbilityRow.of(widthOf("Pickobulus"), STATUS, true, 0);
		assertEquals(ready.statusEnd(), cooling.statusEnd());
		assertEquals(ready.barWidth(), cooling.barWidth());
		assertEquals(ready.width() - 5f, ready.statusEnd());
	}
}
