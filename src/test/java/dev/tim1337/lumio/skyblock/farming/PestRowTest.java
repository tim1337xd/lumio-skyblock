package dev.tim1337.lumio.skyblock.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The one row of the Pests panel's simple style: which state shows. */
class PestRowTest {
	@Test
	void pestsAliveShowTheCountElseTheCooldown() {
		assertEquals(PestRow.COOLDOWN, PestRow.of(0, true, false), "no pests: the time until the next");
		assertEquals(PestRow.PESTS, PestRow.of(1, true, false));
		assertEquals(PestRow.PESTS, PestRow.of(8, true, true), "pests alive win over a cooldown that is over");
	}

	@Test
	void theCooldownOverWithoutAPestIsReady() {
		assertEquals(PestRow.READY, PestRow.of(0, true, true));
	}

	@Test
	void withNextPestOffTheRowIsTheCountAlsoAtZero() {
		assertEquals(PestRow.PESTS, PestRow.of(0, false, false));
		assertEquals(PestRow.PESTS, PestRow.of(0, false, true));
		assertEquals(PestRow.PESTS, PestRow.of(3, false, false));
	}

	@Test
	void thePlotsFollowTheCountWhenShownAndPestsAreAlive() {
		assertEquals("3 · 4, 12", PestRow.countText(3, true, "4, 12"));
		assertEquals("3", PestRow.countText(3, false, "4, 12"));
		assertEquals("3", PestRow.countText(3, true, ""));
		assertEquals("0", PestRow.countText(0, true, "4, 12"), "no pests: no plots");
	}
}
