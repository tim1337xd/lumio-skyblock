package dev.tim1337.lumio.skyblock.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A cocoon's label always shows something, and its bar runs from the accent color to red in the last second. */
class CocoonLookTest {
	private static final int ACCENT = 0x0A84FF;
	private static final int RED = 0xFF3B30;

	@Test
	void whatIsAskedForIsShown() {
		assertEquals(CocoonLook.NAME | CocoonLook.BAR, CocoonLook.kept(CocoonLook.NAME | CocoonLook.TIME | CocoonLook.BAR, CocoonLook.NAME | CocoonLook.BAR));
		assertEquals(CocoonLook.TIME, CocoonLook.kept(CocoonLook.NAME | CocoonLook.TIME, CocoonLook.TIME));
	}

	@Test
	void theLastPartThatIsOnStaysOn() {
		assertEquals(CocoonLook.TIME, CocoonLook.kept(CocoonLook.TIME, 0));
		assertEquals(CocoonLook.BAR, CocoonLook.kept(CocoonLook.BAR, 0));
		assertEquals(CocoonLook.NAME, CocoonLook.kept(CocoonLook.NAME, 0));
	}

	@Test
	void nothingAtAllComesBackAsTheName() {
		assertEquals(CocoonLook.NAME, CocoonLook.kept(0, 0));
	}

	@Test
	void theBarHasTheAccentColorUntilTheLastSecond() {
		assertEquals(ACCENT, CocoonLook.barRgb(ACCENT, 6.0));
		assertEquals(ACCENT, CocoonLook.barRgb(ACCENT, 1.0));
	}

	@Test
	void theBarIsRedAtTheEnd() {
		assertEquals(RED, CocoonLook.barRgb(ACCENT, 0.0));
		assertEquals(RED, CocoonLook.barRgb(ACCENT, -2.0));
	}

	@Test
	void theBarRunsToRedInBetween() {
		int middle = CocoonLook.barRgb(0x000000, 0.5);
		assertEquals(0x80, middle >> 16 & 0xFF);
	}

	@Test
	void theLineHasWhatIsShown() {
		assertEquals("Enderman · 4.2 s", CocoonLook.line("Enderman", "4.2 s", true, true));
		assertEquals("Enderman", CocoonLook.line("Enderman", "4.2 s", true, false));
		assertEquals("4.2 s", CocoonLook.line("Enderman", "4.2 s", false, true));
		assertEquals("", CocoonLook.line("Enderman", "4.2 s", false, false));
	}

	@Test
	void numbersHaveOneDecimal() {
		assertEquals("4.2", CocoonLook.decimal(4.2));
		assertEquals("0.0", CocoonLook.decimal(0.0));
		assertEquals("6.0", CocoonLook.decimal(5.96));
	}
}
