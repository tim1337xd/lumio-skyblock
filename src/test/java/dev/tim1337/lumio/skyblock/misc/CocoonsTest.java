package dev.tim1337.lumio.skyblock.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chat line about a cocoon, the hatch timer and the belts. */
class CocoonsTest {
	private static final double CLOSE = 0.0001;

	@Test
	void theChatNamesTheMob() {
		assertEquals("Enderman", Cocoons.mobOf("CAUGHT! You cocooned a Enderman!"));
		assertEquals("Enderman", Cocoons.mobOf("CAUGHT! You cocooned an Enderman!"));
		assertEquals("Crypt Ghoul", Cocoons.mobOf("CAUGHT! You cocooned a Crypt Ghoul!"));
		assertEquals("Lord Jawbus", Cocoons.mobOf("CAUGHT! You cocooned a Lord Jawbus!"));
	}

	@Test
	void otherLinesNameNothing() {
		assertNull(Cocoons.mobOf("CAUGHT! You cocooned!"));
		assertNull(Cocoons.mobOf("You cocooned a Zombie"));
		assertNull(Cocoons.mobOf("YOU COCOONED YOUR SLAYER BOSS"));
	}

	@Test
	void theTimerRunsDownToZero() {
		Cocoons rules = new Cocoons();
		assertEquals(6.4, rules.secondsLeft(0.0), CLOSE);
		assertEquals(4.4, rules.secondsLeft(2.0), CLOSE);
		assertEquals(0.0, rules.secondsLeft(9.0), CLOSE);
		assertEquals(1f, rules.left(0.0), CLOSE);
		assertEquals(0.5f, rules.left(3.2), CLOSE);
		assertEquals(0f, rules.left(20.0), CLOSE);
	}

	@Test
	void whatIsMeasuredTakesOverSlowly() {
		Cocoons rules = new Cocoons();
		rules.measured(8.4);
		assertEquals(6.9, rules.hatchSeconds(), CLOSE);
		for (int i = 0; i < 60; i++) {
			rules.measured(5.0);
		}
		assertEquals(5.0, rules.hatchSeconds(), 0.01);
	}

	@Test
	void aTimeThatCannotBeAHatchIsIgnored() {
		Cocoons rules = new Cocoons();
		rules.measured(0.5);
		rules.measured(40.0);
		assertEquals(Cocoons.FIRST_HATCH_SECONDS, rules.hatchSeconds(), CLOSE);
	}

	@Test
	void theBeltsThatCanCocoon() {
		assertTrue(Cocoons.beltCanCocoon("The Primordial"));
		assertTrue(Cocoons.beltCanCocoon("Blood Shot Implosion Belt"));
		assertFalse(Cocoons.beltCanCocoon("Implosion Belt"));
	}

	@Test
	void theRiftHasNoCocoons() {
		assertTrue(Cocoons.inRift("The Rift"));
		assertFalse(Cocoons.inRift("Crimson Isle"));
		assertFalse(Cocoons.inRift(null));
	}
}
