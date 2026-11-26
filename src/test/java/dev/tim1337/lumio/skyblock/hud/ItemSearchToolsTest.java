package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The search's tools: the calculator, and the lists of recent items and searches. */
class ItemSearchToolsTest {
	@Test
	void calculator() {
		assertEquals(192d, SearchMath.evaluate("64*3"));
		assertEquals(240_000d, SearchMath.evaluate("1.2m/5"));
		assertEquals(14d, SearchMath.evaluate("2 + 3 × 4"));
		assertEquals(20d, SearchMath.evaluate("(2+3)x4"));
		assertEquals(1.5e9, SearchMath.evaluate("1.5b"));
		assertEquals(-2d, SearchMath.evaluate("-(1+1)"));
		assertEquals(2.5d, SearchMath.evaluate("10÷4"));
		assertEquals(2_500d, SearchMath.evaluate("2,5k"));
		assertEquals("= 240,000", SearchMath.shown(240_000d));
		assertEquals("= 0.25", SearchMath.shown(0.25d));
		assertEquals("= 0.5", SearchMath.shown(0.5d));
	}

	@Test
	void searchesAreNoCalculations() {
		assertTrue(Double.isNaN(SearchMath.evaluate("64")));
		assertTrue(Double.isNaN(SearchMath.evaluate("aspect")));
		assertTrue(Double.isNaN(SearchMath.evaluate("x")));
		assertTrue(Double.isNaN(SearchMath.evaluate("3 /")));
		assertTrue(Double.isNaN(SearchMath.evaluate("1/0")));
		assertTrue(Double.isNaN(SearchMath.evaluate("((1+2)")));
		assertTrue(Double.isNaN(SearchMath.evaluate("(".repeat(30) + "1" + ")".repeat(30))));
		assertTrue(Double.isNaN(SearchMath.evaluate("hyperion 5")));
	}

	@Test
	void recentListsAndHistory() {
		RecentList recent = new RecentList(3);
		recent.note("A");
		recent.note("B");
		recent.note("C");
		recent.note("A");
		assertEquals(List.of("A", "C", "B"), recent.items());
		recent.note("D");
		assertEquals(List.of("D", "A", "C"), recent.items());
		recent.note(" ");
		assertEquals(3, recent.size());
		// the arrow keys: up goes older, down newer, back to what is typed
		assertEquals(0, recent.step(-1, true));
		assertEquals(2, recent.step(2, true));
		assertEquals(-1, recent.step(0, false));
		assertEquals(-1, recent.step(-1, false));
		RecentList read = new RecentList(3);
		read.load(recent.toJson());
		assertEquals(recent.items(), read.items());
		read.clear();
		assertEquals(0, read.size());
	}
}
