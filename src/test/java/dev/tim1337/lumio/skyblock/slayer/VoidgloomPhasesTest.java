package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Voidgloom Seraph's phases from sequences of name tags as the server writes them, with colors and symbols: every hit shield and every part
 * between them is a phase, the bar is the shield's hits or this phase's health, and a moment without a name tag keeps everything.
 */
class VoidgloomPhasesTest {
	private static final String TIER_IV = "Voidgloom Seraph IV";
	private static final double MOST = 50_000_000;

	/** A fight fed with name tags as the boss reader does: the hits, or the health as a share of the most seen. */
	private static final class Fight {
		final VoidgloomPhases phases = new VoidgloomPhases();
		double most;

		Fight(String title) {
			phases.tier(BossNameText.tier(title));
		}

		/** @return the view after this name tag; null stands for a tick without the name tag */
		VoidgloomPhases.View tag(String name) {
			if (name != null) {
				double health = BossNameText.health(name);
				most = Math.max(most, health);
				phases.read(BossNameText.hits(name), health >= 0 && most > 0 ? health / most : -1);
			}
			return phases.view();
		}
	}

	private static void assertPhase(VoidgloomPhases.View view, int step, boolean hits) {
		assertEquals(step, view.step(), "phase");
		assertEquals(hits, view.hits(), "kind of phase " + step);
	}

	@Test
	void theHitsAreReadInEveryFormat() {
		assertEquals(90, BossNameText.hits("§5☠ §bVoidgloom Seraph §5IV §d§l90 Hits"));
		assertEquals(90, BossNameText.hits("☠ Voidgloom Seraph IV §d§l90§r §dHits"));
		assertEquals(1, BossNameText.hits("☠ Voidgloom Seraph IV 1 Hit"));
		assertEquals(42, BossNameText.hits("☠ Voidgloom Seraph IV 42Hits"));
		assertEquals(7, BossNameText.hits("☠ Voidgloom Seraph IV ✦ 7 ✦ Hits"));
		assertEquals(15, BossNameText.hits("§d§l15 HITS"), "a line of its own, in capitals");
		assertEquals(-1, BossNameText.hits("☠ Voidgloom Seraph IV 45M❤"));
		assertEquals(45_000_000, BossNameText.health("§5☠ §bVoidgloom Seraph §5IV §a45M§c❤"));
		assertEquals(4, BossNameText.tier("Voidgloom Seraph IV"));
		assertEquals(3, BossNameText.tier("§bVoidgloom Seraph §5III"));
		assertEquals(0, BossNameText.tier("Voidgloom Seraph"));
	}

	@Test
	void aTierFourFightGoesThroughSixPhases() {
		Fight fight = new Fight(TIER_IV);
		// spawned behind its first shield
		var view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §d§l100 Hits");
		assertPhase(view, 1, true);
		assertEquals(6, view.steps());
		assertEquals(1f, view.bar(), 1e-6f);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §d§l60 Hits");
		assertEquals(60, view.hitsLeft());
		assertEquals(100, view.hitsTotal());
		assertEquals(0.6f, view.bar(), 1e-6f);
		// the shield broke: its health shows, the first damage phase from full
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §a50M§c❤");
		assertPhase(view, 2, false);
		assertEquals(1f, view.bar(), 1e-3f);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §e41.7M§c❤");
		assertEquals(0.5f, view.bar(), 0.01f);
		// a hit dips below two thirds a moment before the shield shows: still the same phase
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §e33.1M§c❤");
		assertPhase(view, 2, false);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §d§l100 Hits");
		assertPhase(view, 3, true);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §e33M§c❤");
		assertPhase(view, 4, false);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §d§l100 Hits");
		assertPhase(view, 5, true);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §d§l12 Hits");
		assertEquals(0.12f, view.bar(), 1e-6f);
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §c16.5M§c❤");
		assertPhase(view, 6, false);
		assertEquals(6, view.steps());
		view = fight.tag("§5☠ §bVoidgloom Seraph §5IV §c8.3M§c❤");
		assertEquals(0.5f, view.bar(), 0.01f);
	}

	@Test
	void aMomentWithoutTheNameTagKeepsTheShield() {
		Fight fight = new Fight(TIER_IV);
		fight.tag("☠ Voidgloom Seraph IV 100 Hits");
		var before = fight.tag("☠ Voidgloom Seraph IV 70 Hits");
		var without = fight.tag(null);
		assertEquals(before, without, "nothing read: the shield and its bar stay");
		assertPhase(without, 1, true);
		assertEquals(0.7f, without.bar(), 1e-6f);
		var after = fight.tag("☠ Voidgloom Seraph IV 64 Hits");
		assertPhase(after, 1, true);
		assertEquals(100, after.hitsTotal());
	}

	@Test
	void aFastKillSkippingADamagePhase() {
		Fight fight = new Fight(TIER_IV);
		fight.tag("☠ Voidgloom Seraph IV 100 Hits");
		fight.tag("☠ Voidgloom Seraph IV 50M❤");
		fight.tag("☠ Voidgloom Seraph IV 100 Hits");
		var view = fight.tag("☠ Voidgloom Seraph IV 2 Hits");
		assertPhase(view, 3, true);
		// the shield broke and the next came up before any damage was read: the hits jump back up
		view = fight.tag("☠ Voidgloom Seraph IV 100 Hits");
		assertPhase(view, 5, true);
		assertEquals(1f, view.bar(), 1e-6f);
		view = fight.tag("☠ Voidgloom Seraph IV 12M❤");
		assertPhase(view, 6, false);
	}

	@Test
	void aFightJoinedLateStartsAtItsPhase() {
		Fight fight = new Fight(TIER_IV);
		fight.most = MOST;
		var view = fight.tag("☠ Voidgloom Seraph IV 20M❤");
		assertPhase(view, 4, false);
		view = fight.tag("☠ Voidgloom Seraph IV 100 Hits");
		assertPhase(view, 5, true);
	}

	@Test
	void anUnknownTierCountsAsTheyCome() {
		Fight fight = new Fight("Voidgloom Seraph II");
		var view = fight.tag("☠ Voidgloom Seraph II 30 Hits");
		assertPhase(view, 1, true);
		assertEquals(0, view.steps(), "no total while the tier's shields are not known");
		view = fight.tag("☠ Voidgloom Seraph II 15M❤");
		assertPhase(view, 2, false);
		assertEquals(1f, view.bar(), 1e-6f, "the whole health, without the parts of a known tier");
		view = fight.tag("☠ Voidgloom Seraph II 30 Hits");
		assertPhase(view, 3, true);
		view = fight.tag("☠ Voidgloom Seraph II 4M❤");
		assertPhase(view, 4, false);
		assertEquals(0, view.steps());
	}

	@Test
	void theBarIsNeverMissing() {
		Fight fight = new Fight(TIER_IV);
		assertFalse(fight.phases.started());
		var view = fight.tag(null);
		assertTrue(view.bar() >= 0f && view.step() >= 1, "even before the first reading there is a bar and a phase");
		for (String name : new String[]{"☠ Voidgloom Seraph IV 100 Hits", null, "☠ Voidgloom Seraph IV 0 Hits", "☠ Voidgloom Seraph IV 50M❤", null,
			"☠ Voidgloom Seraph IV 100 Hits"}) {
			view = fight.tag(name);
			assertTrue(view.bar() >= 0f && view.bar() <= 1f, "a bar from 0 to 1 after " + name);
		}
	}
}
