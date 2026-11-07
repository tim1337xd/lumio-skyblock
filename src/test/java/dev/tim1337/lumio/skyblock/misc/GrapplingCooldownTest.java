package dev.tim1337.lumio.skyblock.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class GrapplingCooldownTest {
	private static final double EXACT = 0.001;
	private static final double LENGTH = 2.0;
	/** A jump, sprinting: the most the player's own legs do in one tick. */
	private static final double JUMP_Y = 0.42;
	private static final double SPRINT_JUMP_X = 0.2;

	/** A hook that has been reeled in, standing still. */
	private static GrapplingCooldown reeled() {
		GrapplingCooldown hook = new GrapplingCooldown();
		hook.moved(0.0, 0.0, 0.0);
		hook.reeled(LENGTH);
		return hook;
	}

	@Test
	void readsTheLore() {
		assertEquals(2.0, GrapplingCooldown.fromLore(List.of("Travel around in style using this", "Grappling Hook.", "2 Second Cooldown")), EXACT);
		assertEquals(1.5, GrapplingCooldown.fromLore(List.of("1.5 Second Cooldown")), EXACT);
		assertEquals(GrapplingCooldown.DEFAULT_SECONDS, GrapplingCooldown.fromLore(List.of("UNCOMMON")), EXACT);
		assertEquals(GrapplingCooldown.DEFAULT_SECONDS, GrapplingCooldown.fromLore(List.of("99 Second Cooldown")), EXACT, "a number from the server stays bounded");
	}

	@Test
	void runsDownAndEmpties() {
		GrapplingCooldown hook = new GrapplingCooldown();
		assertFalse(hook.running());
		hook.pulled(2.0);
		assertTrue(hook.running());
		assertEquals(1f, hook.share(), EXACT);
		hook.advance(0.5);
		assertEquals(1.5, hook.left(), EXACT);
		assertEquals(0.75f, hook.share(), EXACT);
		hook.advance(5.0);
		assertFalse(hook.running());
		assertEquals(0f, hook.share(), EXACT);
	}

	@Test
	void aPullStartsIt() {
		GrapplingCooldown hook = reeled();
		assertFalse(hook.running(), "reeling in alone starts nothing");
		assertTrue(hook.moved(0.9, 0.6, 0.3));
		assertEquals(LENGTH, hook.left(), EXACT);
	}

	@Test
	void aPullMayArriveAMomentAfterTheClick() {
		GrapplingCooldown hook = reeled();
		for (int tick = 0; tick < GrapplingCooldown.PULL_WINDOW_TICKS - 1; tick++) {
			assertFalse(hook.moved(0.0, 0.0, 0.0));
		}
		assertTrue(hook.moved(0.9, 0.6, 0.3));
		assertTrue(hook.running());
	}

	@Test
	void aReelInWithoutAPullStartsNothing() {
		GrapplingCooldown hook = reeled();
		for (int tick = 0; tick < GrapplingCooldown.PULL_WINDOW_TICKS; tick++) {
			assertFalse(hook.moved(0.0, 0.0, 0.0));
		}
		assertFalse(hook.moved(0.9, 0.6, 0.3), "too long after the click: something else threw the player");
		assertFalse(hook.running());
	}

	@Test
	void aSpeedChangeWithoutAReelInStartsNothing() {
		GrapplingCooldown hook = new GrapplingCooldown();
		hook.moved(0.0, 0.0, 0.0);
		assertFalse(hook.moved(0.9, 0.6, 0.3));
		assertFalse(hook.running());
	}

	@Test
	void anEarlyUseChangesNothing() {
		GrapplingCooldown hook = reeled();
		hook.moved(0.9, 0.6, 0.3);
		hook.advance(0.5);
		hook.moved(0.0, 0.0, 0.0);
		hook.reeled(LENGTH);
		for (int tick = 0; tick < GrapplingCooldown.PULL_WINDOW_TICKS; tick++) {
			hook.moved(0.0, 0.0, 0.0);
		}
		assertEquals(1.5, hook.left(), EXACT, "the timer goes on from where it was");
	}

	@Test
	void theMessageDuringTheCooldownChangesNothing() {
		GrapplingCooldown hook = reeled();
		hook.moved(0.9, 0.6, 0.3);
		hook.advance(1.5);
		hook.moved(0.0, 0.0, 0.0);
		hook.reeled(LENGTH);
		hook.resync();
		assertEquals(0.5, hook.left(), EXACT);
		assertFalse(hook.moved(0.9, 0.6, 0.3), "the game said the use failed: what moves the player now is something else");
		assertEquals(0.5, hook.left(), EXACT);
	}

	@Test
	void aUseRightAfterItEndsStartsAgain() {
		GrapplingCooldown hook = reeled();
		hook.moved(0.9, 0.6, 0.3);
		hook.advance(LENGTH);
		assertFalse(hook.running());
		hook.moved(0.0, 0.0, 0.0);
		hook.reeled(LENGTH);
		assertTrue(hook.moved(0.9, 0.6, 0.3));
		assertEquals(LENGTH, hook.left(), EXACT);
	}

	@Test
	void theMessageWhileAtZeroShowsAShortTime() {
		GrapplingCooldown hook = reeled();
		hook.moved(0.9, 0.6, 0.3);
		hook.advance(LENGTH);
		hook.resync();
		assertEquals(GrapplingCooldown.SHORT_SECONDS, hook.left(), EXACT);
		assertTrue(hook.left() < LENGTH, "not a whole new cooldown");
		hook.advance(GrapplingCooldown.SHORT_SECONDS);
		assertFalse(hook.running());
	}

	@Test
	void theRealPullAfterTheMessageStartsAWholeOne() {
		GrapplingCooldown hook = new GrapplingCooldown();
		hook.resync();
		hook.moved(0.0, 0.0, 0.0);
		hook.reeled(LENGTH);
		assertTrue(hook.moved(0.9, 0.6, 0.3));
		assertEquals(LENGTH, hook.left(), EXACT);
	}

	@Test
	void thePlayersOwnMovesAreNoPull() {
		GrapplingCooldown jump = reeled();
		assertFalse(jump.moved(SPRINT_JUMP_X, JUMP_Y, 0.0), "a sprint jump");
		assertFalse(jump.running());

		GrapplingCooldown landing = new GrapplingCooldown();
		landing.moved(0.2, -1.5, 0.0);
		landing.reeled(LENGTH);
		assertFalse(landing.moved(0.2, -0.0784, 0.0), "landing changes the speed a lot, and pulls nothing");
		assertFalse(landing.running());
	}

	@Test
	void aPullWhileFallingCounts() {
		GrapplingCooldown hook = new GrapplingCooldown();
		hook.moved(0.0, -1.5, 0.0);
		hook.reeled(LENGTH);
		assertTrue(hook.moved(0.8, 0.3, 0.0));
		assertTrue(hook.running());
	}

	@Test
	void aSidewaysPullCounts() {
		GrapplingCooldown hook = reeled();
		assertTrue(hook.moved(1.2, -0.0784, 0.0));
		assertTrue(hook.running());
	}

	@Test
	void knowsTheMessage() {
		assertTrue(GrapplingCooldown.tooSoon("Whow! Slow down there!"));
		assertFalse(GrapplingCooldown.tooSoon("Slowdown"));
		assertFalse(GrapplingCooldown.tooSoon("You used the hook"));
	}
}
