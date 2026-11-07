package dev.tim1337.lumio.skyblock.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a real cocoon counts: the larva's exact texture, where a mob just died, with the chat line for the player's own. */
class CocoonCheckTest {
	private static final String LARVA = "4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
	private static final String OTHER_SKULL = "9f1a00000000000000000000000000000000000000000000000000000000beef";
	private static final double RECENT = 0.2;
	private static final double LONG_AGO = 60.0;

	private static CocoonCheck.Verdict judge(String texture, boolean part, boolean label, double chat, double death, double waited) {
		return CocoonCheck.judge(new CocoonCheck.Sighting(texture, part, label, chat, death, 0, false), LARVA, waited);
	}

	@Test
	void aRealCocoonCounts() {
		assertEquals(CocoonCheck.Verdict.COCOON, judge(LARVA, false, false, RECENT, RECENT, 0.0));
		assertEquals(CocoonCheck.Verdict.COCOON, judge(LARVA, false, false, 2.5, 2.5, 0.0), "the chat line and the death a moment apart");
	}

	@Test
	void anEggSacDoesNotEvenWhereAMobDied() {
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, true, false, RECENT, RECENT, 0.0), "the boss's reader counts it");
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, true, RECENT, RECENT, 0.0), "its name stands at it");
	}

	@Test
	void aRandomSkullDoesNot() {
		assertEquals(CocoonCheck.Verdict.NOT, judge(OTHER_SKULL, false, false, RECENT, RECENT, 0.0));
		assertEquals(CocoonCheck.Verdict.NOT, judge("", false, false, RECENT, RECENT, 0.0));
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA.substring(1), false, false, RECENT, RECENT, 0.0), "the whole value is compared");
	}

	@Test
	void aCocoonWithoutTheChatLineOrADeathDoesNot() {
		assertEquals(CocoonCheck.Verdict.WAIT, judge(LARVA, false, false, CocoonCheck.NEVER, CocoonCheck.NEVER, 0.2), "either can still come");
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, false, CocoonCheck.NEVER, CocoonCheck.NEVER, CocoonCheck.WAIT_SECONDS));
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, false, CocoonCheck.NEVER, LONG_AGO, CocoonCheck.WAIT_SECONDS), "a death long ago");
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, false, CocoonCheck.NEVER, 1.5, CocoonCheck.WAIT_SECONDS), "another player's needs the death within a second");
	}

	@Test
	void theChatLineAloneIsNotEnough() {
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, false, RECENT, CocoonCheck.NEVER, CocoonCheck.WAIT_SECONDS));
		assertEquals(CocoonCheck.Verdict.NOT, judge(LARVA, false, false, RECENT, LONG_AGO, CocoonCheck.WAIT_SECONDS));
	}

	@Test
	void anotherPlayersCountsWithinASecondOfTheDeath() {
		assertEquals(CocoonCheck.Verdict.COCOON, judge(LARVA, false, false, CocoonCheck.NEVER, 0.9, 0.0));
	}

	@Test
	void anEggSacsLabelsAreKnown() {
		assertTrue(CocoonCheck.isHelperLabel("SHOOT ME!"));
		assertTrue(CocoonCheck.isHelperLabel(" 11s 7/9 "));
		assertFalse(CocoonCheck.isHelperLabel("Enderman"));
		assertFalse(CocoonCheck.isHelperLabel("11s"));
	}

	/** An egg sac has the larva's head and a little spider dying right at it, as a cocoon has: with neither its name nor its timer seen yet, the fight and its group tell. */
	@Test
	void anEggSacInATarantulaFightIsNoCocoon() {
		CocoonCheck.Sighting sac = new CocoonCheck.Sighting(LARVA_FOR_SAC, false, false, CocoonCheck.NEVER, 0.2, 0, true);
		assertEquals(CocoonCheck.Verdict.WAIT, CocoonCheck.judge(sac, LARVA_FOR_SAC, 0.2));
		assertEquals(CocoonCheck.Verdict.NOT, CocoonCheck.judge(sac, LARVA_FOR_SAC, CocoonCheck.WAIT_SECONDS));
	}

	@Test
	void sacsInAGroupAreNoCocoon() {
		CocoonCheck.Sighting sac = new CocoonCheck.Sighting(LARVA_FOR_SAC, false, false, CocoonCheck.NEVER, 0.2, 3, false);
		assertEquals(CocoonCheck.Verdict.NOT, CocoonCheck.judge(sac, LARVA_FOR_SAC, CocoonCheck.WAIT_SECONDS));
	}

	@Test
	void theSameHeadWithItsNameOrTimerIsASac() {
		CocoonCheck.Sighting sac = new CocoonCheck.Sighting(LARVA_FOR_SAC, false, true, 0.1, 0.1, 0, false);
		assertEquals(CocoonCheck.Verdict.NOT, CocoonCheck.judge(sac, LARVA_FOR_SAC, 0.1));
		assertTrue(CocoonCheck.isHelperLabel("SHOOT ME!"));
		assertTrue(CocoonCheck.isHelperLabel("11s 7/9"));
	}

	@Test
	void myOwnCocoonInATarantulaFightIsOne() {
		CocoonCheck.Sighting mine = new CocoonCheck.Sighting(LARVA_FOR_SAC, false, false, 0.5, 0.5, 0, true);
		assertEquals(CocoonCheck.Verdict.COCOON, CocoonCheck.judge(mine, LARVA_FOR_SAC, 0.1));
	}

	@Test
	void aLoneCocoonOfAnotherPlayerAwayFromAFightIsOne() {
		CocoonCheck.Sighting theirs = new CocoonCheck.Sighting(LARVA_FOR_SAC, false, false, CocoonCheck.NEVER, 0.5, 0, false);
		assertEquals(CocoonCheck.Verdict.COCOON, CocoonCheck.judge(theirs, LARVA_FOR_SAC, 0.1));
	}

	private static final String LARVA_FOR_SAC = "larva";
}
