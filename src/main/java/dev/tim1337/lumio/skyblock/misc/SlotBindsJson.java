package dev.tim1337.lumio.skyblock.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** The slot binds in the config: the named sets and the one in use, and the six numbered sets of before taken over. Tested without the game. */
final class SlotBindsJson {
	/** The settings of before the named sets: the number of the set in use, and six numbered sets of binds. */
	static final String OLD_SET_KEY = "bind_set";
	static final String BINDS_KEY = "binds";
	static final String SETS_KEY = "sets";
	static final String ACTIVE_KEY = "active";

	private SlotBindsJson() {
	}

	/** Writes the sets and the one in use into a feature's settings. */
	static void write(SlotBinds from, JsonObject json) {
		JsonArray sets = new JsonArray();
		for (int s = 0; s < from.count(); s++) {
			JsonObject set = new JsonObject();
			set.addProperty("name", from.name(s));
			set.addProperty("area", from.area(s).name());
			JsonArray pairs = new JsonArray();
			for (int[] pair : from.pairs(s)) {
				JsonArray each = new JsonArray();
				each.add(pair[0]);
				each.add(pair[1]);
				pairs.add(each);
			}
			set.add(BINDS_KEY, pairs);
			sets.add(set);
		}
		json.add(SETS_KEY, sets);
		json.addProperty(ACTIVE_KEY, from.active());
	}

	/**
	 * Configs from before the named sets: of the six numbered sets, each with binds becomes the set "Set N"; empty ones are dropped. The set that
	 * was in use stays in use. Done in place, so the next save writes the new form.
	 */
	static void migrate(JsonObject json) {
		if (json.has(SETS_KEY) || !(json.get(BINDS_KEY) instanceof JsonArray old)) {
			return;
		}
		int oldActive = json.get(OLD_SET_KEY) instanceof JsonPrimitive primitive && primitive.isString() ? oldSetIndex(primitive.getAsString()) : 0;
		JsonArray sets = new JsonArray();
		int active = 0;
		for (int s = 0; s < old.size(); s++) {
			if (!(old.get(s) instanceof JsonArray pairs) || pairs.isEmpty()) {
				continue;
			}
			if (s == oldActive) {
				active = sets.size();
			}
			JsonObject set = new JsonObject();
			set.addProperty("name", "Set " + (s + 1));
			set.addProperty("area", (sets.isEmpty() ? SlotBinds.Area.EVERYWHERE : SlotBinds.Area.OFF).name());
			set.add(BINDS_KEY, pairs);
			sets.add(set);
		}
		json.remove(BINDS_KEY);
		if (!sets.isEmpty()) {
			json.add(SETS_KEY, sets);
			json.addProperty(ACTIVE_KEY, active);
		}
	}

	/** @return the index of the old set setting's value ("ONE" to "SIX") */
	private static int oldSetIndex(String name) {
		String[] names = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX"};
		for (int i = 0; i < names.length; i++) {
			if (names[i].equals(name)) {
				return i;
			}
		}
		return 0;
	}

	/** Reads the sets of a config into the binds; without any, one empty "Default". */
	static void read(JsonObject json, SlotBinds into) {
		into.reset();
		if (!(json.get(SETS_KEY) instanceof JsonArray sets)) {
			return;
		}
		boolean first = true;
		for (JsonElement element : sets) {
			if (!(element instanceof JsonObject set)) {
				continue;
			}
			String name = set.get("name") instanceof JsonPrimitive text && text.isString() ? text.getAsString() : "";
			int index = into.load(name, area(set.get("area")), first);
			first = false;
			if (index < 0) {
				break;
			}
			if (set.get(BINDS_KEY) instanceof JsonArray pairs) {
				for (JsonElement pair : pairs) {
					if (pair instanceof JsonArray each && each.size() == 2 && each.get(0) instanceof JsonPrimitive a && a.isNumber()
						&& each.get(1) instanceof JsonPrimitive b && b.isNumber()) {
						into.load(index, a.getAsInt(), b.getAsInt());
					}
				}
			}
		}
		if (json.get(ACTIVE_KEY) instanceof JsonPrimitive active && active.isNumber()) {
			into.choose(active.getAsInt());
		}
	}

	private static SlotBinds.Area area(JsonElement element) {
		if (element instanceof JsonPrimitive primitive && primitive.isString()) {
			for (SlotBinds.Area area : SlotBinds.Area.values()) {
				if (area.name().equals(primitive.getAsString())) {
					return area;
				}
			}
		}
		return SlotBinds.Area.OFF;
	}
}
