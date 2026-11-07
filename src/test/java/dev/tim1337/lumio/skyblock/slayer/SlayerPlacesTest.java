package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What is only for a slayer: with its quest on one of its islands, or with its boss near; never with only one of them, and not where the place is unknown. */
class SlayerPlacesTest {
	private static final String[][] RIGHT = {
		{"Revenant Horror IV", "Hub"},
		{"Tarantula Broodfather III", "Spider's Den"},
		{"Sven Packmaster IV", "The Park"},
		{"Sven Packmaster IV", "Hub"},
		{"Voidgloom Seraph IV", "The End"},
		{"Inferno Demonlord II", "Crimson Isle"},
		{"Riftstalker Bloodfiend III", "The Rift"}
	};
	private static final String[][] WRONG = {
		{"Revenant Horror IV", "The End"},
		{"Tarantula Broodfather III", "Dwarven Mines"},
		{"Tarantula Broodfather III", "Hub"},
		{"Sven Packmaster IV", "Spider's Den"},
		{"Voidgloom Seraph IV", "Crimson Isle"},
		{"Inferno Demonlord II", "The End"},
		{"Riftstalker Bloodfiend III", "Hub"}
	};

	@Test
	void eachSlayerOnItsIslands() {
		for (String[] place : RIGHT) {
			assertTrue(SlayerPlaces.shows(place[0], place[1], false), place[0] + " on " + place[1]);
		}
	}

	@Test
	void eachSlayerElsewhere() {
		for (String[] place : WRONG) {
			assertFalse(SlayerPlaces.shows(place[0], place[1], false), place[0] + " on " + place[1]);
		}
	}

	@Test
	void itsBossNearShowsItAnywhere() {
		for (String[] place : WRONG) {
			assertTrue(SlayerPlaces.shows(place[0], place[1], true), place[0] + " on " + place[1]);
		}
		assertTrue(SlayerPlaces.shows("", "", true), "a boss carried for someone, with no quest of the player's own");
	}

	@Test
	void noQuestIsHidden() {
		for (String[] place : RIGHT) {
			assertFalse(SlayerPlaces.shows("", place[1], false), place[1]);
		}
	}

	@Test
	void anUnknownPlaceIsHidden() {
		for (String[] place : RIGHT) {
			assertFalse(SlayerPlaces.shows(place[0], "", false), place[0]);
			assertFalse(SlayerPlaces.shows(place[0], null, false), place[0]);
		}
	}

	@Test
	void aSlayerNotOnTheListIsHiddenUntilItsBossIsNear() {
		assertFalse(SlayerPlaces.shows("Some New Boss I", "Hub", false));
		assertTrue(SlayerPlaces.shows("Some New Boss I", "Hub", true));
	}
}
