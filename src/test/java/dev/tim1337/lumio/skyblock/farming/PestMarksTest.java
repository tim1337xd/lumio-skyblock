package dev.tim1337.lumio.skyblock.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Pest highlight: what counts as a pest, which stand is its body, and which color the glow has. */
class PestMarksTest {
	@Test
	void aNameTagWithThePestSignIsAPest() {
		assertTrue(PestMarks.pestTag("ൠ Fly 1,200❤"));
		assertTrue(PestMarks.pestTag("§4ൠ §cCricket §a800§c❤"));
		assertFalse(PestMarks.pestTag("Jacob"));
		assertFalse(PestMarks.pestTag("[Lv5] Silverfish 20❤"));
		assertFalse(PestMarks.pestTag(""));
		assertFalse(PestMarks.pestTag(null));
	}

	@Test
	void theNearestStandWithAHeadUnderTheNameTagIsThePest() {
		double[] bodies = {
			10.0, 64.0, 10.0,   // a decoration far away
			0.2, 63.2, 0.1,     // the pest, right under its name tag
			1.2, 64.0, 0.9};    // another head within reach, but farther
		assertEquals(1, PestMarks.body(0.0, 64.5, 0.0, bodies, 3));
	}

	@Test
	void aStandWithAHeadAndNoPestNameTagNearItIsNoPest() {
		double[] bodies = {4.0, 64.0, 0.0, 0.0, 70.0, 0.0};
		assertEquals(-1, PestMarks.body(0.0, 64.0, 0.0, bodies, 2), "one is too far across, one too far above");
		assertEquals(-1, PestMarks.body(0.0, 64.0, 0.0, bodies, 0), "no stands at all");
		// only the filled part of the list counts
		assertEquals(-1, PestMarks.body(4.0, 64.0, 0.0, bodies, 0));
		assertEquals(0, PestMarks.body(4.0, 64.0, 0.0, bodies, 1));
	}

	@Test
	void twoPestsSideBySideEachGetTheirOwnBody() {
		double[] bodies = {0.0, 63.0, 0.0, 1.0, 63.0, 0.0};
		assertEquals(0, PestMarks.body(0.1, 64.0, 0.0, bodies, 2));
		assertEquals(1, PestMarks.body(0.9, 64.0, 0.0, bodies, 2));
	}

	@Test
	void theGlowTakesTheMenuColorUnlessThePlayerPicksOne() {
		assertEquals(0x0A84FF, PestMarks.color(true, 0x0A84FF, 0xFF453A));
		assertEquals(0xFF453A, PestMarks.color(false, 0x0A84FF, 0xFF453A));
		assertEquals(0x0A84FF, PestMarks.color(true, 0xFF0A84FF, 0xFF453A), "never with an alpha part");
	}
}
