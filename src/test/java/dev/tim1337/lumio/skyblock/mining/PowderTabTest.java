package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The powders read from the tab list: with and without thousands separators, a missing widget keeping the last values, and junk. */
class PowderTabTest {
	@Test
	void readsTheWidgetWithSeparators() {
		PowderTab tab = new PowderTab();
		assertTrue(tab.read(List.of("Powders:", " Mithril: 1,234,567", " Gemstone: 845,210", " Glacite: 92,480")));
		assertEquals(1_234_567, tab.amount(PowderTab.Powder.MITHRIL));
		assertEquals(845_210, tab.amount(PowderTab.Powder.GEMSTONE));
		assertEquals(92_480, tab.amount(PowderTab.Powder.GLACITE));
	}

	@Test
	void readsTheWidgetWithoutSeparators() {
		PowderTab tab = new PowderTab();
		tab.read(List.of("Mithril: 1234567", "Gemstone: 0"));
		assertEquals(1_234_567, tab.amount(PowderTab.Powder.MITHRIL));
		assertEquals(0, tab.amount(PowderTab.Powder.GEMSTONE));
		assertEquals(PowderTab.UNKNOWN, tab.amount(PowderTab.Powder.GLACITE), "never read");
	}

	@Test
	void aMissingWidgetKeepsTheLastValues() {
		PowderTab tab = new PowderTab();
		assertFalse(tab.anyKnown(), "nothing before the first read");
		tab.read(List.of("Mithril: 500", "Gemstone: 600", "Glacite: 700"));
		assertFalse(tab.read(List.of("Area: Hub", "Profile: Kiwi")), "no widget: no change");
		assertEquals(500, tab.amount(PowderTab.Powder.MITHRIL));
		assertTrue(tab.anyKnown());
		assertFalse(tab.read(List.of("Mithril: 500")), "the same value is no change");
	}

	@Test
	void textThatIsNoAmountIsSkipped() {
		assertEquals(PowderTab.UNKNOWN, PowderTab.amount("1.2M"));
		assertEquals(PowderTab.UNKNOWN, PowderTab.amount(""));
		assertEquals(PowderTab.UNKNOWN, PowderTab.amount(","));
		assertEquals(PowderTab.UNKNOWN, PowderTab.amount("12345678901234567890"));
		PowderTab tab = new PowderTab();
		tab.read(List.of("Mithril: 300", "Mithril: lots"));
		assertEquals(300, tab.amount(PowderTab.Powder.MITHRIL));
	}
}
