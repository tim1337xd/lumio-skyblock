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
}
