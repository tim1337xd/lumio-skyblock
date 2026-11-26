package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import dev.tim1337.lumio.skyblock.dungeon.RoomModel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Lumio's own list of dungeon waypoints by room, the only form they are kept in: each point in Lumio's room model ({@link RoomModel}) with its
 * color as "#RRGGBBAA", filled, through walls, its box, title and type. Reading also takes Odin's lists, which {@link OdinWaypoints} turns into
 * Lumio's points once, and Lumio's first format, which counted from the room's corner. Everything read is bounded: rooms, points, numbers and texts.
 */
public final class DungeonWaypointFormat {
	public static final String MARK = "lumio_dungeon_waypoints";
	/** 2: points from the room's middle, "through_walls"; 1 counted from the room's corner and had Odin's "depth". */
	static final int FORMAT = 2;
	private static final int FORMAT_FROM_CORNER = 1;
	static final int MOST_ROOMS = 400;
	static final int MOST_POINTS_PER_ROOM = 200;
	static final int MOST_ROOM_NAME = 48;
	/** The most a gzipped list may grow to when it is opened. */
	public static final int MOST_TEXT_BYTES = 4 * 1024 * 1024;
	/** Points lie within a room: a few rooms across at most. */
	static final int MOST_OFFSET = 512;
	/** A point's box may reach a few blocks past its own block (Odin's lists have boxes three blocks tall). */
	static final double MOST_BOX_REACH = 8.0;
	static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

	private DungeonWaypointFormat() {
	}

