package dev.tim1337.lumio.skyblock.dragons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonFightTest {
	private static final String NEST = "Dragon's Nest";
	private static final String ELSEWHERE = "Void Sepulture";

	@Test
	void aSpawnWhileElsewhereIsNothing() {
		DragonFight fight = new DragonFight();
		fight.spawned();
		fight.seeArea(ELSEWHERE);
		assertFalse(fight.involved());
		fight.down();
		assertFalse(fight.involved(), "and stays nothing to the end");
		assertFalse(fight.counts(true), "even with the switch on");
	}

	@Test
	void anEyePlacedIsInvolvedAndCounts() {
		DragonFight fight = new DragonFight();
		fight.eyePlaced();
		fight.spawned();
		fight.seeArea(ELSEWHERE);
		assertTrue(fight.involved());
		assertTrue(fight.counts(false));
	}

	@Test
	void anEyeTakenBackIsNot() {
		DragonFight fight = new DragonFight();
		fight.eyePlaced();
		fight.eyeRecovered();
		fight.spawned();
		assertFalse(fight.involved());
		fight.eyeRecovered();
		assertFalse(fight.involved());
	}

	@Test
	void inTheNestWithoutEyesShowsAndCountsOnlyWithTheSwitchAndListed() {
		DragonFight fight = new DragonFight();
		fight.spawned();
		fight.seeArea(NEST);
		assertTrue(fight.involved(), "shown");
		assertFalse(fight.counts(false));
		assertFalse(fight.counts(true), "not listed yet");
		fight.down();
		fight.listed();
		assertFalse(fight.counts(false), "the switch is off");
		assertTrue(fight.counts(true));
	}

	@Test
	void walkingIntoTheNestDuringTheFight() {
		DragonFight fight = new DragonFight();
		fight.spawned();
		fight.seeArea(ELSEWHERE);
		assertFalse(fight.involved());
		fight.seeArea(NEST);
		fight.seeArea(ELSEWHERE);
		assertTrue(fight.involved(), "any moment of the fight is enough");
	}

	@Test
	void theNestOutsideAFightIsNothing() {
		DragonFight fight = new DragonFight();
		fight.seeArea(NEST);
		assertFalse(fight.involved());
		fight.spawned();
		fight.down();
		fight.seeArea(NEST);
		assertFalse(fight.involved(), "the fight is over");
	}

	@Test
	void listedWithoutTheNestShowsButDoesNotCount() {
		DragonFight fight = new DragonFight();
		fight.spawned();
		fight.down();
		fight.listed();
		assertTrue(fight.involved());
		assertFalse(fight.counts(true), "only with the Nest");
	}

	@Test
	void theNextFightStartsClean() {
		DragonFight fight = new DragonFight();
		fight.eyePlaced();
		fight.spawned();
		fight.seeArea(NEST);
		fight.down();
		fight.listed();
		fight.finished();
		assertFalse(fight.involved());
		fight.spawned();
		assertFalse(fight.involved());
	}

	@Test
	void theAreaName() {
		assertTrue(DragonFight.isNest("Dragon's Nest"));
		assertTrue(DragonFight.isNest(" dragon's nest "));
		assertFalse(DragonFight.isNest("Dragon's Gate"));
		assertFalse(DragonFight.isNest(""));
		assertFalse(DragonFight.isNest(null));
	}
}
