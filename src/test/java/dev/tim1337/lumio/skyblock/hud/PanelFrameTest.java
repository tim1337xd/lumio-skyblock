package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

/** The item search's panel frame: only the grip moves a panel, the fold arrow, and a folded-up panel keeping its height. */
class PanelFrameTest {
	private static final int[] BOX = {100, 50, 160, 120};

	@Test
	void onlyTheGripPicksAPanelUp() {
		int gripX = BOX[0] + PanelFrame.PAD + PanelFrame.GRIP_WIDTH / 2;
		int middle = PanelFrame.middle(BOX);
		assertTrue(PanelFrame.onGrip(BOX, gripX, middle));
		// the rest of the header, the title and the body do not
		assertFalse(PanelFrame.onGrip(BOX, BOX[0] + BOX[2] / 2, middle));
		assertFalse(PanelFrame.onGrip(BOX, gripX, BOX[1] + PanelFrame.BAR + 10));
		assertFalse(PanelFrame.onGrip(BOX, gripX, BOX[1] - 1));
	}

	@Test
	void theFoldArrowIsAtTheHeadersRightEndButLeavesTheEdgeToPull() {
		int right = BOX[0] + BOX[2];
		int middle = PanelFrame.middle(BOX);
		assertTrue(PanelFrame.onCollapse(BOX, right - PanelFrame.PAD - PanelFrame.ARROW / 2, middle));
		assertFalse(PanelFrame.onCollapse(BOX, BOX[0] + BOX[2] / 2, middle));
		assertFalse(PanelFrame.onCollapse(BOX, right - PanelFrame.PAD - PanelFrame.ARROW / 2, BOX[1] + PanelFrame.BAR + 4));
		// the right edge still pulls the panel wider
		assertFalse(PanelFrame.onCollapse(BOX, right - 1, middle));
		assertEquals(PanelFrame.RIGHT, PanelFrame.edgesAt(BOX, right - 1, middle, true));
	}

	@Test
	void aFoldedUpPanelKeepsItsFullHeight() {
		assertEquals(BOX[3], PanelFrame.keptHeight(true, PanelFrame.BAR, BOX[3]));
		// an open one keeps what it was pulled to
		assertEquals(90, PanelFrame.keptHeight(false, 90, BOX[3]));
	}

	@Test
	void aFoldedUpPanelIsPulledOnlyAtItsSides() {
		int[] bar = {BOX[0], BOX[1], BOX[2], PanelFrame.BAR};
		int bottom = bar[1] + bar[3];
		int right = bar[0] + bar[2];
		// no height to pull: the bottom edge and the corner are not there
		assertEquals(PanelFrame.RIGHT, PanelFrame.edgesAt(bar, right - 1, bottom - 1, false));
		assertEquals(0, PanelFrame.edgesAt(bar, bar[0] + bar[2] / 2, bottom, false));
	}

	@Test
	void onlyPinnedAndRecentFold() {
		assertTrue(ItemPanelKind.PINNED.collapses());
		assertTrue(ItemPanelKind.RECENT.collapses());
		assertFalse(ItemPanelKind.ITEMS.collapses());
		assertFalse(ItemPanelKind.SEARCH.collapses());
		assertFalse(ItemPanelKind.DETAILS.collapses());
	}

	@Test
	void theFoldedUpPanelsAreSavedAndReadBack() {
		JsonArray saved = ItemPanelKind.collapsedToJson(EnumSet.of(ItemPanelKind.PINNED, ItemPanelKind.RECENT));
		Set<ItemPanelKind> read = EnumSet.noneOf(ItemPanelKind.class);
		ItemPanelKind.collapsedFromJson(saved, read);
		assertEquals(EnumSet.of(ItemPanelKind.PINNED, ItemPanelKind.RECENT), read);
		// unknown names, panels that do not fold and broken values are left out
		ItemPanelKind.collapsedFromJson(JsonParser.parseString("[\"items\", \"nothing\", 3, {}, \"recent\"]"), read);
		assertEquals(EnumSet.of(ItemPanelKind.RECENT), read);
		ItemPanelKind.collapsedFromJson(JsonParser.parseString("{}"), read);
		assertTrue(read.isEmpty());
		ItemPanelKind.collapsedFromJson(null, read);
		assertTrue(read.isEmpty());
	}
}
