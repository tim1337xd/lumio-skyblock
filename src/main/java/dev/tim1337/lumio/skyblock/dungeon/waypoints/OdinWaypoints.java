package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static dev.tim1337.lumio.skyblock.dungeon.waypoints.DungeonWaypointFormat.MOST_POINTS_PER_ROOM;
import static dev.tim1337.lumio.skyblock.dungeon.waypoints.DungeonWaypointFormat.MOST_ROOMS;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.skyblock.dungeon.RoomModel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The converter between Odin's dungeon waypoint lists and Lumio's: used only to import and export, never to keep points; after an import nothing
 * of Odin's is used again. Odin's lists count a point from a corner of the room, in a turn of the room that is Lumio's own for every shape (an L
 * with its missing tile right of the top left in both); Lumio counts from the room's middle ({@link RoomModel}), so a point moves by the room
 * shape's middle, once, and its box, which lies in the same turn, stays. Odin's "depth" (walls hide the
 * point) is the opposite of Lumio's through walls. Odin's lists name rooms as Lumio's room data does; a room not in it is logged once and left out.
 */
public final class OdinWaypoints {
	private static final String[] BOX_KEYS = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
	/** Rooms already logged as unknown, so each is logged once. */
	private static final Set<String> UNKNOWN_LOGGED = ConcurrentHashMap.newKeySet();
	private static final int MOST_LOGGED = 64;

	private OdinWaypoints() {
	}

	/** @return Odin's list (a JSON object of rooms) in Lumio's room model */
	static Map<String, List<DungeonWaypoint>> fromOdin(JsonObject root, Function<String, String> shapeOf) {
		Map<String, List<DungeonWaypoint>> rooms = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			String room = DungeonWaypointFormat.roomName(entry.getKey());
			if (rooms.size() >= MOST_ROOMS || room == null || !(entry.getValue() instanceof JsonArray array)) {
				continue;
			}
			String shape = shapeOf.apply(room);
			if (shape == null) {
				// a room not in the room data: kept while it has no points (nothing to convert), else logged once and left out
				if (array.isEmpty()) {
					rooms.put(room, new ArrayList<>());
				} else {
					unknownRoom(room);
				}
				continue;
			}
			List<DungeonWaypoint> points = new ArrayList<>();
			for (JsonElement element : array) {
				if (points.size() >= MOST_POINTS_PER_ROOM) {
					break;
				}
				DungeonWaypoint point = element instanceof JsonObject json ? point(json, shape) : null;
				if (point != null) {
					points.add(point);
				}
			}
			rooms.put(room, points);
		}
		return rooms;
	}

	/** @return the points in Odin's JSON, counted from each room's corner again; rooms not known are left out */
	public static String writeJson(Map<String, List<DungeonWaypoint>> rooms, Function<String, String> shapeOf) {
		return DungeonWaypointFormat.PRETTY.toJson(toOdin(rooms, shapeOf));
	}

	/** @return the points as Odin exports them: its JSON gzipped, in Base64 */
	public static String write(Map<String, List<DungeonWaypoint>> rooms, Function<String, String> shapeOf) {
		return DungeonWaypointFormat.pack(new Gson().toJson(toOdin(rooms, shapeOf)));
	}

	private static JsonObject toOdin(Map<String, List<DungeonWaypoint>> rooms, Function<String, String> shapeOf) {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<DungeonWaypoint>> room : rooms.entrySet()) {
			String shape = shapeOf.apply(room.getKey());
			if (shape == null && !room.getValue().isEmpty()) {
				unknownRoom(room.getKey());
				continue;
			}
			JsonArray array = new JsonArray();
			for (DungeonWaypoint point : room.getValue()) {
				int[] at = RoomModel.toCorner(shape, point.x(), point.y(), point.z());
				JsonObject json = new JsonObject();
				JsonObject pos = new JsonObject();
				pos.addProperty("x", at[0]);
				pos.addProperty("y", at[1]);
				pos.addProperty("z", at[2]);
				json.add("blockPos", pos);
				json.addProperty("color", DungeonWaypointFormat.color(point.argb()));
				json.addProperty("filled", point.filled());
				json.addProperty("depth", !point.throughWalls());
				double[] box = {point.minX(), point.minY(), point.minZ(), point.maxX(), point.maxY(), point.maxZ()};
				JsonObject aabb = new JsonObject();
				for (int i = 0; i < BOX_KEYS.length; i++) {
					aabb.addProperty(BOX_KEYS[i], box[i]);
				}
				json.add("aabb", aabb);
				json.addProperty("title", point.title());
				json.addProperty("type", point.type().name());
				array.add(json);
			}
			root.add(room.getKey(), array);
		}
		return root;
	}

	private static DungeonWaypoint point(JsonObject json, String shape) {
		JsonObject pos = json.get("blockPos") instanceof JsonObject inner ? inner : json;
		if (!DungeonWaypointFormat.number(pos, "x") || !DungeonWaypointFormat.number(pos, "y") || !DungeonWaypointFormat.number(pos, "z")) {
			return null;
		}
		int[] at = RoomModel.fromCorner(shape, DungeonWaypointFormat.offset(pos, "x"), DungeonWaypointFormat.offset(pos, "y"),
			DungeonWaypointFormat.offset(pos, "z"));
		double[] box = {0, 0, 0, 1, 1, 1};
		if (json.get("aabb") instanceof JsonObject aabb) {
			for (int i = 0; i < BOX_KEYS.length; i++) {
				box[i] = DungeonWaypointFormat.boxValue(aabb.get(BOX_KEYS[i]), box[i]);
			}
		}
		return new DungeonWaypoint(at[0], at[1], at[2], DungeonWaypointFormat.argb(json.get("color")), DungeonWaypointFormat.bool(json, "filled", false),
			!DungeonWaypointFormat.bool(json, "depth", false), box[0], box[1], box[2], box[3], box[4], box[5],
			DungeonWaypointFormat.title(json.get("title")), DungeonWaypointFormat.type(json), true);
	}

	/** Logs a room that is not in the room data, once per name. */
	static void unknownRoom(String room) {
		if (UNKNOWN_LOGGED.size() < MOST_LOGGED && UNKNOWN_LOGGED.add(room)) {
			Logs.warn("Dungeon waypoints: no room named \"{}\" in the room data, its points are left out", room);
		}
	}
}
