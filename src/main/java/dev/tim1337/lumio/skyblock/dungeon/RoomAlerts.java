package dev.tim1337.lumio.skyblock.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * When the room the player is in gets its checkmark on the game's map: "Room cleared" once per room and run, and (if wanted) "All secrets" once
 * when the check turns green. Only a change seen while in the room counts: a room entered already cleared says nothing. The entrance, the fairy
 * room and the blood room only when wanted. Plain values, so it is tested without the game.
 */
final class RoomAlerts {
	/** What a map update says for the room the player is in, as flags. */
	static final int NOTHING = 0;
	static final int CLEARED = 1;
	static final int ALL_SECRETS = 2;

	private final Map<Integer, DungeonMapData.Check> seen = new HashMap<>();
	private final Set<Integer> cleared = new HashSet<>();
	private final Set<Integer> allSecrets = new HashSet<>();

	/**
	 * @param room          the main tile of the room the player is in
	 * @param kind          its kind on the map
	 * @param check         its checkmark on the map now
	 * @param special       whether the entrance, fairy and blood rooms count
	 * @param secretsAlerts whether the green check gives its own alert
	 * @return the alerts to show now, as flags
	 */
	int update(int room, DungeonMapData.Kind kind, DungeonMapData.Check check, boolean special, boolean secretsAlerts) {
		if (room < 0 || kind == null || check == null) {
			return NOTHING;
		}
		DungeonMapData.Check before = seen.put(room, check);
		if (before == null || !special && excluded(kind)) {
			// first seen in this room: whatever it shows was so before the player came
			return NOTHING;
		}
		int alerts = NOTHING;
		if (done(check) && !done(before) && cleared.add(room)) {
			alerts |= CLEARED;
		}
		if (secretsAlerts && check == DungeonMapData.Check.GREEN && before != DungeonMapData.Check.GREEN && allSecrets.add(room)) {
			alerts |= ALL_SECRETS;
		}
		return alerts;
	}

	private static boolean done(DungeonMapData.Check check) {
		return check == DungeonMapData.Check.WHITE || check == DungeonMapData.Check.GREEN;
	}

	/** @return whether a room kind is left out unless wanted: the entrance, the fairy room and the blood room */
	static boolean excluded(DungeonMapData.Kind kind) {
		return kind == DungeonMapData.Kind.ENTRANCE || kind == DungeonMapData.Kind.FAIRY || kind == DungeonMapData.Kind.BLOOD;
	}

	/** A new run or world: every room may alert again. */
	void reset() {
		seen.clear();
		cleared.clear();
		allSecrets.clear();
	}
}
