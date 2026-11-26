package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The one queue of Lumio's titles: one at a time, several at once each shown briefly, an urgent one at once. */
class TitleOrderTest {
	private static final long LONG = 3_000;

	@Test
	void aTitleShowsAtOnceWhenNothingShows() {
		TitleOrder<String> order = new TitleOrder<>();
		TitleOrder.Showing<String> first = order.offer("Hyperion", LONG, false, 0);
		assertEquals("Hyperion", first.title());
		assertEquals(LONG, first.millis());
		// long after it is over the next one shows at once too
		assertNotNull(order.offer("Necron's Handle", LONG, false, LONG + 1));
	}

	@Test
	void severalAtOnceAreQueuedAndEachShowsBriefly() {
		TitleOrder<String> order = new TitleOrder<>();
		assertNotNull(order.offer("one", LONG, false, 0));
		assertNull(order.offer("two", LONG, false, 100), "waits instead of taking the first one's place");
		assertNull(order.offer("three", LONG, false, 200));
		assertEquals(2, order.waiting());
		// the first one stays for its brief moment, not its whole time
		assertNull(order.next(TitleOrder.BRIEF_MILLIS - 1));
		TitleOrder.Showing<String> two = order.next(TitleOrder.BRIEF_MILLIS);
		assertEquals("two", two.title());
		assertEquals(TitleOrder.BRIEF_MILLIS, two.millis(), "brief while another waits behind it");
		assertNull(order.next(2 * TitleOrder.BRIEF_MILLIS - 1));
		TitleOrder.Showing<String> three = order.next(2 * TitleOrder.BRIEF_MILLIS);
		assertEquals("three", three.title());
		assertEquals(LONG, three.millis(), "the last one keeps its whole time");
		assertNull(order.next(10 * LONG));
	}

	@Test
	void aTitleThatWasSeenLongEnoughGivesWayAtOnce() {
		TitleOrder<String> order = new TitleOrder<>();
		order.offer("one", LONG, false, 0);
		assertNull(order.offer("two", LONG, false, 2_000));
		assertEquals("two", order.next(2_000).title());
	}

	@Test
	void anUrgentTitleShowsAtOnceAndWhatWaitsFollowsIt() {
		TitleOrder<String> order = new TitleOrder<>();
		order.offer("drop", LONG, false, 0);
		assertNull(order.offer("another drop", LONG, false, 100));
		TitleOrder.Showing<String> urgent = order.offer("Use your ultimate now", 1_500, true, 200);
		assertEquals("Use your ultimate now", urgent.title());
		assertEquals(1_500, urgent.millis());
		assertNull(order.next(1_000), "the urgent one is not cut short by what waits");
		assertEquals("another drop", order.next(1_700).title());
	}

	@Test
	void theSameTitleWaitsOnlyOnceAndTheQueueIsBounded() {
		TitleOrder<String> order = new TitleOrder<>();
		order.offer("showing", LONG, false, 0);
		order.offer("same", LONG, false, 1);
		order.offer("same", LONG, false, 2);
		assertEquals(1, order.waiting());
		for (int i = 0; i < 20; i++) {
			order.offer("drop " + i, LONG, false, 3 + i);
		}
		assertEquals(TitleOrder.MOST_WAITING, order.waiting());
		order.clear();
		assertEquals(0, order.waiting());
		assertNotNull(order.offer("after another world", LONG, false, 30));
	}
}
