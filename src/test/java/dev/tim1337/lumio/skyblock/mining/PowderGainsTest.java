package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.ProfitTracker.Range;
import dev.tim1337.lumio.skyblock.mining.PowderTab.Powder;

import org.junit.jupiter.api.Test;

/** The powder tracker's gains: from chat and from the tab value, never twice, never a loss; the farmed time with its pause; the chests. */
class PowderGainsTest {
	private static final Powder GEM = Powder.GEMSTONE;

	@Test
	void theChatLinesOfAPowderChestAreRead() {
		PowderGains.Line line = PowderGains.parse("You received +1,234 Gemstone Powder.");
		assertEquals(GEM, line.powder());
		assertEquals(1_234, line.amount());
		assertEquals(Powder.MITHRIL, PowderGains.parse("You received +80 ᠅ Mithril Powder").powder());
		PowderGains.Line listed = PowderGains.parse("    Glacite Powder x2,500");
		assertEquals(Powder.GLACITE, listed.powder());
		assertEquals(2_500, listed.amount());
		assertNull(PowderGains.parse("You received +3 Gemstone Powder from nothing I know"));
		assertNull(PowderGains.parse("[MVP] Someone: Gemstone Powder x999999"), "a player's line is no gain");
		assertNull(PowderGains.parse("You uncovered a treasure chest!"));
	}

	@Test
	void theFirstTabValueIsNoGainAndARiseIs() {
		PowderGains gains = new PowderGains();
		assertEquals(0, gains.fromTab(GEM, 500_000, 0), "what was there before is not made now");
		assertEquals(1_200, gains.fromTab(GEM, 501_200, 1_000));
		assertEquals(0, gains.fromTab(GEM, 501_200, 2_000), "the same value again");
		assertEquals(1_200, gains.gained(GEM, Range.SESSION));
		assertEquals(1_200, gains.gained(GEM, Range.LIFETIME));
		assertEquals(0, gains.gained(Powder.MITHRIL, Range.SESSION), "each powder has its own numbers");
		assertEquals(GEM, gains.lastGained());
	}

	@Test
	void spendingPowderIsNeverALoss() {
		PowderGains gains = new PowderGains();
		gains.fromTab(GEM, 500_000, 0);
		gains.fromTab(GEM, 501_000, 1_000);
		assertEquals(0, gains.fromTab(GEM, 201_000, 2_000), "spent in the Heart of the Mountain");
		assertEquals(1_000, gains.gained(GEM, Range.SESSION), "nothing taken off");
		assertEquals(700, gains.fromTab(GEM, 201_700, 3_000), "and the next rise counts from the new value");
		assertEquals(1_700, gains.gained(GEM, Range.SESSION));
	}

	@Test
	void aGainReportedByChatAndTabCountsOnce() {
		PowderGains gains = new PowderGains();
		gains.fromTab(GEM, 100_000, 0);
		gains.fromChat(GEM, 2_000, 1_000);
		assertEquals(2_000, gains.gained(GEM, Range.SESSION), "counted at once, from chat");
		assertEquals(0, gains.fromTab(GEM, 102_000, 2_500), "the tab's rise is the same powder");
		assertEquals(2_000, gains.gained(GEM, Range.SESSION));
		// mined powder on top of a chest's in the same rise: only what chat did not report is new
		gains.fromChat(GEM, 1_000, 3_000);
		assertEquals(300, gains.fromTab(GEM, 103_300, 4_000));
		assertEquals(3_300, gains.gained(GEM, Range.SESSION));
	}

	@Test
	void aChatGainTheTabNeverShowedDoesNotSwallowALaterRise() {
		PowderGains gains = new PowderGains();
		gains.fromTab(GEM, 100_000, 0);
		gains.fromChat(GEM, 2_000, 1_000);
		long later = 1_000 + PowderGains.CREDIT_MS + 1;
		assertEquals(500, gains.fromTab(GEM, 100_500, later), "long after the chat line the rise is a gain of its own");
		assertEquals(2_500, gains.gained(GEM, Range.SESSION));
	}

	@Test
	void timeCountsBetweenGainsAndStopsForAPause() {
		PowderGains gains = new PowderGains();
		gains.fromTab(GEM, 0, 0);
		gains.fromTab(GEM, 100, 10_000);
		assertEquals(0, gains.timeMillis(GEM, Range.SESSION), "the first gain starts the clock");
		gains.fromTab(GEM, 200, 40_000);
		assertEquals(30_000, gains.timeMillis(GEM, Range.SESSION));
		assertFalse(gains.paused(GEM, 60_000));
		assertTrue(gains.paused(GEM, 40_000 + PowderGains.PAUSE_AFTER_MS + 1));
		gains.fromTab(GEM, 300, 40_000 + PowderGains.PAUSE_AFTER_MS + 5_000);
		assertEquals(30_000, gains.timeMillis(GEM, Range.SESSION), "a pause is not farmed time");
		gains.fromTab(GEM, 400, 40_000 + PowderGains.PAUSE_AFTER_MS + 25_000);
		assertEquals(50_000, gains.timeMillis(GEM, Range.SESSION));
		assertEquals(400 / (50_000 / 3_600_000.0), gains.gainedPerHour(GEM, Range.SESSION), 1e-6);
	}

	@Test
	void chestsAreCountedWithTheirPowder() {
		PowderGains gains = new PowderGains();
		gains.chestOpened(1_000);
		gains.fromChat(GEM, 3_000, 1_050);
		gains.fromChat(Powder.MITHRIL, 600, 1_060);
		gains.fromChat(GEM, 1_000, 1_070);
		assertEquals(1, gains.chests(GEM, Range.SESSION), "one chest, however many lines");
		assertEquals(1, gains.chests(Powder.MITHRIL, Range.SESSION));
		assertEquals(4_000.0, gains.perChest(GEM, Range.SESSION));
		gains.chestOpened(30_000);
		gains.fromChat(GEM, 2_000, 30_100);
		assertEquals(2, gains.chests(GEM, Range.SESSION));
		assertEquals(3_000.0, gains.perChest(GEM, Range.SESSION));
		// powder long after a chest is not that chest's
		gains.fromChat(GEM, 50, 30_000 + PowderGains.CHEST_LINES_MS + 1);
		assertEquals(3_000.0, gains.perChest(GEM, Range.SESSION));
		assertEquals(6_050, gains.gained(GEM, Range.SESSION));
	}

	@Test
	void aResetStartsARangeOverAndSavedValuesComeBack() {
		PowderGains gains = new PowderGains();
		gains.fromTab(GEM, 0, 0);
		gains.fromTab(GEM, 900, 1_000);
		PowderGains other = new PowderGains();
		other.load(gains.save());
		assertEquals(900, other.gained(GEM, Range.LIFETIME), "all time comes back from the file");
		assertEquals(0, other.gained(GEM, Range.SESSION), "the session is this game's own");
		gains.reset(Range.SESSION);
		assertEquals(0, gains.gained(GEM, Range.SESSION));
		assertEquals(900, gains.gained(GEM, Range.LIFETIME));
	}
}
