package dev.tim1337.lumio.skyblock.dungeon.f7;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The six Floor 7 terminals: which one a menu is (from its title), and which slots the player still has to click, in what order and how often.
 * The menu is given as plain cells (item id, count, name, whether it shines), so all of it is tested without the game. It only says what to click;
 * the clicks are the player's.
 */
public final class Terminals {
	/** The terminals, by the title of their menu. */
	public enum Kind {
		PANES("Correct all the panes!"), RUBIX("Change all to same color!"), NUMBERS("Click in order!"), STARTS_WITH("What starts with"),
		SELECT("Select all the"), MELODY("Click the button on time!");

		private final String title;

		Kind(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}
	}

	/** How the color terminal is solved: the fewest clicks (left and right), or left clicks only. */
	public enum RubixMode {
		FEWEST, LEFT_ONLY
	}

	/** One slot of the menu: the item's id ("red_stained_glass_pane"), its count, its name without colors, and whether it shines. */
	public record Cell(int slot, String item, int count, String name, boolean shines) {
	}

	/**
	 * What to click: the slots in order, and for each how many clicks it still needs (negative: right clicks, for the color terminal).
	 *
	 * @param order the slots to click, the next one first
	 * @param clicks the clicks each slot needs, in the same order
	 */
	public record Solution(List<Integer> order, List<Integer> clicks) {
		public static final Solution NONE = new Solution(List.of(), List.of());

		/** @return the clicks a slot still needs (negative for right clicks), or 0 when it is not part of the solution */
		public int clicksAt(int slot) {
			int index = order.indexOf(slot);
			return index < 0 ? 0 : clicks.get(index);
		}

		/** @return where a slot stands in the order (0 is next), or -1 */
		public int place(int slot) {
			return order.indexOf(slot);
		}

		public boolean isEmpty() {
			return order.isEmpty();
		}
	}

	private static final Kind[] KINDS = Kind.values();
	private static final Pattern STARTS_WITH = Pattern.compile("^What starts with: '(\\w)'\\?$");
	private static final Pattern SELECT = Pattern.compile("^Select all the ([\\w ]{1,20}) items!$");
	private static final String PANE = "_stained_glass_pane";
	private static final String FILLER = "black" + PANE;
	/** The colors of the color terminal, in the order a left click turns them. */
	static final List<String> RUBIX_ORDER = List.of("orange", "yellow", "green", "blue", "red");
	/** The melody terminal: the column marker, the moving pane and the buttons. */
	private static final String MELODY_COLUMN = "magenta" + PANE;
	private static final String MELODY_MOVER = "lime" + PANE;
	private static final String MELODY_BUTTON = "lime_terracotta";
	private static final int ROW = 9;

	private Terminals() {
	}

	/** @return the terminal a menu title belongs to, or null */
	public static Kind kindOf(String title) {
		for (Kind kind : KINDS) {
			if (title.startsWith(kind.title)) {
				return kind;
			}
		}
		return null;
	}

	/**
	 * Solves a terminal.
	 *
	 * @param clicked the slots the player already clicked in this menu (for the terminals where the server marks a click a moment later)
	 * @param rubixTarget the color the color terminal is solved toward, or null to choose the one with the fewest clicks
	 */
	public static Solution solve(Kind kind, String title, List<Cell> cells, Set<Integer> clicked, RubixMode mode, String rubixTarget) {
		return switch (kind) {
			case PANES -> each(cells, cell -> cell.item.equals("red" + PANE));
			case NUMBERS -> numbers(cells);
			case RUBIX -> rubix(cells, mode, rubixTarget == null ? rubixTarget(cells, mode) : rubixTarget);
			case STARTS_WITH -> startsWith(title, cells, clicked);
			case SELECT -> select(title, cells, clicked);
			case MELODY -> melody(cells);
		};
	}

	private interface CellTest {
		boolean wanted(Cell cell);
	}

	private static Solution each(List<Cell> cells, CellTest test) {
		List<Integer> order = new ArrayList<>();
		List<Integer> clicks = new ArrayList<>();
		for (Cell cell : cells) {
			if (test.wanted(cell)) {
				order.add(cell.slot);
				clicks.add(1);
			}
		}
		return new Solution(order, clicks);
	}

	/** The red panes, lowest number first. */
	private static Solution numbers(List<Cell> cells) {
		List<Cell> red = new ArrayList<>();
		for (Cell cell : cells) {
			if (cell.item.equals("red" + PANE)) {
				red.add(cell);
			}
		}
		red.sort((a, b) -> Integer.compare(a.count, b.count));
		List<Integer> order = new ArrayList<>();
		List<Integer> clicks = new ArrayList<>();
		for (Cell cell : red) {
			order.add(cell.slot);
			clicks.add(1);
		}
		return new Solution(order, clicks);
	}

