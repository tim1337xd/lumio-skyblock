package dev.tim1337.lumio.skyblock.mining.hollows;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The places found in each Crystal Hollows lobby, by its server id, so they are still there after rejoining the same lobby. A lobby's places are
 * only ever given for that lobby; a lobby not seen for a few hours is dropped. What the player found themselves wins over what chat said; from
 * chat the first place stays. Plain data with the time given to it, so it is unit tested; the feature writes it to a file off the game's thread.
 */
final class HollowsLobbies {
	/** A lobby not seen for this long is dropped: lobbies close when empty, and the same id may be a new lobby later. */
	static final long KEEP_MILLIS = 4L * 60 * 60 * 1000;
	/** The most lobbies kept; the oldest go first. */
	static final int MOST_LOBBIES = 40;
	private static final int MOST_ID_LENGTH = 16;

	/** A place: where, and whether the player found it there themselves (else it came from chat). */
	record Spot(int x, int y, int z, boolean own) {
	}

	private static final class Lobby {
		long seen;
		final Map<HollowsStructure, Spot> spots = new EnumMap<>(HollowsStructure.class);

		Lobby(long seen) {
			this.seen = seen;
		}
	}

	private final Map<String, Lobby> lobbies = new LinkedHashMap<>();

	/**
	 * Keeps a place in a lobby.
	 *
	 * @return whether something changed
	 */
	boolean put(String lobby, HollowsStructure structure, int x, int y, int z, boolean own, long now) {
		if (!valid(lobby) || !HollowsGeometry.inside(x, y, z)) {
			return false;
		}
		Lobby entry = lobbies.computeIfAbsent(lobby, id -> new Lobby(now));
		entry.seen = Math.max(entry.seen, now);
		Spot before = entry.spots.get(structure);
		// the player's own visit is the truth; chat never moves a place the player found, and the first place from chat stays
		if (before != null && (before.own() || !own)) {
			return false;
		}
		entry.spots.put(structure, new Spot(x, y, z, own));
		trim();
		return true;
	}

	/** @return the places of one lobby (empty for an unknown lobby or none) */
	Map<HollowsStructure, Spot> spots(String lobby) {
		Lobby entry = valid(lobby) ? lobbies.get(lobby) : null;
		return entry == null ? Map.of() : Collections.unmodifiableMap(entry.spots);
	}

	/** The player is in this lobby now: it is kept a while longer. */
	void seen(String lobby, long now) {
		Lobby entry = valid(lobby) ? lobbies.get(lobby) : null;
		if (entry != null) {
			entry.seen = Math.max(entry.seen, now);
		}
	}

	/** @return whether a lobby had places and has none now */
	boolean clear(String lobby) {
		return valid(lobby) && lobbies.remove(lobby) != null;
	}

	/** @return whether lobbies were dropped for being too old */
	boolean expire(long now) {
		return lobbies.values().removeIf(entry -> now - entry.seen > KEEP_MILLIS);
	}

	int lobbyCount() {
		return lobbies.size();
	}

	private void trim() {
		while (lobbies.size() > MOST_LOBBIES) {
			String oldest = null;
			long oldestSeen = Long.MAX_VALUE;
			for (Map.Entry<String, Lobby> entry : lobbies.entrySet()) {
				if (entry.getValue().seen < oldestSeen) {
					oldest = entry.getKey();
					oldestSeen = entry.getValue().seen;
				}
			}
			lobbies.remove(oldest);
		}
	}

	private static boolean valid(String lobby) {
		return lobby != null && !lobby.isEmpty() && lobby.length() <= MOST_ID_LENGTH;
	}

	// --------------------------------------------------------------------------------------------------------------- file

	/** @return the lobbies as JSON: {"m14AB": {"seen": 1731400000000, "spots": {"mines_of_divan": [512, 80, 300, 1]}}} */
	String toJson() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Lobby> entry : lobbies.entrySet()) {
			JsonObject lobby = new JsonObject();
			lobby.addProperty("seen", entry.getValue().seen);
			JsonObject spots = new JsonObject();
			for (Map.Entry<HollowsStructure, Spot> spot : entry.getValue().spots.entrySet()) {
				JsonArray place = new JsonArray();
				place.add(spot.getValue().x());
				place.add(spot.getValue().y());
				place.add(spot.getValue().z());
				place.add(spot.getValue().own() ? 1 : 0);
				spots.add(spot.getKey().key(), place);
			}
			lobby.add("spots", spots);
			root.add(entry.getKey(), lobby);
		}
		return root.toString();
	}

	/** Reads lobbies saved before, dropping the old ones; anything it cannot read is left out. */
	void load(String json, long now) {
		lobbies.clear();
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
				if (!valid(entry.getKey()) || !entry.getValue().isJsonObject()) {
					continue;
				}
				readLobby(entry.getKey(), entry.getValue().getAsJsonObject());
			}
		} catch (RuntimeException e) {
			lobbies.clear();
			return;
		}
		expire(now);
		trim();
	}

	private void readLobby(String id, JsonObject json) {
		if (!json.has("seen") || !json.has("spots") || !json.get("spots").isJsonObject()) {
			return;
		}
		Lobby lobby = new Lobby(json.get("seen").getAsLong());
		for (Map.Entry<String, JsonElement> spot : json.getAsJsonObject("spots").entrySet()) {
			HollowsStructure structure = HollowsStructure.byKey(spot.getKey());
			if (structure == null || !spot.getValue().isJsonArray() || spot.getValue().getAsJsonArray().size() != 4) {
				continue;
			}
			JsonArray place = spot.getValue().getAsJsonArray();
			int x = place.get(0).getAsInt();
			int y = place.get(1).getAsInt();
			int z = place.get(2).getAsInt();
			if (HollowsGeometry.inside(x, y, z)) {
				lobby.spots.put(structure, new Spot(x, y, z, place.get(3).getAsInt() == 1));
			}
		}
		if (!lobby.spots.isEmpty()) {
			lobbies.put(id, lobby);
		}
	}
}
