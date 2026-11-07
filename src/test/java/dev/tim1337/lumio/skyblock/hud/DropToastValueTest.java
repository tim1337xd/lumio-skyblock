package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.skyblock.data.ProfitTracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a drop of a known value of at least "Show from" gets a toast; a drop without a known price never does, and is counted as before. */
class DropToastValueTest {
	private static final double MILLION = 1_000_000;

	@Test
	void byValueOnly() {
		assertFalse(DropToastFeature.worthAToast(50_000, MILLION));
		assertTrue(DropToastFeature.worthAToast(MILLION, MILLION));
		assertTrue(DropToastFeature.worthAToast(20_000_000, MILLION));
	}

	@Test
	void anUnknownPriceNeverGetsAToast() {
		assertFalse(DropToastFeature.worthAToast(0, MILLION));
		assertFalse(DropToastFeature.worthAToast(0, 100_000), "not even with the lowest Show from");
	}

	@Test
	void anUnknownPriceIsStillCountedByTheProfitTracker() {
		ProfitTracker tracker = new ProfitTracker();
		// a pet without a known price: counted as an item, worth nothing until a price is known
		tracker.add("UNKNOWN_TEST_PET;4", "Unknown Pet", 1);
		var entry = tracker.bucket(ProfitTracker.Range.SESSION).items.get("UNKNOWN_TEST_PET;4");
		assertEquals(1, entry.count);
	}
}
