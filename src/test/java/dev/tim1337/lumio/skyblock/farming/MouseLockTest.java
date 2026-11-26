package dev.tim1337.lumio.skyblock.farming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the mouse lock locks and unlocks, and what it does to the mouse's movement. */
class MouseLockTest {
	private static final float CLOSE = 0.0001f;

	private static MouseLock locked() {
		MouseLock lock = new MouseLock();
		lock.toggle(true);
		return lock;
	}

	@Test
	void onePressLocksTheNextUnlocks() {
		MouseLock lock = new MouseLock();
		assertEquals(MouseLock.Toggled.LOCKED, lock.toggle(true));
		assertTrue(lock.on());
		assertEquals(MouseLock.Toggled.UNLOCKED, lock.toggle(true));
		assertFalse(lock.on());
	}

	@Test
	void outsideTheGardenItDoesNothing() {
		MouseLock lock = new MouseLock();
		assertEquals(MouseLock.Toggled.NOT_HERE, lock.toggle(false));
		assertFalse(lock.on());
	}

	@Test
	void itCanAlwaysBeUnlocked() {
		MouseLock lock = locked();
		assertEquals(MouseLock.Toggled.UNLOCKED, lock.toggle(false));
	}

	@Test
	void aTeleportUnlocks() {
		MouseLock lock = locked();
		assertTrue(lock.onChat("Teleported you to Plot - 4!", true));
		assertFalse(lock.on());
		lock = locked();
		assertTrue(lock.onChat("Teleported you to Plot - Mud Patch!", true));
		lock = locked();
		assertTrue(lock.onChat("Teleported you to The Barn!", true));
	}

	@Test
	void otherLinesAndTheSwitchLeaveItLocked() {
		MouseLock lock = locked();
		assertFalse(lock.onChat("Warping...", true));
		assertFalse(lock.onChat("[MVP+] Someone: Teleported you to The Barn!", true));
		assertFalse(lock.onChat("Teleported you to Plot - 4!", false));
		assertTrue(lock.on());
	}

	@Test
	void aMessageWhileUnlockedChangesNothing() {
		assertFalse(new MouseLock().onChat("Teleported you to The Barn!", true));
	}

	@Test
	void anythingOffUnlocks() {
		for (int off = 0; off < 5; off++) {
			MouseLock lock = locked();
			assertTrue(lock.check(off != 0, off != 1, off != 2, off != 3, off != 4), "case " + off);
			assertFalse(lock.on());
		}
	}

	@Test
	void disconnectingUnlocks() {
		MouseLock lock = locked();
		assertTrue(lock.check(true, false, true, true, true));
		assertEquals(1f, lock.scale(MouseLock.Mode.LOCK, 10f), CLOSE);
	}

	@Test
	void whileAllIsWellItStays() {
		MouseLock lock = locked();
		assertFalse(lock.check(true, true, true, true, true));
		assertTrue(lock.on());
	}

	@Test
	void theModesScaleTheMouse() {
		MouseLock lock = new MouseLock();
		assertEquals(1f, lock.scale(MouseLock.Mode.LOCK, 10f), CLOSE);
		lock.toggle(true);
		assertEquals(0f, lock.scale(MouseLock.Mode.LOCK, 10f), CLOSE);
		assertEquals(0.1f, lock.scale(MouseLock.Mode.SLOW, 10f), CLOSE);
		// a sample movement of 240 across
		assertEquals(24.0, 240.0 * lock.scale(MouseLock.Mode.SLOW, 10f), 0.01);
		assertEquals(0.0, 240.0 * lock.scale(MouseLock.Mode.LOCK, 10f), 0.01);
	}

	// ------------------------------------------------------------------------------------------------ the automatic lock

	private static final String HOE = "1:THEORETICAL_HOE_WHEAT_3";
	private static final String DICER = "2:MELON_DICER_2";

	/** Farming with the hoe, or not. */
	private static MouseLock.Farming farming(boolean now) {
		return new MouseLock.Farming(true, now, HOE);
	}

	@Test
	void locksByItselfWhileFarmingWithoutWaitingForACrop() {
		MouseLock lock = new MouseLock();
		assertEquals(MouseLock.Auto.NONE, lock.tick(farming(false)));
		assertEquals(MouseLock.Auto.LOCKED, lock.tick(farming(true)), "at once: no crop has to break first");
		assertTrue(lock.on());
		assertTrue(lock.automatic());
		assertEquals(0f, lock.scale(MouseLock.Mode.LOCK, 10f));
		assertEquals(MouseLock.Auto.NONE, lock.tick(farming(true)), "it stays while the farming goes on");
	}

	@Test
	void noAutomaticLockWithTheSwitchOff() {
		MouseLock lock = new MouseLock();
		assertEquals(MouseLock.Auto.NONE, lock.tick(new MouseLock.Farming(false, true, HOE)));
		assertFalse(lock.on());
	}

	@Test
	void theAutomaticLockLetsGoAtOnceWhenTheFarmingEnds() {
		MouseLock.Farming[] reasons = {
			new MouseLock.Farming(true, true, DICER),
			new MouseLock.Farming(true, false, HOE),
			new MouseLock.Farming(false, true, HOE)};
		String[] names = {"another item", "the farming ended (in the air, the Barn, a screen, out of the Garden)", "switched off"};
		for (int i = 0; i < reasons.length; i++) {
			MouseLock lock = new MouseLock();
			lock.tick(farming(true));
			assertEquals(MouseLock.Auto.UNLOCKED, lock.tick(reasons[i]), names[i]);
			assertFalse(lock.on(), names[i]);
			assertEquals(1f, lock.scale(MouseLock.Mode.LOCK, 10f), names[i] + ": the camera turns as usual again");
		}
	}

	@Test
	void aTeleportEndsTheAutomaticLockEvenWithTheSwitchOff() {
		MouseLock lock = new MouseLock();
		lock.tick(farming(true));
		assertTrue(lock.onChat("Teleported you to Plot - 4!", false));
		assertFalse(lock.on());
	}

	@Test
	void aManualUnlockWhileFarmingStaysUntilTheItemChangesOrTheFarmingEnds() {
		MouseLock lock = new MouseLock();
		lock.tick(farming(true));
		assertEquals(MouseLock.Toggled.UNLOCKED, lock.toggle(true, HOE, true));
		assertEquals(MouseLock.Auto.NONE, lock.tick(farming(true)), "still farming: it stays unlocked");
		assertFalse(lock.on());
		// another tool: it may lock again
		lock.tick(new MouseLock.Farming(true, true, DICER));
		assertTrue(lock.on(), "a switch ends the manual unlock");

		MouseLock stop = new MouseLock();
		stop.tick(farming(true));
		stop.toggle(true, HOE, true);
		stop.tick(farming(false));
		assertFalse(stop.on());
		assertEquals(MouseLock.Auto.LOCKED, stop.tick(farming(true)), "after the farming ended once, farming again locks again");
	}

	@Test
	void aManualLockStaysUntilTheNextPress() {
		MouseLock lock = new MouseLock();
		assertEquals(MouseLock.Toggled.LOCKED, lock.toggle(true, HOE, false));
		assertFalse(lock.automatic());
		assertEquals(MouseLock.Auto.NONE, lock.tick(new MouseLock.Farming(true, false, DICER)), "no farming, another item: a manual lock stays");
		assertTrue(lock.on());
		assertEquals(MouseLock.Toggled.UNLOCKED, lock.toggle(true, DICER, false));
		assertFalse(lock.on());
		// the safety unlocks still apply to a manual lock
		lock.toggle(true);
		assertTrue(lock.check(true, true, false, true, true));
	}
}
