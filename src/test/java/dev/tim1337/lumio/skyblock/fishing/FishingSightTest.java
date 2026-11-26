package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;

import org.junit.jupiter.api.Test;

/**
 * A sea creature found by its name tag alerts only once the player sees it (drawn, in view, nothing solid between): one behind a block gives
 * no alert and is told of when it comes into sight, once; the alerts read from chat are not touched by it.
 */
class FishingSightTest {
	private static final Creature THUNDER = SeaCreatures.caughtIn("You hear a massive rumble as Thunder emerges.");
	private static final long LATER = 120_000L;

	@Test
	void hiddenBehindABlockGivesNoAlert() {
		FishingAlerts.Seen seen = new FishingAlerts.Seen();
		for (long now = LATER; now < LATER + 10_000; now += 250) {
			assertFalse(seen.sighted(7, THUNDER, false, now), "hidden at " + now);
		}
	}

	@Test
	void visibleGivesOneAndTheSameCreatureNeverTwice() {
		FishingAlerts.Seen seen = new FishingAlerts.Seen();
		assertTrue(seen.sighted(7, THUNDER, true, LATER));
		assertFalse(seen.sighted(7, THUNDER, true, LATER + 250));
		// it went behind a wall and came out again: still the same creature
		assertFalse(seen.sighted(7, THUNDER, false, LATER + 500));
		assertFalse(seen.sighted(7, THUNDER, true, LATER + 750));
		// another one of the same kind is its own creature
		assertTrue(seen.sighted(8, THUNDER, true, LATER + 1000));
	}

	@Test
	void hiddenFirstThenSeenAlertsWhenItComesIntoSight() {
		FishingAlerts.Seen seen = new FishingAlerts.Seen();
		assertFalse(seen.sighted(9, THUNDER, false, LATER));
		assertFalse(seen.sighted(9, THUNDER, false, LATER + 250));
		assertTrue(seen.sighted(9, THUNDER, true, LATER + 500), "it came out from behind the block");
		assertFalse(seen.sighted(9, THUNDER, true, LATER + 750));
	}

	@Test
	void chatAlertsAreUnaffected() {
		FishingAlerts.Seen seen = new FishingAlerts.Seen();
		// nothing of the sight rule touches a share in chat: it alerts as before, whatever is seen
		assertFalse(seen.sighted(10, THUNDER, false, LATER));
		assertTrue(seen.chat("SomePlayer", THUNDER, LATER));
		assertFalse(seen.chat("SomePlayer", THUNDER, LATER + 1000), "the same share again within a minute, as before");
		assertTrue(seen.chat("SomePlayer", THUNDER, LATER + FishingAlerts.Seen.CHAT_MILLIS + 1));
	}
}
