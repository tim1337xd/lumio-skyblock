package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When the radar's waypoint goes: near the middle, inside a large hotspot, or the bobber in it. */
class RadarArrivalTest {
	private static final double NONE = Double.NaN;

	@Test
	void fishingFromTheShoreEightBlocksAwayCounts() {
		assertTrue(RadarArrival.reached(0, 0, 3, 7.5, 0, false, 0, 0));
		assertFalse(RadarArrival.reached(0, 0, 3, 12, 0, false, 0, 0));
	}

	@Test
	void insideALargeHotspotCountsEvenBeyondEightBlocks() {
		assertTrue(RadarArrival.reached(0, 0, 10, 9.5, 0, false, 0, 0));
		assertFalse(RadarArrival.reached(0, 0, 10, 10.5, 0, false, 0, 0));
	}

	@Test
	void theBobberLandingInTheHotspotCounts() {
		assertTrue(RadarArrival.reached(0, 0, 4, 20, 0, true, 3.5, 0));
		// beside it, or still in the air, it does not
		assertFalse(RadarArrival.reached(0, 0, 4, 20, 0, true, 5, 0));
		assertFalse(RadarArrival.reached(0, 0, 4, 20, 0, false, 0, 0));
		// where the game shows no hotspot, a usual one is assumed
		assertTrue(RadarArrival.reached(0, 0, NONE, 20, 0, true, 2.5, 0));
		assertFalse(RadarArrival.reached(0, 0, NONE, 20, 0, true, 4, 0));
	}

	@Test
	void aShownHotspotNearTheWaypointIsItsOwn() {
		assertTrue(RadarArrival.matches(2, 1, 3, 0, 0));
		assertFalse(RadarArrival.matches(20, 0, 3, 0, 0));
	}
}
