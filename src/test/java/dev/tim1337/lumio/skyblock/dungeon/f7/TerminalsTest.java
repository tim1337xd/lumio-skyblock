package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The six terminals: what to click, in which order and how often. */
class TerminalsTest {
	private static Terminals.Cell pane(int slot, String color) {
		return new Terminals.Cell(slot, color + "_stained_glass_pane", 1, "", false);
	}

	private static Terminals.Solution solve(Terminals.Kind kind, String title, List<Terminals.Cell> cells) {
		return Terminals.solve(kind, title, cells, Set.of(), Terminals.RubixMode.FEWEST, null);
	}

	@Test
	void titles() {
		assertEquals(Terminals.Kind.NUMBERS, Terminals.kindOf("Click in order!"));
		assertEquals(Terminals.Kind.STARTS_WITH, Terminals.kindOf("What starts with: 'B'?"));
		assertEquals(Terminals.Kind.SELECT, Terminals.kindOf("Select all the LIGHT BLUE items!"));
		assertNull(Terminals.kindOf("Large Chest"));
	}

	@Test
	void panesAndNumbers() {
		List<Terminals.Cell> cells = List.of(pane(11, "red"), pane(12, "lime"), pane(13, "red"), pane(0, "black"));
		assertEquals(List.of(11, 13), solve(Terminals.Kind.PANES, "Correct all the panes!", cells).order());
		List<Terminals.Cell> numbers = List.of(new Terminals.Cell(10, "red_stained_glass_pane", 3, "3", false),
			new Terminals.Cell(11, "red_stained_glass_pane", 1, "1", false), new Terminals.Cell(12, "lime_stained_glass_pane", 2, "2", false),
			new Terminals.Cell(13, "red_stained_glass_pane", 4, "4", false));
		Terminals.Solution order = solve(Terminals.Kind.NUMBERS, "Click in order!", numbers);
		assertEquals(List.of(11, 10, 13), order.order());
		assertEquals(0, order.place(11));
	}

	@Test
	void rubixFewestAndLeftOnly() {
		List<Terminals.Cell> cells = new ArrayList<>();
		cells.add(pane(12, "orange"));
		cells.add(pane(13, "red"));
		cells.add(pane(14, "red"));
		cells.add(pane(0, "black"));
		// red is one click from orange; everything to red is the fewest
		assertEquals("red", Terminals.rubixTarget(cells, Terminals.RubixMode.FEWEST));
		Terminals.Solution fewest = solve(Terminals.Kind.RUBIX, "Change all to same color!", cells);
		assertEquals(List.of(12), fewest.order());
		assertEquals(-1, fewest.clicksAt(12));
		Terminals.Solution left = Terminals.solve(Terminals.Kind.RUBIX, "Change all to same color!", cells, Set.of(), Terminals.RubixMode.LEFT_ONLY, "red");
		assertEquals(4, left.clicksAt(12));
		assertEquals(2, Terminals.turns("orange", "green", Terminals.RubixMode.FEWEST));
		assertEquals(-2, Terminals.turns("orange", "blue", Terminals.RubixMode.FEWEST));
		assertEquals(3, Terminals.turns("orange", "blue", Terminals.RubixMode.LEFT_ONLY));
	}

	@Test
	void startsWithAndSelect() {
		List<Terminals.Cell> cells = List.of(new Terminals.Cell(10, "bone", 1, "Bone", false), new Terminals.Cell(11, "bread", 1, "Bread", true),
			new Terminals.Cell(12, "apple", 1, "Apple", false), new Terminals.Cell(13, "book", 1, "Book", false));
		Terminals.Solution startsWith = Terminals.solve(Terminals.Kind.STARTS_WITH, "What starts with: 'B'?", cells, Set.of(13), Terminals.RubixMode.FEWEST,
			null);
		assertEquals(List.of(10), startsWith.order());
		List<Terminals.Cell> colors = List.of(new Terminals.Cell(10, "blue_wool", 1, "Blue Wool", false),
			new Terminals.Cell(11, "light_blue_wool", 1, "Light Blue Wool", false), new Terminals.Cell(12, "lapis_lazuli", 1, "Lapis Lazuli", false),
			new Terminals.Cell(13, "blue_dye", 1, "Blue Dye", true));
		assertEquals(List.of(10, 12), solve(Terminals.Kind.SELECT, "Select all the BLUE items!", colors).order());
		assertEquals(List.of(11), solve(Terminals.Kind.SELECT, "Select all the LIGHT BLUE items!", colors).order());
	}

	@Test
	void melody() {
		List<Terminals.Cell> cells = List.of(new Terminals.Cell(3, "magenta_stained_glass_pane", 1, "", false),
			new Terminals.Cell(12, "lime_stained_glass_pane", 1, "", false), new Terminals.Cell(16, "lime_terracotta", 1, "", false),
			new Terminals.Cell(25, "lime_terracotta", 1, "", false));
		assertEquals(List.of(16), solve(Terminals.Kind.MELODY, "Click the button on time!", cells).order());
		List<Terminals.Cell> early = List.of(cells.get(0), new Terminals.Cell(11, "lime_stained_glass_pane", 1, "", false), cells.get(2));
		assertTrue(solve(Terminals.Kind.MELODY, "Click the button on time!", early).isEmpty());
	}
}
