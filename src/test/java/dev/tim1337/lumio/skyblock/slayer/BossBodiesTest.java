package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boss is its main mob, for every slayer, never a helper. */
class BossBodiesTest {
	@Test
	void eachSlayerHasItsMob() {
		assertTrue(BossBodies.isBody("voidgloom seraph iv", "enderman", false, false));
		assertTrue(BossBodies.isBody("tarantula broodfather iii", "spider", false, false));
		assertTrue(BossBodies.isBody("tarantula broodfather iii", "cave_spider", false, false));
		assertTrue(BossBodies.isBody("revenant horror v", "zombie", false, false));
		assertTrue(BossBodies.isBody("sven packmaster iv", "wolf", false, false));
		assertTrue(BossBodies.isBody("inferno demonlord iv", "blaze", false, false));
	}

	@Test
	void helpersAreNever() {
		assertFalse(BossBodies.isBody("voidgloom seraph iv", "guardian", true, false));
		assertFalse(BossBodies.isBody("voidgloom seraph iv", "guardian", false, false));
		assertFalse(BossBodies.isBody("voidgloom seraph iv", "endermite", false, false));
		assertFalse(BossBodies.isBody("sven packmaster iv", "zombie", false, false));
	}

	@Test
	void aBossOfAKnownKindStaysTheBossWhileItRides() {
		assertTrue(BossBodies.isBody("voidgloom seraph iv", "enderman", false, true), "in its laser");
		assertFalse(BossBodies.isBody("voidgloom seraph iv", "guardian", true, true));
	}

	@Test
	void aRiderIsNotAnUnknownBoss() {
		assertFalse(BossBodies.isBody("riftstalker bloodfiend", "zombie", false, true));
	}

	@Test
	void theBossStaysTheSameMobThroughItsPhases() {
		assertTrue(BossLink.keeps(false, true, true, 2.0, 0), "the name tag at hits, or anything else it says");
		assertTrue(BossLink.keeps(false, true, true, 12.0, 0), "the name tag gone far from a boss that rides");
		assertTrue(BossLink.keeps(false, true, false, 0.0, 40), "the name tag not there for a moment");
	}

	@Test
	void theLinkEnds() {
		assertFalse(BossLink.keeps(true, true, true, 2.0, 0), "dead or gone");
		assertFalse(BossLink.keeps(false, false, true, 2.0, 0), "another boss is watched");
		assertFalse(BossLink.keeps(false, true, true, BossLink.LOST_BLOCKS + 1.0, 0), "far from its name tag");
		assertFalse(BossLink.keeps(false, true, false, 0.0, BossLink.NO_TAG_TICKS + 1), "no name tag for long");
	}

	@Test
	void anInvisibleBossStaysTheBoss() {
		assertTrue(BossBodies.isBody("voidgloom seraph iv", "enderman", true, false));
	}

	@Test
	void anUnknownBossIsAnyMobThatCanBeSeen() {
		assertTrue(BossBodies.isBody("riftstalker bloodfiend", "zombie", false, false));
		assertFalse(BossBodies.isBody("riftstalker bloodfiend", "zombie", true, false));
	}
}
