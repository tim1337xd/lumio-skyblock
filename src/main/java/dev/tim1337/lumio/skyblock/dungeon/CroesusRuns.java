package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.skyblock.data.Roman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Croesus' list of runs, kept per profile and saved: for each run its floor, the best profit seen when its chests were opened, and its marks (the
 * chest opened, the Kismet Feather and on which chest). Croesus shows no id for a run, only the runs in their order, newest first; so Lumio keeps
 * the same order: a run that ends puts a new one in front, and every page of the list seen lines the kept runs up with it again, by their floor and
 * by what the game says of each (opened, Kismet used). A run Croesus no longer lists is dropped.
 */
final class CroesusRuns {
	private static final Pattern RUN_LIST = Pattern.compile("^(?:\\((\\d+)/\\d+\\) )?Croesus$");
	private static final Pattern FLOOR = Pattern.compile("Floor ([IVX]+)");
	private static final Pattern KUUDRA = Pattern.compile("^(?:Basic|Hot|Burning|Fiery|Infernal) Tier$");
	private static final String MASTER = "Master";
	private static final String UNOPENED = "No chests opened yet!";
	private static final String ENTRANCE = "Entrance";
	/** The floor of a Kuudra run, which Croesus lists among the dungeon runs. */
	static final String KUUDRA_FLOOR = "K";
	/** Croesus' page: 4 rows of 7 runs, from slot 10. */
	static final int RUNS_PER_PAGE = 28;
	private static final int ROW_RUNS = 7;
	private static final int ROW_SLOTS = 9;
	private static final int ROWS = 4;
	/** How many runs are kept per profile (Croesus holds about 60), and for how many profiles. */
	private static final int MOST_RUNS = 64;
	private static final int MOST_PROFILES = 16;
	/** How far the kept runs may be off the list (runs that ended without Lumio, or one Croesus never listed) and still be lined up. */
	private static final int MOST_SHIFT = 4;
	private static final int MOST_TEXT = 32;
	/** How long after hovering a run its chests may open and still count for it. */
	private static final long OPEN_WINDOW_MILLIS = 10_000L;

	/** A run of the list: its floor, the best profit of its chests (NaN until seen) and its marks. */
	record Run(String floor, double profit, RunMarks marks) {
		static Run fresh(String floor) {
			return new Run(floor, Double.NaN, RunMarks.NONE);
		}
	}

	/** What the game and the player marked on a run: the chest opened (or null) and whether its Kismet Feather was used, and on which chest. */
	record RunMarks(String opened, boolean kismet, String kismetKind) {
		static final RunMarks NONE = new RunMarks(null, false, null);
	}

	/** One run item of a page of the list: its place in the whole list (from 0), its floor and what its lore says. */
	record Listed(int index, String floor, RunMarks marks) {
	}

	private final Map<String, List<Run>> byProfile = new HashMap<>();
	private final Supplier<String> profile;
	private final Runnable changed;
	private int hoveredIndex = -1;
	private String hoveredFloor;
	private long hoveredAt;
	private RunMarks hoveredMarks = RunMarks.NONE;
	/** The run whose chests are open now (its place in the list), -1 for none. */
	private int openIndex = -1;

	/** @param profile the SkyBlock profile now ("" when unknown); @param changed called when something to save changed */
	CroesusRuns(Supplier<String> profile, Runnable changed) {
		this.profile = profile;
		this.changed = changed;
	}

	/** @return the page of Croesus' run list this title is (1 when it has none), or 0 when it is not the run list */
	static int page(String title) {
		Matcher matcher = RUN_LIST.matcher(title);
		if (!matcher.matches()) {
			return 0;
		}
		return matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
	}

	/** @return the run's place in the whole list (from 0) for a slot of this page, or -1 for a slot that holds no run */
	static int index(int page, int slot) {
		int row = slot / ROW_SLOTS - 1;
		int column = slot % ROW_SLOTS - 1;
		if (page < 1 || row < 0 || row >= ROWS || column < 0 || column >= ROW_RUNS) {
			return -1;
		}
		return (page - 1) * RUNS_PER_PAGE + row * ROW_RUNS + column;
	}

	/** @return the run's floor as "F7" or "M7" ("E" for the entrance, "K" for Kuudra), or null when the item is no run */
	static String floorOf(String name, List<String> lore) {
		String mode = name.contains(MASTER) ? "M" : "F";
		for (String line : lore) {
			Matcher matcher = FLOOR.matcher(line);
			if (matcher.find()) {
				return mode + Roman.ofIvx(matcher.group(1));
			}
			if (line.contains(ENTRANCE)) {
				return "E";
			}
			if (KUUDRA.matcher(line).matches()) {
				return KUUDRA_FLOOR;
			}
		}
		return null;
	}

	/** The lines of a run's lore that say its chests were opened or claimed. */
	private static final String OPENED_PREFIX = "Opened Chest:";
	private static final String ALL_CLAIMED = "No more chests to open!";

