package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The pickaxe ability's cooldown: fixed at the use, kept across server switches, set again by whatever the server says. */
class AbilityClockTest {
	private static final long BASE = 120_000L;
	/** When the tests start: the clock takes 0 for "never used". */
	private static final long T = 1_000_000L;
	private final List<String> log = new ArrayList<>();

	private AbilityClock clock() {
		AbilityClock clock = new AbilityClock(log::add);
		clock.lore(BASE);
		return clock;
	}

	@Test
	void usedThenAServerSwitchThenThePlayerListSetsIt() {
		AbilityClock clock = clock();
		clock.used(T, false);
		assertEquals(BASE, clock.left(T));
		// joining a Glacite Mineshaft: nothing changes the running clock, it counts on in real time
		assertEquals(BASE - 30_000L, clock.left(T + 30_000L));
		// the new server's list says more is left than counted: it wins
		clock.tab(T + 35_000L, 100_000L, false);
		assertEquals(100_000L, clock.left(T + 35_000L));
		// and less, too
		clock.tab(T + 60_000L, 40_000L, false);
		assertEquals(40_000L, clock.left(T + 60_000L));
		// a few seconds behind is no correction: the list is a little late
		clock.tab(T + 62_000L, 40_000L, false);
		assertEquals(38_000L, clock.left(T + 62_000L));
		assertTrue(log.stream().anyMatch(line -> line.startsWith("player list: 100 s left")), log.toString());
	}

	@Test
	void theListRightAfterAUseIsNotRead() {
		AbilityClock clock = clock();
		clock.used(T, false);
		// it still shows "Available" from before, and while the ability is active it is not read
		clock.tab(T + 500L, -1L, false);
		clock.tab(T + 10_000L, 5_000L, true);
		assertEquals(BASE - 10_000L, clock.left(T + 10_000L));
	}

	@Test
	void onCooldownCorrectsItBothWays() {
		AbilityClock clock = clock();
		clock.used(T, false);
		clock.onCooldownFor(T + 10_000L, 45);
		assertEquals(45_000L, clock.left(T + 10_000L));
		clock.onCooldownFor(T + 20_000L, 90);
		assertEquals(90_000L, clock.left(T + 20_000L));
		assertTrue(log.contains("on cooldown message: 90 s left"), log.toString());
	}

	@Test
	void skyMallShortensItOnlyWhereItCounts() {
		AbilityClock inMines = clock();
		inMines.used(T, true);
		assertEquals(Math.round(BASE * AbilityClock.SKY_MALL), inMines.left(T));
		AbilityClock inMineshaft = clock();
		inMineshaft.used(T, false);
		assertEquals(BASE, inMineshaft.left(T));
	}

	@Test
	void theBaseIsLearnedWithoutTheReductionOfThen() {
		AbilityClock clock = new AbilityClock(log::add);
		clock.used(T, true);
		// ready after 96 s with Sky Mall: the base is 120 s
		clock.available(T + 96_000L);
		assertEquals(0, clock.left(T + 96_000L));
		assertEquals(120_000L, clock.baseMillis);
		clock.used(T + 200_000L, false);
		assertEquals(120_000L, clock.left(T + 200_000L));
	}

	@Test
	void readyStaysReadyWhileTheListLags() {
		AbilityClock clock = clock();
		clock.used(T, false);
		clock.available(T + BASE);
		// the list still shows the last seconds for a moment
		clock.tab(T + BASE + 1_000L, 4_000L, false);
		assertEquals(0, clock.left(T + BASE + 1_000L));
	}

	@Test
	void aProfileChangeForgetsIt() {
		AbilityClock clock = clock();
		clock.used(T, false);
		clock.reset("another profile");
		assertEquals(0, clock.left(T + 1_000L));
	}
}
