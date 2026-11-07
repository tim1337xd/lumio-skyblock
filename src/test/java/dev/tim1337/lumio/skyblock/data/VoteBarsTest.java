package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The election's votes come as text blocks, and become a bar and a percent. */
class VoteBarsTest {
	private static final float CLOSE = 0.0001f;

	@Test
	void aRunOfBarsIsFound() {
		assertEquals(6, VoteBars.barsStart("Diana ||||||||| 34%"));
		assertEquals(-1, VoteBars.barsStart("Diana"));
		assertEquals(-1, VoteBars.barsStart("A || B"));
	}

	@Test
	void thePercentInTheTextCounts() {
		assertEquals(0.34f, VoteBars.share("||||||||| 34%", 3, 10), CLOSE);
		assertEquals(1f, VoteBars.share("|||| 100%", 1, 4), CLOSE);
	}

	@Test
	void withoutOneTheColoredBarsCount() {
		assertEquals(0.3f, VoteBars.share("||||||||||", 3, 10), CLOSE);
		assertTrue(VoteBars.share("", 0, 0) == 0f);
	}
}
