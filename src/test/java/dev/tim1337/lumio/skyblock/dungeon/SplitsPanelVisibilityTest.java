package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Splits panel only shows in the Catacombs; in the HUD editor elsewhere only when its example is asked for, and never after the editor closes. */
class SplitsPanelVisibilityTest {
	@Test
	void inTheGameOnlyDuringARun() {
		// the private island, no editor: nothing at all
		assertEquals(DungeonSplitsFeature.Source.NONE, DungeonSplitsFeature.source(false, false, false));
		assertFalse(DungeonSplitsFeature.shownFor(false, false));
		// a run: its own rows
		assertEquals(DungeonSplitsFeature.Source.RUN, DungeonSplitsFeature.source(true, false, false));
		assertTrue(DungeonSplitsFeature.shownFor(true, false));
	}

	@Test
	void inTheEditorElsewhereOnlyWithAllPanels() {
		// the editor open on the private island, All panels off: the example is ready for the list's preview, but the panel is not shown
		assertEquals(DungeonSplitsFeature.Source.SAMPLE, DungeonSplitsFeature.source(false, false, true));
		assertFalse(DungeonSplitsFeature.shownFor(false, false));
		// All panels (or Simulate) on: shown with the example
		assertEquals(DungeonSplitsFeature.Source.SAMPLE, DungeonSplitsFeature.source(false, true, true));
		assertTrue(DungeonSplitsFeature.shownFor(false, true));
		// in a run the editor shows the run, not the example
		assertEquals(DungeonSplitsFeature.Source.RUN, DungeonSplitsFeature.source(true, true, true));
	}

	@Test
	void theExampleIsGoneOnceTheEditorCloses() {
		// after Done: no editor, no simulation, no run
		assertEquals(DungeonSplitsFeature.Source.NONE, DungeonSplitsFeature.source(false, false, false));
		assertFalse(DungeonSplitsFeature.shownFor(false, false));
	}
}
