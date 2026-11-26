package dev.tim1337.lumio.skyblock.hud.buttons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * The buttons on the 28 spots, with the steps the editor can take back. Every change is one step; Undo restores the spots as they were before it.
 * Plain data, so it is unit tested.
 */
final class ButtonLayout {
	/** A button on its spot, for the clipboard and pasting. */
	record Placed(int spot, InventoryButton button) {
	}

	/** How many steps Undo can take back. */
	static final int MOST_UNDO = 50;

	private InventoryButton[] spots = new InventoryButton[ButtonSpots.COUNT];
	private final Deque<InventoryButton[]> undo = new ArrayDeque<>();

	InventoryButton get(int spot) {
		return valid(spot) ? spots[spot] : null;
	}

	int used() {
		int used = 0;
		for (InventoryButton button : spots) {
			if (button != null) {
				used++;
			}
		}
		return used;
	}

	boolean isEmpty() {
		return used() == 0;
	}

	/** @return the first free spot, or -1 when all are taken */
	int nextFree() {
		for (int i = 0; i < spots.length; i++) {
			if (spots[i] == null) {
				return i;
			}
		}
		return -1;
	}

	/** Puts a button on a spot (null clears it). */
	void set(int spot, InventoryButton button) {
		if (!valid(spot) || spots[spot] == button) {
			return;
		}
		step();
		spots[spot] = button;
	}

	/** Moves a button to another spot; a button there trades places with it. */
	void move(int from, int to) {
		if (!valid(from) || !valid(to) || from == to || spots[from] == null) {
			return;
		}
		step();
		InventoryButton there = spots[to];
		spots[to] = spots[from];
		spots[from] = there;
	}

	/** @return the spot of the copy on the next free spot, or -1 when there is none free */
	int duplicate(int spot) {
		int free = nextFree();
		if (get(spot) == null || free < 0) {
			return -1;
		}
		set(free, spots[spot]);
		return free;
	}

	/** Removes every button, as one step. */
	void clear() {
		if (!isEmpty()) {
			step();
			Arrays.fill(spots, null);
		}
	}

	/**
	 * Adds pasted buttons, as one step: each on its own spot when free, else on the next free one; with replace the old ones go first.
	 *
	 * @return how many were placed
	 */
	int paste(List<Placed> buttons, boolean replace) {
		step();
		if (replace) {
			Arrays.fill(spots, null);
		}
		int placed = 0;
		for (Placed entry : buttons) {
			int spot = valid(entry.spot()) && spots[entry.spot()] == null ? entry.spot() : nextFree();
			if (spot < 0) {
				break;
			}
			spots[spot] = entry.button();
			placed++;
		}
		return placed;
	}

	boolean canUndo() {
		return !undo.isEmpty();
	}

	/** Takes back the last step. */
	void undo() {
		if (!undo.isEmpty()) {
			spots = undo.pop();
		}
	}

	private void step() {
		undo.push(spots.clone());
		while (undo.size() > MOST_UNDO) {
			undo.removeLast();
		}
	}

	private static boolean valid(int spot) {
		return spot >= 0 && spot < ButtonSpots.COUNT;
	}

	/** @return the buttons with their spots, in spot order */
	List<Placed> placed() {
		List<Placed> placed = new ArrayList<>();
		for (int i = 0; i < spots.length; i++) {
			if (spots[i] != null) {
				placed.add(new Placed(i, spots[i]));
			}
		}
		return placed;
	}

	JsonArray toJson() {
		JsonArray array = new JsonArray();
		for (Placed entry : placed()) {
			JsonObject json = entry.button().toJson();
			json.addProperty("spot", entry.spot());
			array.add(json);
		}
		return array;
	}

	/** @return the buttons of saved JSON with their spots; broken entries and spots out of range are left out, a spot only once */
	static List<Placed> fromJson(JsonArray array) {
		List<Placed> placed = new ArrayList<>();
		boolean[] taken = new boolean[ButtonSpots.COUNT];
		for (JsonElement element : array) {
			if (!element.isJsonObject() || placed.size() >= ButtonSpots.COUNT) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			InventoryButton button = InventoryButton.fromJson(json);
			int spot = json.has("spot") && json.get("spot").isJsonPrimitive() && json.get("spot").getAsJsonPrimitive().isNumber() ? json.get("spot").getAsInt()
				: -1;
			if (button != null && valid(spot) && !taken[spot]) {
				taken[spot] = true;
				placed.add(new Placed(spot, button));
			}
		}
		return placed;
	}

	/** Takes saved buttons without a step to undo (loading). */
	void load(List<Placed> placed) {
		Arrays.fill(spots, null);
		undo.clear();
		for (Placed entry : placed) {
			if (valid(entry.spot())) {
				spots[entry.spot()] = entry.button();
			}
		}
	}
}