	/**
	 * Reads a list: Lumio's own, or Odin's JSON or Base64 export, turned into Lumio's room model.
	 *
	 * @param shapeOf a room's shape by its name, or null for a room not known (its points are left out)
	 * @return the points by room name, in the order read; empty when nothing could be read
	 */
	public static Map<String, List<DungeonWaypoint>> read(String text, Function<String, String> shapeOf) {
		JsonObject root = parse(text);
		if (root == null) {
			return new LinkedHashMap<>();
		}
		if (!root.has(MARK)) {
			return OdinWaypoints.fromOdin(root, shapeOf);
		}
		boolean fromCorner = root.get(MARK) instanceof JsonPrimitive format && format.isNumber() && format.getAsInt() == FORMAT_FROM_CORNER;
		Map<String, List<DungeonWaypoint>> rooms = new LinkedHashMap<>();
		if (!(root.get("rooms") instanceof JsonObject byRoom)) {
			return rooms;
		}
		for (Map.Entry<String, JsonElement> entry : byRoom.entrySet()) {
			String room = roomName(entry.getKey());
			if (rooms.size() >= MOST_ROOMS || room == null || !(entry.getValue() instanceof JsonArray array)) {
				continue;
			}
			String shape = fromCorner ? shapeOf.apply(room) : null;
			if (fromCorner && shape == null) {
				OdinWaypoints.unknownRoom(room);
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
			// a room without points is kept too, so a list comes back as it was
			rooms.put(room, points);
		}
		return rooms;
	}

	/**
	 * What an import found, for the question before anything changes: the format (Odin's or Lumio's, plain or packed), the rooms and points read,
	 * and how many rooms were left out because the room data does not know them.
	 */
	public record ImportSummary(String format, boolean packed, Map<String, List<DungeonWaypoint>> rooms, int skipped) {
		public int points() {
			int points = 0;
			for (List<DungeonWaypoint> room : rooms.values()) {
				points += room.size();
			}
			return points;
		}

		/** @return one line of what was found: "140 rooms · 1,186 points · 2 room names not known (skipped)" */
		public String line() {
			String text = String.format(java.util.Locale.ROOT, "%,d rooms · %,d points", rooms.size(), points());
			return skipped == 0 ? text : text + " · " + skipped + (skipped == 1 ? " room name" : " room names") + " not known (skipped)";
		}
	}

	/** @return what a text holds, read and recognized by itself; null when it is no list of either format */
	public static ImportSummary summarize(String text, Function<String, String> shapeOf) {
		JsonObject root = parse(text);
		if (root == null) {
			return null;
		}
		boolean packed = !text.strip().startsWith("{");
		boolean lumio = root.has(MARK);
		Map<String, List<DungeonWaypoint>> rooms = read(text, shapeOf);
		int skipped = 0;
		JsonObject byRoom = lumio ? (root.get("rooms") instanceof JsonObject inner ? inner : new JsonObject()) : root;
		boolean fromCorner = lumio && root.get(MARK) instanceof JsonPrimitive format && format.isNumber() && format.getAsInt() == FORMAT_FROM_CORNER;
		if (!lumio || fromCorner) {
			for (Map.Entry<String, JsonElement> entry : byRoom.entrySet()) {
				String room = roomName(entry.getKey());
				if (room != null && entry.getValue() instanceof JsonArray array && !array.isEmpty() && !rooms.containsKey(room) && shapeOf.apply(room) == null) {
					skipped++;
				}
			}
		}
		if (rooms.isEmpty() && skipped == 0 && !lumio && !looksLikeOdin(root)) {
			return null;
		}
		return new ImportSummary(lumio ? "Lumio" : "Odin", packed, rooms, skipped);
	}

	/** @return whether an object is shaped like Odin's list: rooms as lists of points */
	private static boolean looksLikeOdin(JsonObject root) {
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!(entry.getValue() instanceof JsonArray)) {
				return false;
			}
		}
		return !root.isEmpty();
	}

	/** @return the points in Lumio's own format, with the rooms switched off */
	public static String write(Map<String, List<DungeonWaypoint>> rooms, java.util.Collection<String> off, Map<String, Integer> fromOdin) {
		JsonObject root = JsonParser.parseString(write(rooms)).getAsJsonObject();
		if (!fromOdin.isEmpty()) {
			JsonObject counts = new JsonObject();
			fromOdin.forEach(counts::addProperty);
			root.add("from_odin", counts);
		}
		if (!off.isEmpty()) {
			JsonArray names = new JsonArray();
			off.forEach(names::add);
			root.add("off_rooms", names);
		}
		return PRETTY.toJson(root);
	}

	/** @return how many points of each room came from an Odin import, in Lumio's own list */
	public static Map<String, Integer> fromOdin(String text) {
		Map<String, Integer> counts = new java.util.TreeMap<>();
		JsonObject root = parse(text);
		if (root != null && root.has(MARK) && root.get("from_odin") instanceof JsonObject json) {
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				String room = roomName(entry.getKey());
				if (room != null && counts.size() < MOST_ROOMS && entry.getValue() instanceof JsonPrimitive number && number.isNumber()) {
					counts.put(room, Math.clamp(number.getAsInt(), 0, MOST_POINTS_PER_ROOM));
				}
			}
		}
		return counts;
	}

	/** @return the rooms switched off in Lumio's own list; none in any other */
	public static java.util.Set<String> offRooms(String text) {
		java.util.Set<String> off = new java.util.TreeSet<>();
		JsonObject root = parse(text);
		if (root != null && root.has(MARK) && root.get("off_rooms") instanceof JsonArray names) {
			for (JsonElement name : names) {
				String room = name instanceof JsonPrimitive primitive && primitive.isString() ? roomName(primitive.getAsString()) : null;
				if (room != null && off.size() < MOST_ROOMS) {
					off.add(room);
				}
			}
		}
		return off;
	}

	/** @return the points in Lumio's own format */
	public static String write(Map<String, List<DungeonWaypoint>> rooms) {
		JsonObject root = new JsonObject();
		root.addProperty(MARK, FORMAT);
		JsonObject byRoom = new JsonObject();
		for (Map.Entry<String, List<DungeonWaypoint>> room : rooms.entrySet()) {
			JsonArray array = new JsonArray();
			for (DungeonWaypoint point : room.getValue()) {
				JsonObject json = new JsonObject();
				json.addProperty("x", point.x());
				json.addProperty("y", point.y());
				json.addProperty("z", point.z());
				json.addProperty("color", color(point.argb()));
				json.addProperty("filled", point.filled());
				json.addProperty("through_walls", point.throughWalls());
				JsonArray box = new JsonArray();
				box.add(point.minX());
				box.add(point.minY());
				box.add(point.minZ());
				box.add(point.maxX());
				box.add(point.maxY());
				box.add(point.maxZ());
				json.add("box", box);
				if (!point.title().isEmpty()) {
					json.addProperty("title", point.title());
				}
				json.addProperty("type", point.type().name().toLowerCase(Locale.ROOT));
				json.addProperty("hide_found", point.hideFound());
				array.add(json);
			}
			byRoom.add(room.getKey(), array);
		}
		root.add("rooms", byRoom);
		return PRETTY.toJson(root);
	}

	/** A point of Lumio's list; with a shape, one of the first format, counted from the room's corner, moved to the middle. */
	private static DungeonWaypoint point(JsonObject json, String cornerShape) {
		if (!number(json, "x") || !number(json, "y") || !number(json, "z")) {
			return null;
		}
		double[] box = {0, 0, 0, 1, 1, 1};
		if (json.get("box") instanceof JsonArray array && array.size() == box.length) {
			for (int i = 0; i < box.length; i++) {
				box[i] = boxValue(array.get(i), box[i]);
			}
		}
		int[] at = {offset(json, "x"), offset(json, "y"), offset(json, "z")};
		boolean throughWalls = bool(json, "through_walls", false);
		if (cornerShape != null) {
			at = RoomModel.fromCorner(cornerShape, at[0], at[1], at[2]);
			throughWalls = !bool(json, "depth", false);
		}
		return new DungeonWaypoint(at[0], at[1], at[2], argb(json.get("color")), bool(json, "filled", false), throughWalls, box[0], box[1], box[2],
			box[3], box[4], box[5], text(json.get("title")), type(json), bool(json, "hide_found", true));
	}

	// ------------------------------------------------------------------------------------------------------ shared with the Odin converter

	/** @return the text's JSON object, unpacked first where it is Base64, or null */
	static JsonObject parse(String text) {
		if (text == null) {
			return null;
		}
		String input = text.strip();
		if (input.length() > MOST_TEXT_BYTES) {
			return null;
		}
		try {
			if (!input.startsWith("{")) {
				input = unpack(input);
			}
			return JsonParser.parseString(input) instanceof JsonObject object ? object : null;
		} catch (RuntimeException | IOException e) {
			return null;
		}
	}

	/** @return a room's name, cut to its limit, or null for a blank one */
	static String roomName(String name) {
		if (name.isBlank()) {
			return null;
		}
		return name.length() > MOST_ROOM_NAME ? name.substring(0, MOST_ROOM_NAME) : name;
	}

	static DungeonWaypoint.Type type(JsonObject json) {
		return json.get("type") instanceof JsonPrimitive kind && kind.isString() ? DungeonWaypoint.Type.of(kind.getAsString())
			: bool(json, "secret", false) ? DungeonWaypoint.Type.SECRET : DungeonWaypoint.Type.NORMAL;
	}

	/** @return "#RRGGBBAA" of an ARGB color */
	static String color(int argb) {
		return String.format(Locale.ROOT, "#%06X%02X", argb & 0xFFFFFF, argb >>> 24);
	}

	/** @return the ARGB color of "#RRGGBBAA" or "#RRGGBB", or opaque white */
	static int argb(JsonElement element) {
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return 0xFFFFFFFF;
		}
		String hex = primitive.getAsString().strip();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		try {
			if (hex.length() == 8) {
				long value = Long.parseLong(hex, 16);
				return (int) ((value & 0xFF) << 24 | value >>> 8);
			}
			if (hex.length() == 6) {
				return 0xFF000000 | Integer.parseInt(hex, 16);
			}
		} catch (NumberFormatException e) {
			return 0xFFFFFFFF;
		}
		return 0xFFFFFFFF;
	}

	static double boxValue(JsonElement element, double fallback) {
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			double value = primitive.getAsDouble();
			return Double.isFinite(value) ? Math.clamp(value, -MOST_BOX_REACH, 1.0 + MOST_BOX_REACH) : fallback;
		}
		return fallback;
	}

	static boolean number(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isNumber() && Double.isFinite(primitive.getAsDouble());
	}

	static int offset(JsonObject json, String key) {
		return (int) Math.clamp(json.get(key).getAsDouble(), -MOST_OFFSET, MOST_OFFSET);
	}

	static boolean bool(JsonObject json, String key, boolean fallback) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	static String text(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString().strip() : "";
	}

	/**
	 * @return a point's title from a list: a text, a number (some lists keep a secret's number as one), or a chat text object's "text"; "" for
	 *         anything else
	 */
	static String title(JsonElement element) {
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			return primitive.getAsString();
		}
		if (element instanceof JsonObject object) {
			return text(object.get("text"));
		}
		return text(element);
	}

	static String pack(String text) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			gzip.write(text.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	/** @return a Base64 text unpacked from gzip, read at most to its limit */
	static String unpack(String base64) throws IOException {
		byte[] packed = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
			byte[] bytes = in.readNBytes(MOST_TEXT_BYTES + 1);
			if (bytes.length > MOST_TEXT_BYTES) {
				throw new IOException("too large");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}
}
