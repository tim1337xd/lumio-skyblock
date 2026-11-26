package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Precision Mining target: only particles inside the block being mined, while mining, count; it follows the newest, fades about half a second
 * after the last, and is gone at once on another block or when mining stops.
 */
class PrecisionTargetTest {
	private static final double FACE_X = 11.06;

	private static PrecisionTarget mining() {
		PrecisionTarget target = new PrecisionTarget();
		target.mining(10, 64, 5);
		return target;
	}

	@Test
	void onlyParticlesInsideTheMinedBlock() {
		PrecisionTarget target = mining();
		assertTrue(target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 0), "on the block's east face");
		assertFalse(target.particle(PrecisionTarget.Kind.TARGET, 12.2, 64.5, 5.5, 0), "the next block over");
		assertFalse(target.particle(PrecisionTarget.Kind.TARGET, 10.5, 66.0, 5.5, 0), "above the block");
		assertEquals(FACE_X, target.x(), 1e-9, "the outside particles changed nothing");
		assertTrue(PrecisionTarget.inside(10.5 + PrecisionTarget.REACH, 64.5, 5.5, 10, 64, 5));
		assertFalse(PrecisionTarget.inside(10.5 + PrecisionTarget.REACH + 0.01, 64.5, 5.5, 10, 64, 5));
	}

	@Test
	void nothingWhileNotMining() {
		PrecisionTarget target = new PrecisionTarget();
		assertFalse(target.particle(PrecisionTarget.Kind.TARGET, 10.5, 64.5, 5.5, 0));
		assertEquals(0f, target.alpha(0));
	}

	@Test
	void followsTheNewestAndFadesAfterTheLast() {
		PrecisionTarget target = mining();
		target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 1_000);
		target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.6, 5.2, 1_050);
		assertEquals(64.6, target.y(), 1e-9, "the newest position");
		assertEquals(1f, target.alpha(1_050 + PrecisionTarget.HOLD_MILLIS));
		float fading = target.alpha(1_050 + PrecisionTarget.HOLD_MILLIS + PrecisionTarget.FADE_MILLIS / 2);
		assertTrue(fading > 0f && fading < 1f, "fading: " + fading);
		assertEquals(0f, target.alpha(1_050 + PrecisionTarget.HOLD_MILLIS + PrecisionTarget.FADE_MILLIS));
		assertEquals(500L, PrecisionTarget.HOLD_MILLIS + PrecisionTarget.FADE_MILLIS, "gone about half a second after the last particle");
	}

	@Test
	void anotherBlockOrStoppingClearsAtOnce() {
		PrecisionTarget target = mining();
		target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 1_000);
		target.mining(10, 64, 5);
		assertEquals(1f, target.alpha(1_010), "the same block keeps it");
		target.mining(11, 64, 5);
		assertEquals(0f, target.alpha(1_010), "another block: gone at once");
		target.mining(10, 64, 5);
		target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 2_000);
		target.stopped();
		assertEquals(0f, target.alpha(2_010), "stopped mining: gone at once");
		assertFalse(target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 2_020));
	}

	@Test
	void theServerSaysWhenItIsAimedAt() {
		PrecisionTarget target = mining();
		target.particle(PrecisionTarget.Kind.TARGET, FACE_X, 64.3, 5.7, 1_000);
		assertFalse(target.aimedByServer(1_000));
		target.particle(PrecisionTarget.Kind.AIMED, FACE_X, 64.3, 5.7, 1_100);
		assertTrue(target.aimedByServer(1_200));
		assertFalse(target.aimedByServer(1_100 + PrecisionTarget.AIMED_MILLIS + 1));
	}

	@Test
	void theFaceAndTheCrosshairOnIt() {
		assertEquals(1, PrecisionTarget.face(FACE_X, 64.3, 5.7, 10, 64, 5), "east: x, high side");
		assertEquals(2, PrecisionTarget.face(10.4, 63.95, 5.6, 10, 64, 5), "bottom");
		assertEquals(5, PrecisionTarget.face(10.4, 64.5, 6.05, 10, 64, 5), "south");
		// the crosshair's point on the east face, near the target, and far from it
		assertTrue(PrecisionTarget.onTarget(11.0, 64.32, 5.68, FACE_X, 64.3, 5.7, 1, 0.085));
		assertFalse(PrecisionTarget.onTarget(11.0, 64.7, 5.2, FACE_X, 64.3, 5.7, 1, 0.085));
	}
}
