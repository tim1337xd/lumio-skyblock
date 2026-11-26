package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** A hotspot's marked area keeps its size through a lag: a burst of late particles or a neighbour's never grows it; a real change does, eased. */
class HotspotSizeTest {
	private static final long NOW = 1_000_000L;
	/** The largest change of the shown size from one frame to the next. */
	private static float biggestStep;

	/** Its own particles, spread over a disc of the given radius, one every 50 ms from a start. */
	private static long feed(Hotspots.Spot spot, float radius, int count, long from, long seed) {
		Random random = new Random(seed);
		long at = from;
		for (int i = 0; i < count; i++) {
			spot.sample((float) (radius * Math.sqrt(random.nextDouble())), at);
			float shown = spot.radius;
			spot.ease(at);
			biggestStep = Math.max(biggestStep, Math.abs(spot.radius - shown));
			at += 50L;
		}
		return at;
	}

	@Test
	void aBurstOfLateParticlesDoesNotGrowIt() {
		Hotspots.Spot spot = new Hotspots.Spot(1);
		long at = feed(spot, 4f, 64, NOW, 3);
		spot.ease(at);
		spot.ease(at + Hotspots.EASE_MILLIS);
		float before = spot.radius;
		// a lag: many particles at once, a few of them from far out (a neighbour, another player's line)
		for (int i = 0; i < 40; i++) {
			spot.sample(i % 3 == 0 ? 9.5f : 3.9f, at);
		}
		spot.ease(at + 50L);
		spot.ease(at + 1_000L);
		assertEquals(before, spot.radius, 0.15f, "the size stays through the burst");
	}

	@Test
	void aHotspotGrownFarIsMeasuredAfresh() {
		Hotspots.Spot spot = new Hotspots.Spot(3);
		long at = feed(spot, 3f, 64, NOW, 4);
		// its particles now reach twice as far, for many seconds: no lag, the hotspot itself
		at = feed(spot, 6f, 400, at, 8);
		spot.ease(at + Hotspots.EASE_MILLIS * 2);
		assertEquals(5.4f, spot.size, 0.7f, "the new size, " + spot.size);
	}

	@Test
	void aRealChangeIsTakenAfterAWhileAndEased() {
		Hotspots.Spot spot = new Hotspots.Spot(2);
		biggestStep = 0f;
		long at = feed(spot, 3f, 64, NOW, 5);
		spot.ease(at);
		spot.ease(at + Hotspots.EASE_MILLIS);
		float small = spot.radius;
		// the hotspot really grows: its particles reach farther for longer than the change takes
		at = feed(spot, 4f, 200, at, 9);
		assertEquals(small * 4f / 3f, spot.size, small * 0.25f, "the new size is taken");
		spot.ease(at + Hotspots.EASE_MILLIS * 2);
		assertEquals(spot.size, spot.radius, 0.01f, "the shown size gets there");
		assertEquals(true, biggestStep < (spot.size - small) * 0.5f, "with a short ease, not at once: a frame moved it " + biggestStep);
	}
}
