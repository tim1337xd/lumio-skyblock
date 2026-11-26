package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Hotspot Radar's estimate on three trails recorded in the game, shifted by whole blocks to lie near the origin: all point at the same
 * hotspot, whose middle the recorder's mark gave. Uses 1 and 2 were made looking well aside of it (the trail bends), use 3 looking almost at it.
 */
class RadarTrailTest {
	private static final double HOTSPOT_X = -0.40;
	private static final double HOTSPOT_Y = 0.5;
	private static final double HOTSPOT_Z = -0.56;

	/** A recorded use: its eyes and look, then its flames in order. */
	private record Recorded(double[] use, List<double[]> flames) {
		RadarTrail trail() {
			return new RadarTrail(use[0], use[1], use[2], use[3], use[4], use[5]);
		}
	}

	private static Recorded read(String name) throws IOException {
		try (InputStream in = RadarTrailTest.class.getResourceAsStream("/radar/" + name);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			double[] use = null;
			List<double[]> flames = new ArrayList<>();
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] parts = line.split(" ");
				if (parts[0].equals("use")) {
					use = new double[6];
					for (int i = 0; i < 6; i++) {
						use[i] = Double.parseDouble(parts[i + 1]);
					}
				} else if (parts[0].equals("flame")) {
					flames.add(new double[] {Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])});
				}
			}
			return new Recorded(use, flames);
		}
	}

	/** @return the estimate after the first {@code count} flames, or null */
	private static RadarTrail.Estimate after(Recorded recorded, int count) {
		RadarTrail trail = recorded.trail();
		RadarTrail.Estimate estimate = null;
		for (int i = 0; i < count; i++) {
			double[] flame = recorded.flames().get(i);
			assertTrue(trail.add(flame[0], flame[1], flame[2]), "flame " + i + " belongs to the trail");
			estimate = trail.estimate();
		}
		return estimate;
	}

	private static double miss(RadarTrail.Estimate estimate) {
		return Math.hypot(estimate.x() - HOTSPOT_X, estimate.z() - HOTSPOT_Z);
	}

	@Test
	void aBentTrailGivesThePlaceFromThreeFlames() throws IOException {
		for (String name : List.of("use1.txt", "use2.txt")) {
			RadarTrail.Estimate estimate = after(read(name), 3);
			assertNotNull(estimate, name);
			assertTrue(miss(estimate) < 1.0, name + " misses by " + miss(estimate));
		}
	}

	@Test
	void aStraightTrailWaitsForTheFourthFlame() throws IOException {
		Recorded recorded = read("use3.txt");
		assertNull(after(recorded, 3), "looking at the hotspot, three flames do not tell how far");
		RadarTrail.Estimate estimate = after(recorded, 4);
		assertNotNull(estimate);
		assertTrue(miss(estimate) < 1.5, "misses by " + miss(estimate));
	}

	@Test
	void everyTrailEndsOnTheHotspotsMiddle() throws IOException {
		for (String name : List.of("use1.txt", "use2.txt", "use3.txt")) {
			Recorded recorded = read(name);
			for (int count : new int[] {5, recorded.flames().size()}) {
				RadarTrail.Estimate estimate = after(recorded, count);
				assertNotNull(estimate, name + " " + count);
				assertTrue(miss(estimate) < 0.6, name + " after " + count + " misses by " + miss(estimate));
				assertEquals(HOTSPOT_Y, estimate.y(), 0.5, name + " height");
			}
		}
	}

	@Test
	void flamesElsewhereAreNotTheTrail() throws IOException {
		Recorded recorded = read("use1.txt");
		RadarTrail trail = recorded.trail();
		// the first flame lies one block ahead of the eyes; one far off is someone else's
		assertFalse(trail.add(recorded.use()[0] + 20, recorded.use()[1], recorded.use()[2]));
		double[] first = recorded.flames().getFirst();
		assertTrue(trail.add(first[0], first[1], first[2]));
		assertFalse(trail.add(first[0] + 10, first[1], first[2]), "a flame further than a step away");
		assertEquals(1, trail.size());
	}

	@Test
	void aStraightUniformLineGivesNoPlace() {
		// moving on at an even pace never comes to rest: nothing to show
		RadarTrail trail = new RadarTrail(0, 64, 0, 1, 0, 0);
		for (int i = 0; i < 12; i++) {
			trail.add(1 + i * 1.5, 64, 0);
		}
		assertNull(trail.estimate());
	}
}