	/**
	 * @return whether a run in the list has no chest opened yet: as its lore says ("No chests opened yet!", or "Opened Chest: …" and "No more chests
	 *         to open!" for opened), and where it says neither, by the marks Lumio kept of the run (null for none)
	 */
	static boolean notOpened(List<String> lore, RunMarks kept) {
		for (String line : lore) {
			if (line.equals(UNOPENED)) {
				return true;
			}
			if (line.startsWith(OPENED_PREFIX) || line.equals(ALL_CLAIMED)) {
				return false;
			}
		}
		return kept != null && kept.opened() == null;
	}

	/** @return the marks kept for the run at this place, if it is still the same floor there, else null */
	RunMarks marksAt(int page, int slot, String floor) {
		int index = index(page, slot);
		List<Run> runs = runs();
		if (index < 0 || index >= runs.size() || !floor.equals(runs.get(index).floor())) {
			return null;
		}
		return runs.get(index).marks();
	}

	/** @return whether none of the run's chests is opened yet */
	static boolean unopened(List<String> lore) {
		for (String line : lore) {
			if (line.equals(UNOPENED)) {
				return true;
			}
		}
		return false;
	}

	private List<Run> runs() {
		String key = profile.get();
		List<Run> runs = byProfile.get(key == null ? "" : key);
		if (runs == null) {
			if (byProfile.size() >= MOST_PROFILES) {
				byProfile.clear();
			}
			runs = new ArrayList<>();
			byProfile.put(key == null ? "" : key, runs);
		}
		return runs;
	}

	/** A run ended in a dungeon: Croesus puts it in front of its list, and the marks of its chests at the end belong to it. */
	void runEnded(String floor) {
		List<Run> runs = runs();
		runs.addFirst(Run.fresh(floor));
		while (runs.size() > MOST_RUNS) {
			runs.removeLast();
		}
		openIndex = 0;
		changed.run();
	}

	/**
	 * A page of the list was read: the kept runs are lined up with it (on the first page also shifted when runs came or went in front without
	 * Lumio), a run of another floor or a state that cannot be its own is replaced, and on the last page the runs after the list's end are dropped.
	 *
	 * @param lastPage whether this is the list's last page (it holds fewer runs than a full page)
	 */
	void sync(int page, List<Listed> listed, boolean lastPage) {
		List<Run> runs = runs();
		List<Run> before = List.copyOf(runs);
		if (page == 1 && !listed.isEmpty()) {
			int shift = alignment(runs, listed);
			if (shift == Integer.MIN_VALUE) {
				// nothing lines up: start again from what the list shows
				runs.clear();
			} else if (shift > 0) {
				for (int i = 0; i < shift; i++) {
					runs.addFirst(Run.fresh(null));
				}
			} else {
				for (int i = 0; i < -shift; i++) {
					runs.removeFirst();
				}
			}
		}
		int end = (page - 1) * RUNS_PER_PAGE;
		for (Listed item : listed) {
			while (runs.size() <= item.index() && runs.size() < MOST_RUNS) {
				runs.add(Run.fresh(null));
			}
			if (item.index() >= runs.size()) {
				continue;
			}
			Run run = runs.get(item.index());
			if (!fits(run, item)) {
				run = Run.fresh(item.floor());
			}
			runs.set(item.index(), new Run(item.floor(), run.profit(), merge(item.marks(), run.marks())));
			end = Math.max(end, item.index() + 1);
		}
		if (lastPage) {
			while (runs.size() > end) {
				runs.removeLast();
			}
		}
		if (!runs.equals(before)) {
			changed.run();
		}
	}

