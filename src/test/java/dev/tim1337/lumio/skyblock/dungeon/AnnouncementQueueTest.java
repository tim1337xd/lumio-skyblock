package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Each announcement once per run, right away or 1 to 2 seconds after the last one, the texts Odin's, the placeholders filled. */
class AnnouncementQueueTest {
	private long now;
	/** What the queue rolls for a gap, from 0 to its span (1 to 2 seconds in all). */
	private long roll = 400;

	private AnnouncementQueue queue() {
		now = 10_000;
		return new AnnouncementQueue(() -> now, span -> roll);
	}

	@Test
	void eachEventOncePerRun() {
		AnnouncementQueue queue = queue();
		assertTrue(queue.add("mimic", AnnouncementQueue.MIMIC));
		assertFalse(queue.add("mimic", AnnouncementQueue.MIMIC));
		assertEquals(AnnouncementQueue.MIMIC, queue.poll().text());
		// sent: the same event again in this run is not taken
		assertFalse(queue.add("mimic", AnnouncementQueue.MIMIC));
		queue.clear();
		assertTrue(queue.add("mimic", AnnouncementQueue.MIMIC));
	}

	@Test
	void oneEventGoesOutInTheSameTick() {
		AnnouncementQueue queue = queue();
		queue.add("prince", AnnouncementQueue.PRINCE);
		assertEquals(AnnouncementQueue.PRINCE, queue.poll().text());
	}

	@Test
	void threeAtOnceGoOneToTwoSecondsApart() {
		AnnouncementQueue queue = queue();
		queue.add("mimic", AnnouncementQueue.MIMIC);
		queue.add("prince", AnnouncementQueue.PRINCE);
		queue.add("bat", AnnouncementQueue.BAT);
		assertEquals(AnnouncementQueue.MIMIC, queue.poll().text(), "the first right away");
		assertNull(queue.poll());
		now += AnnouncementQueue.GAP_LEAST_MILLIS + roll - 1;
		assertNull(queue.poll(), "not before its gap");
		now += 1;
		assertEquals(AnnouncementQueue.PRINCE, queue.poll().text(), "1.4 s after the first");
		roll = 1_000;
		now += AnnouncementQueue.GAP_LEAST_MILLIS + 400;
		assertEquals(AnnouncementQueue.BAT, queue.poll().text());
	}

	@Test
	void theGapsStayBetweenOneAndTwoSeconds() {
		for (long rolled : new long[]{0, AnnouncementQueue.GAP_MOST_MILLIS - AnnouncementQueue.GAP_LEAST_MILLIS}) {
			roll = rolled;
			AnnouncementQueue queue = queue();
			queue.add("a", "one");
			queue.add("b", "two");
			queue.poll();
			now += AnnouncementQueue.GAP_LEAST_MILLIS + rolled;
			assertEquals("two", queue.poll().text());
			assertTrue(AnnouncementQueue.GAP_LEAST_MILLIS + rolled <= AnnouncementQueue.GAP_MOST_MILLIS);
		}
	}

	@Test
	void theQueueIsClearedOnWorldChange() {
		AnnouncementQueue queue = queue();
		queue.add("a", "one");
		queue.add("b", "two");
		queue.clear();
		assertEquals(0, queue.waiting());
		assertNull(queue.poll());
	}

	@Test
	void theSameTextIsNotQueuedTwice() {
		AnnouncementQueue queue = queue();
		assertTrue(queue.add("leap1", "Leaped to Kiwi!"));
		assertFalse(queue.add("leap2", "Leaped to Kiwi!"));
		assertEquals(1, queue.waiting());
	}

	@Test
	void theFixedTextsAreOdins() {
		assertEquals("Mimic Killed!", AnnouncementQueue.MIMIC);
		assertEquals("Prince Killed!", AnnouncementQueue.PRINCE);
		assertEquals("Bat Killed!", AnnouncementQueue.BAT);
		assertEquals("Leaped to Kiwi!", AnnouncementQueue.fill("", AnnouncementQueue.DEFAULT_LEAP, "name", "Kiwi"));
	}

	@Test
	void placeholdersAreFilled() {
		assertEquals("270 score reached! (6m 42s)", AnnouncementQueue.fill("", AnnouncementQueue.DEFAULT_270, "score", "271", "time", "6m 42s", "floor", "F7"));
		assertEquals("F7: 301 at 7m", AnnouncementQueue.fill("{floor}: {score} at {time}", AnnouncementQueue.DEFAULT_300, "score", "301", "time", "7m",
			"floor", "F7"));
		assertEquals("Mage Kiwi in Temple", AnnouncementQueue.fill("{class} {name} in {room}", AnnouncementQueue.DEFAULT_LEAP, "name", "Kiwi", "class",
			"Mage", "room", "Temple"));
		assertEquals(AnnouncementQueue.MOST_TEXT, AnnouncementQueue.fill("x".repeat(300), "", "").length());
	}
}
