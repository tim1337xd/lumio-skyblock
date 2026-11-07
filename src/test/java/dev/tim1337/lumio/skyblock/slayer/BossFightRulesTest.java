package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The phase count, which items are a boss's drops, and a beacon's owner and life, from sample entities. */
class BossFightRulesTest {
	private static final float CLOSE = 0.0001f;
	private static final long NOW = 100_000L;

	@Test
	void thePhaseCountOfShields() {
		BossPhase hits = BossPhase.ofShields(new VoidgloomPhases.View(1, 6, true, 90, 100, 0.9f), 45_000_000, 45_000_000);
		assertEquals("Phase 1/6", hits.count());
		assertTrue(hits.hits());
		assertEquals("90", hits.value());
		assertEquals("100", hits.total());
		BossPhase damage = BossPhase.ofShields(new VoidgloomPhases.View(4, 6, false, 0, 0, 0.5f), 12_400_000, 23_000_000);
		assertEquals("Phase 4/6", damage.count());
		assertFalse(damage.hits());
		assertEquals("12.40M", damage.value());
		assertEquals(0.5f, damage.bar(), CLOSE);
	}

	@Test
	void thePhaseCountOfThirds() {
		assertEquals("Phase 1/3", BossPhase.ofHealth(100, 100, true).count());
		assertEquals("Phase 1/3", BossPhase.ofHealth(70, 100, true).count());
		assertEquals("Phase 2/3", BossPhase.ofHealth(60, 100, true).count());
		assertEquals("Phase 3/3", BossPhase.ofHealth(10, 100, true).count());
		assertEquals("Phase 3/3", BossPhase.ofHealth(0, 100, true).count());
		assertEquals("Phase 1/1", BossPhase.ofHealth(50, 100, false).count());
	}

	@Test
	void aBossWithoutHealthYetHasAnEmptyBar() {
		assertEquals(0f, BossPhase.ofHealth(0, 0, true).bar(), CLOSE);
	}

	@Test
	void aDropIsNamedCloseAndSoon() {
		assertTrue(BossDrops.counts(3.0, 1_000L, true));
		assertTrue(BossDrops.counts(8.0, 10_000L, true));
		assertTrue(BossDrops.counts(1.0, -1_500L, true), "it can appear just before the chat line about the death");
	}

	@Test
	void aDropTooFarOrTooLateOrUnnamedStaysPlain() {
		assertFalse(BossDrops.counts(12.5, 1_000L, true));
		assertFalse(BossDrops.counts(3.0, 15_001L, true));
		assertFalse(BossDrops.counts(3.0, -8_500L, true));
		assertFalse(BossDrops.counts(3.0, 1_000L, false), "a random item nearby");
	}

	@Test
	void theDropListKnowsSlayerDrops() {
		assertTrue(BossDrops.onList("Revenant Flesh"));
		assertTrue(BossDrops.onList("revenant flesh x12"));
		assertTrue(BossDrops.onList("Null Sphere 8"));
		assertTrue(BossDrops.onList("Enchanted Book (Smite VI)"));
		assertFalse(BossDrops.onList("Rotten Flesh"));
		assertFalse(BossDrops.onList("Diamond Sword"));
	}

	@Test
	void rareDropsGetTheirName() {
		assertFalse(BossDrops.labeled(0));
		assertFalse(BossDrops.labeled(1));
		assertTrue(BossDrops.labeled(2));
		assertTrue(BossDrops.labeled(5));
		assertTrue(BossDrops.labeled(BossDrops.UNKNOWN_RARITY));
	}

	@Test
	void aBeaconBelongsToTheBossHoldingIt() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, NOW - 500L), new BeaconOwners.Boss("Friend", 2, 64, 0, 0L));
		assertEquals("Me", BeaconOwners.ownerOf(1, 65, 0, NOW, bosses));
	}

	@Test
	void aBeaconByTheOtherPlayersBossIsTheirs() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0L), new BeaconOwners.Boss("Friend", 20, 64, 0, NOW - 100L));
		assertEquals("Friend", BeaconOwners.ownerOf(21, 65, 0, NOW, bosses));
	}

	@Test
	void aLoneBossOwnsABeaconFartherAway() {
		List<BeaconOwners.Boss> one = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0L));
		assertEquals("Me", BeaconOwners.ownerOf(12, 64, 0, NOW, one));
		assertNull(BeaconOwners.ownerOf(20, 64, 0, NOW, one));
	}

	@Test
	void twoBossesAboutAsNearCannotBeToldApart() {
		List<BeaconOwners.Boss> bosses = List.of(new BeaconOwners.Boss("Me", 0, 64, 0, 0L), new BeaconOwners.Boss("Friend", 4, 64, 0, 0L));
		assertNull(BeaconOwners.ownerOf(2, 64, 0, NOW, bosses));
	}

	@Test
	void aBeaconLandsAndStaysUntilItIsDestroyed() {
		BeaconWatch watch = new BeaconWatch();
		assertFalse(watch.up());
		watch.thrown(1, 10.5, 70.0, 20.5);
		assertEquals(BeaconWatch.State.THROWN, watch.state());
		watch.noneThrown(100);
		assertTrue(watch.searching(), "the thrown entity is gone: its block is looked for");
		watch.landed(10, 64, 20);
		assertTrue(watch.placed());
		assertEquals(64, watch.blockY());
		watch.noneThrown(500);
		assertTrue(watch.placed(), "it stays as long as the block is there");
		watch.clear();
		assertFalse(watch.up());
	}

	@Test
	void aBeaconThatNeverLandsIsForgotten() {
		BeaconWatch watch = new BeaconWatch();
		watch.thrown(1, 0, 70, 0);
		watch.noneThrown(100);
		watch.noneThrown(100 + BeaconWatch.LAND_TICKS);
		assertTrue(watch.searching());
		watch.noneThrown(100 + BeaconWatch.LAND_TICKS + 1);
		assertFalse(watch.up());
	}

	@Test
	void aNewThrowTakesOver() {
		BeaconWatch watch = new BeaconWatch();
		watch.thrown(1, 0, 70, 0);
		watch.noneThrown(100);
		watch.landed(0, 64, 0);
		watch.thrown(2, 5, 70, 5);
		assertEquals(BeaconWatch.State.THROWN, watch.state());
		assertEquals(5.0, watch.x(), CLOSE);
	}
}
