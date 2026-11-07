package dev.tim1337.lumio.skyblock.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A label has a fixed size in the world, as a name tag does, and fades out where its text cannot be read. */
class WorldLabelTest {
	private static final int GUI_2_HEIGHT = 540;
	private static final int GUI_4_HEIGHT = 270;
	private static final double FOV = 70.0;
	private static final float CLOSE = 0.0001f;

	@Test
	void itGetsSmallerInProportionToTheDistance() {
		assertEquals(WorldLabel.size(4.0, GUI_2_HEIGHT, FOV) / 2f, WorldLabel.size(8.0, GUI_2_HEIGHT, FOV), CLOSE);
		assertEquals(WorldLabel.size(5.0, GUI_2_HEIGHT, FOV) / 6f, WorldLabel.size(30.0, GUI_2_HEIGHT, FOV), CLOSE);
	}

	@Test
	void itHasNoLeastSizeFarAway() {
		assertTrue(WorldLabel.size(100.0, GUI_2_HEIGHT, FOV) < 0.2f);
	}

	@Test
	void itStopsGrowingWhenStandingInTheMob() {
		assertEquals(WorldLabel.size(2.0, GUI_2_HEIGHT, FOV), WorldLabel.size(0.5, GUI_2_HEIGHT, FOV), CLOSE);
	}

	@Test
	void itIsTheSameOnTheScreenAtEveryGuiScale() {
		assertEquals(WorldLabel.size(6.0, GUI_2_HEIGHT, FOV) * 2f, WorldLabel.size(6.0, GUI_4_HEIGHT, FOV) * 4f, CLOSE);
	}

	@Test
	void itIsFullyShownAtAFightingDistance() {
		for (double blocks = 3.0; blocks <= 8.0; blocks += 0.5) {
			assertEquals(1f, WorldLabel.fade(WorldLabel.size(blocks, GUI_2_HEIGHT, FOV), 2.0), CLOSE, "at " + blocks);
			assertEquals(1f, WorldLabel.fade(WorldLabel.size(blocks, GUI_4_HEIGHT, FOV), 4.0), CLOSE, "at " + blocks);
		}
	}

	@Test
	void itIsGoneWhereTheTextCouldNotBeRead() {
		assertEquals(0f, WorldLabel.fade(WorldLabel.size(30.0, GUI_2_HEIGHT, FOV), 2.0), CLOSE);
	}

	@Test
	void itFadesSoftlyAndComesBackTheSameWay() {
		float last = 1f;
		boolean between = false;
		for (double blocks = 8.0; blocks <= 30.0; blocks += 0.25) {
			float fade = WorldLabel.fade(WorldLabel.size(blocks, GUI_2_HEIGHT, FOV), 2.0);
			assertTrue(fade <= last + CLOSE, "at " + blocks);
			assertTrue(last - fade < 0.2f, "a soft step at " + blocks);
			between |= fade > 0.1f && fade < 0.9f;
			last = fade;
		}
		assertTrue(between);
	}
}
