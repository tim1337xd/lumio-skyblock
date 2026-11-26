package dev.tim1337.lumio.skyblock.mining.hollows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** World to map, the quadrants with the Nucleus in the middle, and the island's bounds. */
class HollowsGeometryTest {
	private static final float SIDE = 120f;

	@Test
	void theCornersAndTheMiddleOnTheMap() {
		assertEquals(0f, HollowsGeometry.toMap(202, SIDE), 0.001f);
		assertEquals(SIDE, HollowsGeometry.toMap(824, SIDE), 0.001f, "the far edge of the last block");
		assertEquals(SIDE / 2f, HollowsGeometry.toMap(HollowsGeometry.MIDDLE, SIDE), 0.001f);
	}

	@Test
	void placesOutsideStayOnTheEdge() {
		assertEquals(0f, HollowsGeometry.toMap(-500, SIDE));
		assertEquals(SIDE, HollowsGeometry.toMap(5000, SIDE));
	}

	@Test
	void quadrants() {
		assertEquals(HollowsGeometry.Quadrant.JUNGLE, HollowsGeometry.quadrantAt(300, 300));
		assertEquals(HollowsGeometry.Quadrant.MITHRIL_DEPOSITS, HollowsGeometry.quadrantAt(700, 300));
		assertEquals(HollowsGeometry.Quadrant.GOBLIN_HOLDOUT, HollowsGeometry.quadrantAt(300, 700));
		assertEquals(HollowsGeometry.Quadrant.PRECURSOR_REMNANTS, HollowsGeometry.quadrantAt(700, 700));
		assertEquals(HollowsGeometry.Quadrant.NUCLEUS, HollowsGeometry.quadrantAt(513, 513));
		assertEquals(HollowsGeometry.Quadrant.NUCLEUS, HollowsGeometry.quadrantAt(470, 555));
		assertNull(HollowsGeometry.quadrantAt(100, 300));
		assertNull(HollowsGeometry.quadrantAt(300, 824));
	}

	@Test
	void bounds() {
		assertTrue(HollowsGeometry.inside(202, 31, 823));
		assertFalse(HollowsGeometry.inside(201, 100, 500));
		assertFalse(HollowsGeometry.inside(500, 30, 500));
		assertFalse(HollowsGeometry.inside(500, 189, 500));
	}
}
