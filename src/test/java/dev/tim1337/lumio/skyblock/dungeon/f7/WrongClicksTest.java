package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Block wrong clicks": per terminal and device, a wrong click is blocked, a right one passes, an unsure state blocks nothing, and the
 * override key lets everything through. A blocked click is a plain "yes, hold it back": there is nothing in the answer that could be sent.
 */
class WrongClicksTest {
	private static final int CHEST = 54;

	private static Terminals.Cell pane(int slot, String color) {
		return new Terminals.Cell(slot, color + "_stained_glass_pane", 1, "", false);
	}

	private static Terminals.Cell item(int slot, String item, String name) {
		return new Terminals.Cell(slot, item, 1, name, false);
	}

	private static Terminals.Solution solve(Terminals.Kind kind, String title, List<Terminals.Cell> cells) {
		return Terminals.solve(kind, title, cells, Set.of(), Terminals.RubixMode.FEWEST, kind == Terminals.Kind.RUBIX
			? Terminals.rubixTarget(cells, Terminals.RubixMode.FEWEST) : null);
	}

	private static boolean blocks(Terminals.Kind kind, Terminals.Solution solution, int slot) {
		return WrongClicks.terminal(true, false, kind, solution, true, slot, CHEST, 0);
	}

	/** What every terminal shares: off, the key held, not loaded, no terminal, and a slot outside the terminal block nothing. */
	private static void neverWhenUnsure(Terminals.Kind kind, Terminals.Solution solution, int wrongSlot) {
		assertTrue(blocks(kind, solution, wrongSlot));
		assertFalse(WrongClicks.terminal(false, false, kind, solution, true, wrongSlot, CHEST, 0), "switched off");
		assertFalse(WrongClicks.terminal(true, true, kind, solution, true, wrongSlot, CHEST, 0), "the override key");
		assertFalse(WrongClicks.terminal(true, false, kind, solution, false, wrongSlot, CHEST, 0), "menu not loaded");
		assertFalse(WrongClicks.terminal(true, false, null, solution, true, wrongSlot, CHEST, 0), "no terminal");
		assertFalse(WrongClicks.terminal(true, false, kind, solution, true, CHEST + 3, CHEST, 0), "the player's inventory");
		assertFalse(WrongClicks.terminal(true, false, kind, solution, true, -999, CHEST, 0), "outside the menu");
		if (kind != Terminals.Kind.MELODY) {
			assertFalse(blocks(kind, Terminals.Solution.NONE, wrongSlot), "no solution known");
		}
	}

	@Test
	void panes() {
		Terminals.Solution solution = solve(Terminals.Kind.PANES, "Correct all the panes!", List.of(pane(11, "red"), pane(12, "lime"), pane(13, "red")));
		assertFalse(blocks(Terminals.Kind.PANES, solution, 11));
		assertFalse(blocks(Terminals.Kind.PANES, solution, 13));
		neverWhenUnsure(Terminals.Kind.PANES, solution, 12);
	}

	@Test
	void numbersInOrder() {
		List<Terminals.Cell> cells = List.of(new Terminals.Cell(10, "red_stained_glass_pane", 3, "3", false),
			new Terminals.Cell(11, "red_stained_glass_pane", 1, "1", false), new Terminals.Cell(12, "lime_stained_glass_pane", 2, "2", false),
			new Terminals.Cell(13, "red_stained_glass_pane", 4, "4", false));
		Terminals.Solution solution = solve(Terminals.Kind.NUMBERS, "Click in order!", cells);
		assertEquals(List.of(11, 10, 13), solution.order());
		assertFalse(blocks(Terminals.Kind.NUMBERS, solution, 11), "the next number");
		assertTrue(blocks(Terminals.Kind.NUMBERS, solution, 10), "a later number");
		assertTrue(blocks(Terminals.Kind.NUMBERS, solution, 12), "a number already done");
		// one right click on its way: the number after it passes, the one sent does not pass a second time
		assertFalse(WrongClicks.terminal(true, false, Terminals.Kind.NUMBERS, solution, true, 10, CHEST, 1));
		assertTrue(WrongClicks.terminal(true, false, Terminals.Kind.NUMBERS, solution, true, 11, CHEST, 1));
		assertTrue(WrongClicks.terminal(true, false, Terminals.Kind.NUMBERS, solution, true, 13, CHEST, 1));
		neverWhenUnsure(Terminals.Kind.NUMBERS, solution, 13);
	}

	@Test
	void startsWithAndSelect() {
		List<Terminals.Cell> letters = List.of(item(10, "bread", "Bread"), item(11, "apple", "Apple"), item(12, "bone", "Bone"));
		Terminals.Solution starts = solve(Terminals.Kind.STARTS_WITH, "What starts with: 'B'?", letters);
		assertFalse(blocks(Terminals.Kind.STARTS_WITH, starts, 10));
		assertFalse(blocks(Terminals.Kind.STARTS_WITH, starts, 12));
		neverWhenUnsure(Terminals.Kind.STARTS_WITH, starts, 11);
		List<Terminals.Cell> colors = List.of(item(10, "red_wool", "Red Wool"), item(11, "blue_wool", "Blue Wool"), item(12, "red_stained_glass", "Red Stained Glass"));
		Terminals.Solution select = solve(Terminals.Kind.SELECT, "Select all the RED items!", colors);
		assertFalse(blocks(Terminals.Kind.SELECT, select, 10));
		neverWhenUnsure(Terminals.Kind.SELECT, select, 11);
	}

