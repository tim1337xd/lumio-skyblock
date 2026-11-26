package dev.tim1337.lumio.skyblock.profit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The corpse panel only in a Glacite Mineshaft: shown there, hidden at once after leaving, shown again in the next one with its numbers kept (the panel's numbers live in its tracker, which showing and hiding never touch). */
class CorpsePanelShownTest {
	private static final long SHOW_MILLIS = 15_000L;

	@Test
	void shownInAMineshaftHiddenAfterLeavingShownAgainInTheNext() {
		CorpsePanelShown panel = new CorpsePanelShown();

		panel.at(true, 0, SHOW_MILLIS);
		assertTrue(panel.shown(1_000), "entering a mineshaft shows it");
		panel.corpse(20_000, SHOW_MILLIS);
		assertTrue(panel.shown(30_000), "after a corpse");

		panel.at(false, 31_000, SHOW_MILLIS);
		assertFalse(panel.shown(31_000), "hidden at once after leaving, though the corpse's time is not over");
		panel.at(false, 40_000, SHOW_MILLIS);
		assertFalse(panel.shown(40_000));

		panel.at(true, 100_000, SHOW_MILLIS);
		assertTrue(panel.shown(100_500), "the next mineshaft shows it again");
	}

	@Test
	void notSittingThereInBetween() {
		CorpsePanelShown panel = new CorpsePanelShown();
		panel.at(true, 0, SHOW_MILLIS);
		panel.at(true, 5_000, SHOW_MILLIS);
		assertFalse(panel.shown(SHOW_MILLIS + 1), "staying in the mineshaft does not show it again without a corpse");
	}

	@Test
	void neverOutsideAMineshaft() {
		CorpsePanelShown panel = new CorpsePanelShown();
		panel.at(false, 0, SHOW_MILLIS);
		panel.corpse(1_000, SHOW_MILLIS);
		assertFalse(panel.shown(2_000));
	}
}
