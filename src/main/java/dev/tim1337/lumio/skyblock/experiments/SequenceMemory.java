package dev.tim1337.lumio.skyblock.experiments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The order of Chronomatron or Ultrasequencer and how far the player has repeated it, from what the menu shows at every update (the rules after SkyHanni's).
 * <p>
 * Chronomatron: while the game shows the pattern, each flash (terracotta on the board, after the board was without any) is one step. Every round the game
 * plays the whole order again plus one new color: the known steps are walked through, and only the step after them is added. So a missed or doubled look
 * never shifts the order, and the same color twice in a row counts twice.
 * <p>
 * Ultrasequencer: the numbers of every update of a showing are gathered, each to its slot, also those of the update that turns to the player's turn;
 * the steps are the numbers in order. The game draws them one by one and takes them off again, so an update with fewer numbers never takes a step away;
 * a number never seen leaves its step unknown, and then no click is judged.
 * <p>
 * Repeating: a click counts at once if it was the expected one, without waiting for the server, so the next step lights up at once. If the game goes on in
 * another way (a new showing, the round or the phase changes), what is known is taken from the menu again instead of drifting. Nothing here clicks or
 * sends anything.
 */
public final class SequenceMemory {
	private final List<String> colors = new ArrayList<>();
	/** Ultrasequencer: the slot of each step, by its number less one; a number that was never seen is an unknown slot. */
	private final List<Integer> slots = new ArrayList<>();
	private final Map<Integer, Integer> numbers = new TreeMap<>();
	/** Ultrasequencer: every number seen in this showing, to its slot. */
	private final TreeMap<Integer, Integer> shown = new TreeMap<>();
	private static final int UNKNOWN_SLOT = -1;
	/** The last phase that was not NONE: an update without a phase in the middle of a showing does not start it anew. */
	private ExperimentFormats.Phase lastActive = ExperimentFormats.Phase.NONE;
	private ExperimentFormats.Phase phase = ExperimentFormats.Phase.NONE;
	private int round;
	/** Where in the known order the flashes of this showing are. */
	private int readIndex;
	/** Whether the board was without a flash since the last one, so the next one is a new step. */
	private boolean flashOver = true;
	/** The right clicks of this round. */
	private int done;
	/** Those of them that an update of the menu has followed. */
	private int confirmed;

	/** The menu closed, or a new game starts: all is forgotten. */
	public void clear() {
		colors.clear();
		slots.clear();
		numbers.clear();
		shown.clear();
		lastActive = ExperimentFormats.Phase.NONE;
		phase = ExperimentFormats.Phase.NONE;
		round = 0;
		readIndex = 0;
		flashOver = true;
		done = 0;
		confirmed = 0;
	}

	/** @return a color's own name, whatever the item calls it: "Lime" and "lime" are one color */
	static String colorOf(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * Chronomatron, at every update of the menu.
	 *
	 * @param flashing the colors flashing on the board now, without doubles
	 */
	public void seeChronomatron(ExperimentFormats.Phase now, int roundNow, Set<String> flashing) {
		ExperimentFormats.Phase before = phase;
		boolean newRound = roundNow != round;
		phase = now;
		round = roundNow;
		if (now == ExperimentFormats.Phase.NONE) {
			return;
		}
		if (now == ExperimentFormats.Phase.READ && (before != ExperimentFormats.Phase.READ || newRound)) {
			// the game plays the order from its start again: walk the known steps from the first
			readIndex = 0;
			restartRound();
		}
		confirm(before, newRound);
		if (flashing.isEmpty()) {
			flashOver = true;
			return;
		}
		// the last flash of a showing can still be on when the phase has turned, while the player has not clicked yet
		boolean counts = now == ExperimentFormats.Phase.READ || before == ExperimentFormats.Phase.READ || done == 0 && colors.size() < round;
		if (flashOver && counts) {
			String color = colorOf(flashing.iterator().next());
			if (readIndex < colors.size()) {
				// a known step: if the game shows another color there, the order is taken from here on as it is shown now
				if (!colors.get(readIndex).equals(color)) {
					colors.subList(readIndex, colors.size()).clear();
					colors.add(color);
				}
			} else if (readIndex == colors.size()) {
				colors.add(color);
			}
			readIndex++;
		}
		flashOver = false;
	}

	/**
	 * Ultrasequencer, at every update of the menu.
	 *
	 * @param numberOfSlot the number each slot shows now
	 */
	public void seeUltrasequencer(ExperimentFormats.Phase now, Map<Integer, Integer> numberOfSlot) {
		ExperimentFormats.Phase before = phase;
		ExperimentFormats.Phase active = lastActive;
		phase = now;
		if (now == ExperimentFormats.Phase.NONE) {
			return;
		}
		lastActive = now;
		if (now == ExperimentFormats.Phase.READ && active != ExperimentFormats.Phase.READ) {
			// a new showing: its numbers are gathered afresh
			shown.clear();
		}
		// the numbers of every update of a showing are gathered, also of the one that turns to the player's turn: the game takes them off one by
		// one, and the last can come with the turn, so an update with fewer numbers never takes a step away
		if ((now == ExperimentFormats.Phase.READ || active == ExperimentFormats.Phase.READ) && !numberOfSlot.isEmpty()) {
			numberOfSlot.forEach((slot, number) -> shown.put(number, slot));
			int highest = shown.lastKey();
			if (highest != slots.size() || !sameOrder()) {
				slots.clear();
				numbers.clear();
				for (int number = 1; number <= highest; number++) {
					Integer slot = shown.get(number);
					slots.add(slot == null ? UNKNOWN_SLOT : slot);
					if (slot != null) {
						numbers.put(slot, number);
					}
				}
				round = highest;
				restartRound();
			}
		}
		confirm(before, false);
	}

	/** @return whether the order holds just what this showing has shown */
	private boolean sameOrder() {
		for (Map.Entry<Integer, Integer> step : shown.entrySet()) {
			if (slots.get(step.getKey() - 1) != step.getValue().intValue()) {
				return false;
			}
		}
		return true;
	}

	/** @return whether every step of the order is known: none of its numbers was missed */
	public boolean orderComplete() {
		return !slots.isEmpty() && !slots.contains(UNKNOWN_SLOT);
	}

	/** An update of the menu: what follows from it for the clicks. */
	private void confirm(ExperimentFormats.Phase before, boolean newRound) {
		if (phase == ExperimentFormats.Phase.REPLICATE && before == ExperimentFormats.Phase.REPLICATE && !newRound) {
			confirmed = done;
		} else if (phase == ExperimentFormats.Phase.REPLICATE && before != ExperimentFormats.Phase.REPLICATE) {
			// the player's turn begins: from the first step
			restartRound();
		}
	}

	private void restartRound() {
		done = 0;
		confirmed = 0;
	}

	/** The player clicked a color: only the expected one moves the progress on, at once. @return whether it was the expected one */
	public boolean clickedColor(String color) {
		if (isNextColor(color)) {
			done++;
			return true;
		}
		return false;
	}

	/** The player clicked a slot: only the expected one moves the progress on, at once. @return whether it was the expected one */
	public boolean clickedSlot(int slot) {
		if (isNextSlot(slot)) {
			done++;
			return true;
		}
		return false;
	}

	/** @return whether a color is the next step; the color counts, not the slot it is clicked on */
	public boolean isNextColor(String color) {
		return phase == ExperimentFormats.Phase.REPLICATE && done < colors.size() && colors.get(done).equals(colorOf(color));
	}

	/** @return whether a slot holds the next step */
	public boolean isNextSlot(int slot) {
		return phase == ExperimentFormats.Phase.REPLICATE && done < slots.size() && slots.get(done) == slot;
	}

	/** @return whether the marks are drawn: it is the player's turn, and steps are left */
	public boolean repeating() {
		return phase == ExperimentFormats.Phase.REPLICATE && done < length();
	}

	/** @return whether a click of Chronomatron can be judged: the player's turn, steps left, and the whole order of the round is known */
	public boolean colorStepsLeft() {
		return phase == ExperimentFormats.Phase.REPLICATE && done < colors.size() && colors.size() == round;
	}

	/** @return whether a click of Ultrasequencer can be judged: the player's turn, steps left, and the order of the round is known */
	public boolean slotStepsLeft() {
		return phase == ExperimentFormats.Phase.REPLICATE && done < slots.size() && orderComplete();
	}

	/** @return the color of the step this many after the next (0 is the next), or null */
	public String nextColor(int ahead) {
		int place = done + ahead;
		return place < colors.size() ? colors.get(place) : null;
	}

	/** @return the slot of the step this many after the next (0 is the next), or -1 */
	public int nextSlot(int ahead) {
		int place = done + ahead;
		return place < slots.size() ? slots.get(place) : -1;
	}

	/** @return the number the game showed on a slot this round, or 0 */
	public int numberAt(int slot) {
		return numbers.getOrDefault(slot, 0);
	}

	public ExperimentFormats.Phase phase() {
		return phase;
	}

	public int round() {
		return round;
	}

	public int done() {
		return done;
	}

	public int confirmed() {
		return confirmed;
	}

	public int length() {
		return Math.max(colors.size(), slots.size());
	}

	/** @return the known order, for the debug overlay and log: the colors, or each number with its slot ("1>43, 2>?") */
	public String known() {
		if (!colors.isEmpty()) {
			return String.join(", ", colors);
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < slots.size(); i++) {
			text.append(i == 0 ? "" : ", ").append(i + 1).append('>').append(slots.get(i) == UNKNOWN_SLOT ? "?" : String.valueOf(slots.get(i)));
		}
		return text.toString();
	}
}
