package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The server's lines for ready abilities, and which moments call for the ultimate of which class. */
class DungeonAbilityLinesTest {
	@Test
	void readyLines() {
		assertTrue(DungeonParse.ABILITY_READY.matcher("Guided Sheep is now available!").matches());
		assertTrue(DungeonParse.ULTIMATE_READY.matcher("Castle of Stone is ready to use! Press DROP to activate it!").matches());
		assertFalse(DungeonParse.ABILITY_READY.matcher("Kiwi: Guided Sheep is now available!").matches());
	}

	@Test
	void ultimateMoments() {
		String maxor = "⚠ Maxor is enraged! ⚠";
		assertEquals("Maxor is enraged", DungeonAbilityFeature.ultimateMoment(maxor, DungeonClass.HEALER));
		assertNull(DungeonAbilityFeature.ultimateMoment(maxor, DungeonClass.MAGE));
		assertEquals("Goldor's factory is down",
			DungeonAbilityFeature.ultimateMoment("[BOSS] Goldor: You have done it, you destroyed the factory…", DungeonClass.TANK));
		assertEquals("The giants are unleashed", DungeonAbilityFeature.ultimateMoment("[BOSS] Sadan: My giants! Unleashed!", DungeonClass.ARCHER));
		assertEquals("Livid begins", DungeonAbilityFeature.ultimateMoment(
			"[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.", DungeonClass.TANK));
		// a class not known yet: only the moment for every class
		assertEquals("The giants are unleashed", DungeonAbilityFeature.ultimateMoment("[BOSS] Sadan: My giants! Unleashed!", null));
		assertNull(DungeonAbilityFeature.ultimateMoment(maxor, null));
	}
}