	/** @return how far the kept runs must move back (positive) or forward (negative) to line up with the page, or MIN_VALUE when they do not */
	static int alignment(List<Run> runs, List<Listed> listed) {
		if (runs.isEmpty()) {
			return 0;
		}
		for (int step = 0; step <= 2 * MOST_SHIFT; step++) {
			int shift = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
			if (linesUp(runs, listed, shift)) {
				return shift;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean linesUp(List<Run> runs, List<Listed> listed, int shift) {
		int overlap = 0;
		for (Listed item : listed) {
			int kept = item.index() - shift;
			if (kept < 0 || kept >= runs.size()) {
				continue;
			}
			if (!fits(runs.get(kept), item)) {
				return false;
			}
			overlap++;
		}
		return overlap > 0;
	}

	/** @return whether a kept run can be this item of the list: the same floor, and nothing Lumio saw that the game's lore contradicts */
	static boolean fits(Run run, Listed item) {
		if (run.floor() != null && !run.floor().equals(item.floor())) {
			return false;
		}
		RunMarks kept = run.marks();
		RunMarks shown = item.marks();
		if (kept.opened() != null && !kept.opened().equals(shown.opened())) {
			return false;
		}
		return !kept.kismet() || shown.kismet();
	}

	/** The player is over a run in the list: if its chests open next, they are this run's. */
	void hover(int page, int slot, String floor, long now) {
		hover(page, slot, floor, now, RunMarks.NONE);
	}

	/** The same, with what the run's line in the list says of it (the chest opened, the Kismet Feather struck through). */
	void hover(int page, int slot, String floor, long now, RunMarks marks) {
		hoveredIndex = index(page, slot);
		hoveredFloor = floor;
		hoveredAt = now;
		hoveredMarks = marks;
	}

	/** A run's chests are shown: their best profit is kept for the run hovered just before. */
	RunMarks opened(double bestProfit, long now) {
		openIndex = -1;
		RunMarks marks = RunMarks.NONE;
		if (hoveredIndex >= 0 && hoveredIndex < MOST_RUNS && now - hoveredAt <= OPEN_WINDOW_MILLIS) {
			List<Run> runs = runs();
			while (runs.size() <= hoveredIndex) {
				runs.add(Run.fresh(null));
			}
			Run before = runs.get(hoveredIndex);
			boolean same = hoveredFloor.equals(before.floor());
			// what the game says now, with what Lumio saw the player do on this run before
			marks = merge(hoveredMarks, same ? before.marks() : RunMarks.NONE);
			runs.set(hoveredIndex, new Run(hoveredFloor, bestProfit, marks));
			openIndex = hoveredIndex;
			changed.run();
		}
		hoveredIndex = -1;
		return marks;
	}

	/** The player opened a chest or used the Kismet Feather on one of the run whose chests are open: kept with the run. */
	void note(RunMarks seen) {
		List<Run> runs = runs();
		if (openIndex >= 0 && openIndex < runs.size()) {
			Run run = runs.get(openIndex);
			runs.set(openIndex, new Run(run.floor(), run.profit(), merge(run.marks(), seen)));
			changed.run();
		}
	}

	/** @return the marks kept for the run whose chests are open, none without one */
	RunMarks openMarks() {
		List<Run> runs = runs();
		return openIndex >= 0 && openIndex < runs.size() ? runs.get(openIndex).marks() : RunMarks.NONE;
	}

	/** The run whose chests are open is left: what is noted next belongs to no run of the list. */
	void closed() {
		openIndex = -1;
	}

	static RunMarks merge(RunMarks first, RunMarks second) {
		return new RunMarks(first.opened() != null ? first.opened() : second.opened(), first.kismet() || second.kismet(),
			first.kismetKind() != null ? first.kismetKind() : second.kismetKind());
	}

	/** @return the best profit seen for the run at this place, if it is still the same floor there, else NaN */
	double profit(int page, int slot, String floor) {
		int index = index(page, slot);
		List<Run> runs = runs();
		if (index < 0 || index >= runs.size()) {
			return Double.NaN;
		}
		Run run = runs.get(index);
		return floor.equals(run.floor()) ? run.profit() : Double.NaN;
	}

	/** @return the kept runs of every profile, for the config */
	JsonObject save() {
		JsonObject json = new JsonObject();
		byProfile.forEach((key, runs) -> {
			JsonArray array = new JsonArray();
			for (Run run : runs) {
				JsonObject entry = new JsonObject();
				if (run.floor() != null) {
					entry.addProperty("floor", run.floor());
				}
				if (!Double.isNaN(run.profit())) {
					entry.addProperty("profit", run.profit());
				}
				if (run.marks().opened() != null) {
					entry.addProperty("opened", run.marks().opened());
				}
				if (run.marks().kismet()) {
					entry.addProperty("kismet", true);
				}
				if (run.marks().kismetKind() != null) {
					entry.addProperty("kismet_chest", run.marks().kismetKind());
				}
				array.add(entry);
			}
			if (!array.isEmpty()) {
				json.add(key, array);
			}
		});
		return json;
	}

	/** Takes the kept runs from the config; anything malformed is left out. */
	void load(JsonElement element) {
		byProfile.clear();
		if (!(element instanceof JsonObject json)) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (byProfile.size() >= MOST_PROFILES || entry.getKey().length() > MOST_TEXT * 2 || !(entry.getValue() instanceof JsonArray array)) {
				continue;
			}
			List<Run> runs = new ArrayList<>();
			for (JsonElement item : array) {
				if (runs.size() >= MOST_RUNS) {
					break;
				}
				runs.add(item instanceof JsonObject run ? read(run) : Run.fresh(null));
			}
			byProfile.put(entry.getKey(), runs);
		}
	}

	private static Run read(JsonObject json) {
		double profit = Double.NaN;
		try {
			if (json.has("profit")) {
				profit = json.get("profit").getAsDouble();
			}
		} catch (RuntimeException e) {
			profit = Double.NaN;
		}
		if (!Double.isFinite(profit)) {
			profit = Double.NaN;
		}
		boolean kismet = json.has("kismet") && json.get("kismet").isJsonPrimitive() && json.get("kismet").getAsBoolean();
		return new Run(text(json, "floor"), profit, new RunMarks(text(json, "opened"), kismet, text(json, "kismet_chest")));
	}

	private static String text(JsonObject json, String key) {
		JsonElement value = json.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		String text = value.getAsString();
		return text.isEmpty() || text.length() > MOST_TEXT ? null : text;
	}
}