	@Test
	void sameColor() {
		List<Terminals.Cell> cells = new ArrayList<>();
		int[] slots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
		for (int i = 0; i < slots.length; i++) {
			cells.add(pane(slots[i], i == 0 ? "orange" : "yellow"));
		}
		Terminals.Solution solution = solve(Terminals.Kind.RUBIX, "Change all to same color!", cells);
		assertEquals(List.of(12), solution.order());
		assertFalse(blocks(Terminals.Kind.RUBIX, solution, 12), "the pane of another color");
		neverWhenUnsure(Terminals.Kind.RUBIX, solution, 13);
	}

	@Test
	void melody() {
		// the marked column is 3; the second row's pane stands in it, the third row's does not
		List<Terminals.Cell> cells = List.of(pane(3, "magenta"), pane(12, "lime"), item(16, "lime_terracotta", ""), pane(23, "lime"),
			item(25, "lime_terracotta", ""));
		Terminals.Solution solution = solve(Terminals.Kind.MELODY, "Click the button on time!", cells);
		assertEquals(List.of(16), solution.order());
		assertFalse(blocks(Terminals.Kind.MELODY, solution, 16), "the button of the row that is in place");
		assertTrue(blocks(Terminals.Kind.MELODY, solution, 25), "the button of another row");
		// no pane in the marked column: every button is the wrong moment
		assertTrue(blocks(Terminals.Kind.MELODY, Terminals.Solution.NONE, 16));
		neverWhenUnsure(Terminals.Kind.MELODY, solution, 25);
	}

	@Test
	void simonSaysButtons() {
		SimonSays simon = new SimonSays();
		simon.shown(5);
		simon.shown(9);
		simon.shown(2);
		assertFalse(WrongClicks.simonButton(true, false, simon.size(), simon.place(5), 0), "the next button");
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(9), 0), "a later button");
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(7), 0), "a button not in the order");
		// one right press on its way: the button after it passes
		assertFalse(WrongClicks.simonButton(true, false, simon.size(), simon.place(9), 1));
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(5), 1));
		// off, the key held, and nothing shown yet block nothing
		assertFalse(WrongClicks.simonButton(false, false, simon.size(), simon.place(7), 0));
		assertFalse(WrongClicks.simonButton(true, true, simon.size(), simon.place(7), 0));
		assertFalse(WrongClicks.simonButton(true, false, 0, -1, 0));
		simon.pressed(5);
		assertFalse(WrongClicks.simonButton(true, false, simon.size(), simon.place(9), 0));
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(5), 0), "pressed already");
	}

	@Test
	void simonSaysSkipIsNotBlocked() {
		// the first phase after the start button: the corrected order decides, not the order the lanterns showed
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.shown(4);
		simon.shown(8);
		assertEquals(List.of(8, 4), simon.remaining());
		assertFalse(WrongClicks.simonButton(true, false, simon.size(), simon.place(8), 0));
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(4), 0));
		simon.shown(1);
		assertEquals(List.of(8, 1), simon.remaining());
		assertFalse(WrongClicks.simonButton(true, false, simon.size(), simon.place(8), 0));
		assertTrue(WrongClicks.simonButton(true, false, simon.size(), simon.place(4), 0), "dropped by the skip");
	}

	@Test
	void simonSaysStartButton() {
		int most = 4;
		assertFalse(WrongClicks.simonStart(true, false, false, 0, most), "the first press, before any first phase");
		assertFalse(WrongClicks.simonStart(true, false, true, 3, most));
		assertTrue(WrongClicks.simonStart(true, false, true, 4, most), "the fifth press");
		assertFalse(WrongClicks.simonStart(true, false, false, 9, most), "only in the first phase");
		assertFalse(WrongClicks.simonStart(false, false, true, 9, most));
		assertFalse(WrongClicks.simonStart(true, true, true, 9, most));
		assertTrue(WrongClicks.simonStart(true, false, true, 1, 1));
	}

	@Test
	void arrowAlign() {
		int[] clicks = new int[Devices.FRAMES];
		clicks[3] = 2;
		assertFalse(WrongClicks.arrow(true, false, clicks, 3, 0), "two clicks left");
		assertFalse(WrongClicks.arrow(true, false, clicks, 3, 1), "one on its way, one left");
		assertTrue(WrongClicks.arrow(true, false, clicks, 3, 2), "both on their way: a third would turn it past");
		assertTrue(WrongClicks.arrow(true, false, clicks, 4, 0), "already right");
		assertFalse(WrongClicks.arrow(true, false, null, 4, 0), "no layout known");
		assertFalse(WrongClicks.arrow(true, false, clicks, 99, 0));
		assertFalse(WrongClicks.arrow(false, false, clicks, 4, 0));
		assertFalse(WrongClicks.arrow(true, true, clicks, 4, 0));
	}

	@Test
	void lightsLevers() {
		assertTrue(WrongClicks.lever(true, false, true), "a lever that is on");
		assertFalse(WrongClicks.lever(true, false, false), "a lever that is off");
		assertFalse(WrongClicks.lever(false, false, true));
		assertFalse(WrongClicks.lever(true, true, true));
	}

	@Test
	void clicksOnTheirWayAndTheCounter() {
		assertEquals(1, WrongClicks.settle(2, 1));
		assertEquals(0, WrongClicks.settle(1, 3));
		assertEquals(2, WrongClicks.settle(2, -1));
		int before = WrongClicks.blocked();
		String line = WrongClicks.note("Simon Says", "button 7 is not the next one");
		assertEquals(before + 1, WrongClicks.blocked());
		assertTrue(line.contains("#" + (before + 1)) && line.contains("Simon Says") && line.contains("button 7"));
	}
}
