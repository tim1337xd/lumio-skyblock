package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Where the trackers are shown: not on the private island, where neither fishing nor mining is tracked. */
class SkyBlockIslandTest {
	@Test
	void miningIsNotTrackedOnThePrivateIsland() {
		assertFalse(SkyBlockIsland.miningHappensOn("Private Island"));
		assertFalse(SkyBlockIsland.miningHappensOn("The Garden"));
	}

	@Test
	void miningIsTrackedOnTheOthers() {
		for (String island : List.of("Dwarven Mines", "Crystal Hollows", "Glacite Mineshafts", "Deep Caverns", "Gold Mine", "The End", "Hub")) {
			assertTrue(SkyBlockIsland.miningHappensOn(island), island);
		}
		assertTrue(SkyBlockIsland.miningHappensOn(""), "between two worlds the island is not known");
	}

	@Test
	void fishingIsNotTrackedOnThePrivateIsland() {
		assertFalse(FishingRods.fishingIsland("Private Island"));
	}
}
