package dev.tim1337.lumio.skyblock.dungeon.f7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * An offline terminal for practice: made up here, with the same rules the server's terminals follow, and clicked only on the player's screen.
 * Nothing of it ever reaches a server. Plain logic, tested without the game.
 */
public final class TerminalSim {
	/** The terminals that can be practised (the melody is about timing and is left out). */
	public static final List<Terminals.Kind> KINDS = List.of(Terminals.Kind.PANES, Terminals.Kind.NUMBERS, Terminals.Kind.RUBIX, Terminals.Kind.STARTS_WITH,
		Terminals.Kind.SELECT);
	private static final int ROW = 9;
	private static final String PANE = "_stained_glass_pane";
	private static final String FILLER = "black" + PANE;
	private static final String[][] ITEMS = {{"apple", "Apple"}, {"arrow", "Arrow"}, {"bone", "Bone"}, {"bread", "Bread"}, {"book", "Book"},
		{"carrot", "Carrot"}, {"coal", "Coal"}, {"cookie", "Cookie"}, {"diamond", "Diamond"}, {"egg", "Egg"}, {"emerald", "Emerald"},
		{"feather", "Feather"}, {"flint", "Flint"}, {"gold_ingot", "Gold Ingot"}, {"iron_ingot", "Iron Ingot"}, {"leather", "Leather"},
		{"melon_slice", "Melon Slice"}, {"paper", "Paper"}, {"potato", "Potato"}, {"redstone", "Redstone Dust"}, {"stick", "Stick"},
		{"sugar", "Sugar"}, {"string", "String"}, {"wheat", "Wheat"}};
	private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
		"blue", "brown", "green", "red", "black"};
	private static final String[] COLORED = {"_wool", "_stained_glass", "_dye", "_concrete"};
	private static final int MOST_NUMBERS = 14;

	private final Terminals.Kind kind;
	private final String title;
	private final int rows;
	private final List<Terminals.Cell> cells;

	private TerminalSim(Terminals.Kind kind, String title, int rows, List<Terminals.Cell> cells) {
		this.kind = kind;
		this.title = title;
		this.rows = rows;
		this.cells = cells;
	}

	/** @return a new terminal of a kind, with its inner area filled at random and the frame around it */
	public static TerminalSim create(Terminals.Kind kind, Random random) {
		return switch (kind) {
			case PANES -> area(kind, kind.title(), 5, 1, 2, 3, 5, random, slot -> pane(slot, random.nextInt(3) == 0 ? "lime" : "red"));
			case NUMBERS -> numbers(random);
			case RUBIX -> area(kind, kind.title(), 5, 1, 3, 3, 3, random,
				slot -> pane(slot, Terminals.RUBIX_ORDER.get(random.nextInt(Terminals.RUBIX_ORDER.size()))));
			case STARTS_WITH -> startsWith(random);
			default -> select(random);
		};
	}

	private interface Filler {
		Terminals.Cell cell(int slot);
	}

	private static TerminalSim area(Terminals.Kind kind, String title, int rows, int top, int left, int height, int width, Random random, Filler filler) {
		List<Terminals.Cell> cells = new ArrayList<>();
		for (int slot = 0; slot < rows * ROW; slot++) {
			int row = slot / ROW;
			int column = slot % ROW;
			boolean inside = row >= top && row < top + height && column >= left && column < left + width;
			cells.add(inside ? filler.cell(slot) : pane(slot, "black"));
		}
		return new TerminalSim(kind, title, rows, cells);
	}

	private static Terminals.Cell pane(int slot, String color) {
		return new Terminals.Cell(slot, color + PANE, 1, "", false);
	}

	private static TerminalSim numbers(Random random) {
		List<Integer> numbers = new ArrayList<>();
		for (int i = 1; i <= MOST_NUMBERS; i++) {
			numbers.add(i);
		}
		Collections.shuffle(numbers, random);
		int[] next = {0};
		return area(Terminals.Kind.NUMBERS, Terminals.Kind.NUMBERS.title(), 4, 1, 1, 2, 7, random, slot -> {
			int number = numbers.get(next[0]++);
			return new Terminals.Cell(slot, "red" + PANE, number, String.valueOf(number), false);
		});
	}

	private static TerminalSim startsWith(Random random) {
		String[] pick = ITEMS[random.nextInt(ITEMS.length)];
		String letter = pick[1].substring(0, 1).toUpperCase(Locale.ROOT);
		return area(Terminals.Kind.STARTS_WITH, "What starts with: '" + letter + "'?", 5, 1, 1, 3, 7, random, slot -> {
			String[] item = ITEMS[random.nextInt(ITEMS.length)];
			return new Terminals.Cell(slot, item[0], 1, item[1], false);
		});
	}

	private static TerminalSim select(Random random) {
		String target = COLORS[random.nextInt(COLORS.length)];
		String title = "Select all the " + target.replace('_', ' ').toUpperCase(Locale.ROOT) + " items!";
		return area(Terminals.Kind.SELECT, title, 6, 1, 1, 4, 7, random, slot -> {
			String color = random.nextInt(4) == 0 ? target : COLORS[random.nextInt(COLORS.length)];
			String suffix = COLORED[random.nextInt(COLORED.length)];
			return new Terminals.Cell(slot, color + suffix, 1, nameOf(color, suffix), false);
		});
	}

	private static String nameOf(String color, String suffix) {
		StringBuilder name = new StringBuilder();
		for (String word : (color + suffix).split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return name.toString();
	}

	/** One click of the player on a slot, with the terminal's own rules: a wrong click changes nothing. @param right whether it was the right button */
	public void click(int slot, boolean right) {
		if (slot < 0 || slot >= cells.size()) {
			return;
		}
		Terminals.Cell cell = cells.get(slot);
		if (cell.item().equals(FILLER)) {
			return;
		}
		Terminals.Solution solution = solution();
		Terminals.Cell changed = switch (kind) {
			case PANES -> pane(slot, cell.item().equals("red" + PANE) ? "lime" : "red");
			case NUMBERS -> solution.place(slot) == 0 ? new Terminals.Cell(slot, "lime" + PANE, cell.count(), cell.name(), false) : null;
			case RUBIX -> rubixTurn(cell, right);
			default -> solution.place(slot) >= 0 ? new Terminals.Cell(slot, cell.item(), cell.count(), cell.name(), true) : null;
		};
		if (changed != null) {
			cells.set(slot, changed);
		}
	}

	private static Terminals.Cell rubixTurn(Terminals.Cell cell, boolean right) {
		String color = cell.item().substring(0, cell.item().length() - PANE.length());
		int index = Terminals.RUBIX_ORDER.indexOf(color);
		if (index < 0) {
			return null;
		}
		int size = Terminals.RUBIX_ORDER.size();
		return pane(cell.slot(), Terminals.RUBIX_ORDER.get(Math.floorMod(index + (right ? -1 : 1), size)));
	}

	/** @return what is left to click (the color terminal toward the color with the fewest clicks) */
	public Terminals.Solution solution() {
		return Terminals.solve(kind, title, cells, Set.of(), Terminals.RubixMode.FEWEST, null);
	}

	/** @return whether the terminal is done (the color terminal: all one color) */
	public boolean solved() {
		return solution().isEmpty();
	}

	public Terminals.Kind kind() {
		return kind;
	}

	public String title() {
		return title;
	}

	public int rows() {
		return rows;
	}

	public List<Terminals.Cell> cells() {
		return cells;
	}
}