	/** @return the pane color of a cell in the color terminal ("blue"), or null for the frame and everything else */
	private static String rubixColor(Cell cell) {
		if (!cell.item.endsWith(PANE) || cell.item.equals(FILLER)) {
			return null;
		}
		String color = cell.item.substring(0, cell.item.length() - PANE.length());
		return RUBIX_ORDER.contains(color) ? color : null;
	}

	/** @return the color the panes are turned to with the fewest clicks */
	public static String rubixTarget(List<Cell> cells, RubixMode mode) {
		String best = null;
		int bestClicks = Integer.MAX_VALUE;
		for (String target : RUBIX_ORDER) {
			int total = 0;
			for (Cell cell : cells) {
				String color = rubixColor(cell);
				if (color != null) {
					total += Math.abs(turns(color, target, mode));
				}
			}
			if (total < bestClicks) {
				bestClicks = total;
				best = target;
			}
		}
		return best;
	}

	/** @return the clicks from one color to another: forward (left clicks), or backward (negative, right clicks) where that is shorter */
	static int turns(String from, String to, RubixMode mode) {
		int size = RUBIX_ORDER.size();
		int forward = Math.floorMod(RUBIX_ORDER.indexOf(to) - RUBIX_ORDER.indexOf(from), size);
		if (mode == RubixMode.FEWEST && forward > size / 2) {
			return forward - size;
		}
		return forward;
	}

	private static Solution rubix(List<Cell> cells, RubixMode mode, String target) {
		List<Integer> order = new ArrayList<>();
		List<Integer> clicks = new ArrayList<>();
		if (target == null) {
			return Solution.NONE;
		}
		for (Cell cell : cells) {
			String color = rubixColor(cell);
			int needed = color == null ? 0 : turns(color, target, mode);
			if (needed != 0) {
				order.add(cell.slot);
				clicks.add(needed);
			}
		}
		return new Solution(order, clicks);
	}

	private static Solution startsWith(String title, List<Cell> cells, Set<Integer> clicked) {
		Matcher matcher = STARTS_WITH.matcher(title);
		if (!matcher.matches()) {
			return Solution.NONE;
		}
		String letter = matcher.group(1).toLowerCase(Locale.ROOT);
		return each(cells, cell -> !cell.shines && !clicked.contains(cell.slot) && !cell.item.equals(FILLER)
			&& cell.name.toLowerCase(Locale.ROOT).startsWith(letter));
	}

	private static Solution select(String title, List<Cell> cells, Set<Integer> clicked) {
		Matcher matcher = SELECT.matcher(title);
		if (!matcher.matches()) {
			return Solution.NONE;
		}
		List<String> words = colorWords(matcher.group(1).toLowerCase(Locale.ROOT));
		return each(cells, cell -> !cell.shines && !clicked.contains(cell.slot) && !cell.item.equals(FILLER) && startsWithAny(cell.name, words));
	}

	/** @return how the items of a color are named: the color itself, and the old names of its dyes ("lapis" for blue, "ink" for black) */
	static List<String> colorWords(String color) {
		return switch (color) {
			case "black" -> List.of("black", "ink");
			case "blue" -> List.of("blue", "lapis");
			case "brown" -> List.of("brown", "cocoa");
			case "white" -> List.of("white", "bone", "wool");
			case "green" -> List.of("green", "cactus");
			case "red" -> List.of("red", "rose");
			case "yellow" -> List.of("yellow", "dandelion");
			case "silver", "light gray" -> List.of("light gray", "silver");
			default -> List.of(color);
		};
	}

	private static boolean startsWithAny(String name, List<String> words) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String word : words) {
			// "blue" must not take the light blue items
			if (lower.startsWith(word) && !(word.equals("blue") && lower.startsWith("light blue")) && !(word.equals("gray") && lower.startsWith("light gray"))) {
				return true;
			}
		}
		return false;
	}

	/** The melody: the button of the row whose moving pane is in the marked column; only that one, and only while it is there. */
	private static Solution melody(List<Cell> cells) {
		int column = melodyColumn(cells);
		if (column < 0) {
			return Solution.NONE;
		}
		for (Cell mover : cells) {
			if (mover.item.equals(MELODY_MOVER) && mover.slot % ROW == column) {
				int row = mover.slot / ROW;
				for (Cell button : cells) {
					if (button.item.equals(MELODY_BUTTON) && button.slot / ROW == row) {
						return new Solution(List.of(button.slot), List.of(1));
					}
				}
			}
		}
		return Solution.NONE;
	}

	/** @return the melody's marked column (0 to 8), or -1 */
	public static int melodyColumn(List<Cell> cells) {
		for (Cell cell : cells) {
			if (cell.item.equals(MELODY_COLUMN)) {
				return cell.slot % ROW;
			}
		}
		return -1;
	}
}
