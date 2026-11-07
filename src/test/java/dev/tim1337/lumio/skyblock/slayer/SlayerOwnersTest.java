package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whose boss a name tag belongs to, and which boss threw a Voidgloom beacon: recorded name tags and made-up positions. */
class SlayerOwnersTest {
	private static final long NOW = 100_000L;

	@Test
	void aSpawnedByTagMatchesItsOwnerAsAWholeNameIgnoringCase() {
		assertTrue(SlayerOwners.ownedBy("Spawned by: Steve123", "steve123"));
		assertTrue(SlayerOwners.ownedBy("Steve123's SLAYER BOSS", "Steve123"));
		assertFalse(SlayerOwners.ownedBy("Spawned by: Steve123", "Steve"), "a part of a longer name is someone else");
		assertFalse(SlayerOwners.ownedBy("Spawned by: xSteve123", "Steve123"));
		assertFalse(SlayerOwners.ownedBy("Steve123", "Steve123"), "a name alone is no owner tag");
		assertFalse(SlayerOwners.ownedBy("Spawned by: Steve123", ""));
		assertEquals("Friend_2", SlayerOwners.ownerOf("Spawned by: Friend_2"));
		assertEquals("Friend_2", SlayerOwners.ownerOf("Friend_2's SLAYER BOSS"));
		assertNull(SlayerOwners.ownerOf("☠ Voidgloom Seraph IV 45M❤"));
	}

	@Test
	void aTypedNameKeepsOnlyNameCharacters() {
		assertEquals("Friend_2", SlayerOwners.clean(" Friend_2 "));
		assertEquals("abc", SlayerOwners.clean("a.b-c§"));
		assertEquals(SlayerOwners.MOST_NAME_LENGTH, SlayerOwners.clean("x".repeat(30)).length());
	}

	@Test
	void theOwnBossBeaconAlerts() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0));
		assertEquals("Me", BeaconOwners.ownerOf(2, 64, 1, NOW, bosses));
	}

	@Test
	void anotherPlayersBossCloseByGetsItsOwnBeacon() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0), new BeaconOwners.Boss("Other", 5, 64, 0, 0));
		assertEquals("Other", BeaconOwners.ownerOf(5.5, 64, 0, NOW, bosses), "the beacon next to the other boss is theirs");
		// the other boss held a beacon a moment ago: it threw it, even if the beacon lands closer to the own boss
		List<BeaconOwners.Boss> held = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0), new BeaconOwners.Boss("Other", 5, 64, 0, NOW - 500));
		assertEquals("Other", BeaconOwners.ownerOf(1, 64, 0, NOW, held));
	}

	@Test
	void twoBossesAtTheSameDistanceCantBeToldApart() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", -3, 64, 0, 0), new BeaconOwners.Boss("Other", 3, 64, 0, 0));
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, bosses));
		List<BeaconOwners.Boss> bothHeld = List.of(new BeaconOwners.Boss("Me", -3, 64, 0, NOW - 100), new BeaconOwners.Boss("Other", 3, 64, 0, NOW - 100));
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, bothHeld));
	}

	@Test
	void anUnknownBeaconDoesNotAlert() {
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, List.of()), "no boss near");
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, List.of(new BeaconOwners.Boss("Me", 20, 64, 0, 0))), "too far from the only boss");
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, List.of(new BeaconOwners.Boss(null, 1, 64, 0, 0))), "a boss without a readable owner");
		assertNull(BeaconOwners.ownerOf(0, 64, 0, NOW, List.of(new BeaconOwners.Boss("Me", 20, 64, 0, NOW - 10_000))), "held long ago, and far");
	}
}
