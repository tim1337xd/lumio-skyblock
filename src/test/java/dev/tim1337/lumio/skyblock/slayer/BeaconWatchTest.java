package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A beacon through its life: thrown, landing, a block until it is gone. The thing that carried it may stay around after it lands. */
class BeaconWatchTest {
	private static final int CARRIER = 7;
	private static final int NEXT_CARRIER = 8;

	@Test
	void itIsLookedForWhileItFliesAndAfter() {
		BeaconWatch watch = new BeaconWatch();
		assertFalse(watch.searching());
		watch.thrown(CARRIER, 1, 2, 3);
		assertTrue(watch.searching());
		watch.noneThrown(100);
		assertTrue(watch.searching());
		watch.noneThrown(100 + BeaconWatch.LAND_TICKS + 1);
		assertFalse(watch.searching());
		assertFalse(watch.up());
	}

	@Test
	void aBlockFoundWhileItsCarrierIsStillThereCounts() {
		BeaconWatch watch = new BeaconWatch();
		watch.thrown(CARRIER, 1, 2, 3);
		watch.landed(1, 0, 3);
		assertTrue(watch.placed());
		// the carrier stays: it does not throw the block back into the air
		watch.thrown(CARRIER, 1, 2, 3);
		assertTrue(watch.placed());
		assertEquals(0, watch.blockY());
		assertFalse(watch.searching());
	}

	@Test
	void aSpentCarrierDoesNotAlertAgainAfterTheBlockIsGone() {
		BeaconWatch watch = new BeaconWatch();
		watch.thrown(CARRIER, 1, 2, 3);
		watch.landed(1, 0, 3);
		watch.clear();
		watch.thrown(CARRIER, 1, 2, 3);
		assertFalse(watch.up());
	}

	@Test
	void theNextThrowIsANewOne() {
		BeaconWatch watch = new BeaconWatch();
		watch.thrown(CARRIER, 1, 2, 3);
		watch.landed(1, 0, 3);
		watch.clear();
		watch.thrown(NEXT_CARRIER, 4, 5, 6);
		assertEquals(BeaconWatch.State.THROWN, watch.state());
		assertEquals(4.0, watch.x());
	}
}
