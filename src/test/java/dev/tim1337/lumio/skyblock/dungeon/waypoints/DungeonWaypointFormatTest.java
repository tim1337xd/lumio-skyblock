package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * A small list in Odin's dungeon waypoint format, made up for the test (5 rooms, 8 points: secrets and plain points, filled boxes, a
 * half box, titles as text and as numbers, and a room no room data knows) imports into Lumio's own format, goes back to Odin's unchanged, and
 * Lumio's own list comes back the same after saving and loading. Where the points land in the world is {@code RoomLayoutTest}'s part.
 */
class DungeonWaypointFormatTest {
	private static final String UNKNOWN = "A Room Nobody Named";

	private static String example() throws IOException {
		try (InputStream in = DungeonWaypointFormatTest.class.getResourceAsStream("/dungeon/odin-waypoints.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, List<DungeonWaypoint>> imported() throws IOException {
		return DungeonWaypointFormat.read(example(), CurrentRoom::shapeOf);
	}

	/** @return the points with an empty title and the type NORMAL where Odin left them out, which reads the same */
	private static JsonElement withTitles(JsonElement points) {
		JsonElement copy = points.deepCopy();
		for (JsonElement point : copy.getAsJsonArray()) {
			if (!point.getAsJsonObject().has("title")) {
				point.getAsJsonObject().addProperty("title", "");
			}
			if (!point.getAsJsonObject().has("type")) {
				point.getAsJsonObject().addProperty("type", "NORMAL");
			}
		}
		return copy;
	}

	private static int count(Map<String, List<DungeonWaypoint>> rooms) {
		return rooms.values().stream().mapToInt(List::size).sum();
	}

	@Test
	void importsEveryRoomAndPoint() throws IOException {
		Map<String, List<DungeonWaypoint>> rooms = imported();
		assertEquals(5, rooms.size());
		assertEquals(8, count(rooms));
		assertTrue(rooms.get(UNKNOWN).isEmpty(), "a room without points comes along even where no room data knows it");
		// Temple is a room of one tile: Odin's (5, 63, 23) from the corner is (-10, 63, 8) from the middle
		DungeonWaypoint first = rooms.get("Temple").getFirst();
		assertEquals(-10, first.x());
		assertEquals(63, first.y());
		assertEquals(8, first.z());
		assertEquals(0xFFFF5555, first.argb());
		assertTrue(first.throughWalls(), "Odin's depth off is through walls");
		assertEquals(1.0, first.maxY());
	}

	@Test
	void odinToLumioToOdinGivesTheSamePoints() throws IOException {
		JsonObject original = JsonParser.parseString(example()).getAsJsonObject();
		JsonObject back = JsonParser.parseString(OdinWaypoints.writeJson(imported(), CurrentRoom::shapeOf)).getAsJsonObject();
		assertEquals(original.keySet(), back.keySet());
		for (Map.Entry<String, JsonElement> room : original.entrySet()) {
			assertEquals(withTitles(room.getValue()), withTitles(back.get(room.getKey())), room.getKey());
		}
		String packed = OdinWaypoints.write(imported(), CurrentRoom::shapeOf);
		assertFalse(packed.startsWith("{"));
		assertEquals(imported(), DungeonWaypointFormat.read(packed, CurrentRoom::shapeOf));
	}

	@Test
	void lumiosSaveLoadsTheSame() throws IOException {
		Map<String, List<DungeonWaypoint>> rooms = imported();
		String saved = DungeonWaypointFormat.write(rooms);
		Map<String, List<DungeonWaypoint>> again = DungeonWaypointFormat.read(saved, CurrentRoom::shapeOf);
		assertEquals(rooms, again);
		assertEquals(saved, DungeonWaypointFormat.write(again));
	}

	@Test
	void theFirstFormatMovesToTheMiddle() {
		Map<String, List<DungeonWaypoint>> rooms = DungeonWaypointFormat.read("{\"lumio_dungeon_waypoints\":1,\"rooms\":{\"Temple\":[{\"x\":15,\"y\":70,"
			+ "\"z\":16,\"color\":\"#55FF55FF\",\"filled\":false,\"depth\":true,\"box\":[0,0,0,1,1,1],\"type\":\"secret\"}],\"Nowhere\":[]}}",
			CurrentRoom::shapeOf);
		DungeonWaypoint point = rooms.get("Temple").getFirst();
		assertEquals(0, point.x());
		assertEquals(1, point.z());
		assertFalse(point.throughWalls());
		assertEquals(DungeonWaypoint.Type.SECRET, point.type());
		assertFalse(rooms.containsKey("Nowhere"), "a room not in the room data is left out");
	}

	@Test
	void colorsTypesAndUnknownRooms() {
		assertEquals("#55FF5580", DungeonWaypointFormat.color(0x8055FF55));
		Map<String, List<DungeonWaypoint>> rooms = DungeonWaypointFormat.read("{\"Temple\":[{\"x\":1,\"y\":2,\"z\":3,\"color\":\"#00AAFF80\",\"filled\":true,"
			+ "\"depth\":true,\"aabb\":{\"minX\":0.25,\"minY\":0,\"minZ\":0.25,\"maxX\":0.75,\"maxY\":0.5,\"maxZ\":0.75},\"title\":\"Lever\",\"type\":\"SECRET\"},"
			+ "{\"x\":9999999,\"y\":0,\"z\":0,\"color\":\"nope\",\"filled\":false,\"depth\":false,\"aabb\":{},\"title\":\"\",\"type\":\"NONE\"}],"
			+ "\"Not A Room\":[{\"x\":1,\"y\":2,\"z\":3}]}", CurrentRoom::shapeOf);
		assertFalse(rooms.containsKey("Not A Room"));
		DungeonWaypoint point = rooms.get("Temple").getFirst();
		assertEquals(0x8000AAFF, point.argb());
		assertTrue(point.filled());
		assertFalse(point.throughWalls());
		assertEquals(0.25, point.minX());
		assertEquals(DungeonWaypoint.Type.SECRET, point.type());
		DungeonWaypoint odd = rooms.get("Temple").get(1);
		assertEquals(DungeonWaypointFormat.MOST_OFFSET - 15, odd.x());
		assertEquals(0xFFFFFFFF, odd.argb());
		assertEquals(DungeonWaypoint.Type.NORMAL, odd.type());
	}

	@Test
	void editsRoundTrip() {
		DungeonWaypoint edited = DungeonWaypoint.block(-12, 71, 9, "Bat spawn", DungeonWaypoint.Type.SECRET).withType(DungeonWaypoint.Type.BAT)
			.withColor(0xC0FF6EC7).withFilled(true).withThroughWalls(true).withHideFound(false).withBox(false, true);
		Map<String, List<DungeonWaypoint>> rooms = Map.of("Temple", List.of(edited, DungeonWaypoint.block(0, 70, 0, "", DungeonWaypoint.Type.LEVER)));
		// Lumio's own save and load keep every field
		assertEquals(rooms, DungeonWaypointFormat.read(DungeonWaypointFormat.write(rooms, java.util.Set.of("Temple"), Map.of("Temple", 1)),
			CurrentRoom::shapeOf));
		assertEquals(java.util.Set.of("Temple"), DungeonWaypointFormat.offRooms(DungeonWaypointFormat.write(rooms, java.util.Set.of("Temple"), Map.of())));
		// Odin's export and import keep what Odin's format has (all but "hide once found", which comes back on)
		DungeonWaypoint back = DungeonWaypointFormat.read(OdinWaypoints.write(rooms, CurrentRoom::shapeOf), CurrentRoom::shapeOf).get("Temple").getFirst();
		assertEquals(edited.withHideFound(true), back);
	}

	@Test
	void titlesSurviveOdinToLumioToOdin() {
		// Odin's titles often hold the secret's number: as text, as a number, or with color codes
		Map<String, List<DungeonWaypoint>> rooms = DungeonWaypointFormat.read("{\"Temple\":["
			+ "{\"x\":5,\"y\":63,\"z\":23,\"title\":\"1\",\"type\":\"SECRET\"},"
			+ "{\"x\":6,\"y\":63,\"z\":23,\"title\":2,\"type\":\"SECRET\"},"
			+ "{\"x\":7,\"y\":63,\"z\":23,\"title\":\"Lever room\"},"
			+ "{\"x\":8,\"y\":63,\"z\":23}]}", CurrentRoom::shapeOf);
		List<DungeonWaypoint> temple = rooms.get("Temple");
		assertEquals(List.of("1", "2", "Lever room", ""), temple.stream().map(DungeonWaypoint::title).toList());
		// ours: saved and loaded
		Map<String, List<DungeonWaypoint>> ours = DungeonWaypointFormat.read(DungeonWaypointFormat.write(rooms), CurrentRoom::shapeOf);
		assertEquals(rooms, ours);
		// and Odin's again
		JsonObject odin = JsonParser.parseString(OdinWaypoints.writeJson(ours, CurrentRoom::shapeOf)).getAsJsonObject();
		assertEquals("1", odin.getAsJsonArray("Temple").get(0).getAsJsonObject().get("title").getAsString());
		assertEquals("2", odin.getAsJsonArray("Temple").get(1).getAsJsonObject().get("title").getAsString());
		assertEquals("Lever room", odin.getAsJsonArray("Temple").get(2).getAsJsonObject().get("title").getAsString());
	}

	@Test
	void nothingFromBrokenText() {
		assertTrue(DungeonWaypointFormat.read("not a list", CurrentRoom::shapeOf).isEmpty());
		assertTrue(DungeonWaypointFormat.read("[1,2]", CurrentRoom::shapeOf).isEmpty());
		assertTrue(DungeonWaypointFormat.read(null, CurrentRoom::shapeOf).isEmpty());
	}

	@Test
	void theFormatIsRecognizedAndSummed() throws IOException {
		// Odin's list, plain and packed
		DungeonWaypointFormat.ImportSummary odin = DungeonWaypointFormat.summarize(example(), CurrentRoom::shapeOf);
		assertEquals("Odin", odin.format());
		assertFalse(odin.packed());
		assertEquals(5, odin.rooms().size());
		assertEquals(8, odin.points());
		assertEquals(0, odin.skipped());
		DungeonWaypointFormat.ImportSummary packed = DungeonWaypointFormat.summarize(OdinWaypoints.write(imported(), CurrentRoom::shapeOf), CurrentRoom::shapeOf);
		assertEquals("Odin", packed.format());
		assertTrue(packed.packed());
		// Lumio's own list
		DungeonWaypointFormat.ImportSummary lumio = DungeonWaypointFormat.summarize(DungeonWaypointFormat.write(imported()), CurrentRoom::shapeOf);
		assertEquals("Lumio", lumio.format());
		assertEquals(8, lumio.points());
		// rooms the room data does not know are counted as skipped
		DungeonWaypointFormat.ImportSummary unknown = DungeonWaypointFormat.summarize("{\"Temple\":[{\"x\":5,\"y\":63,\"z\":23}],"
			+ "\"Not A Room\":[{\"x\":1,\"y\":2,\"z\":3}],\"Also Not\":[{\"x\":1,\"y\":2,\"z\":3}]}", CurrentRoom::shapeOf);
		assertEquals(2, unknown.skipped());
		assertEquals("1 rooms · 1 points · 2 room names not known (skipped)", unknown.line());
		// no list at all
		assertNull(DungeonWaypointFormat.summarize("hello", CurrentRoom::shapeOf));
		assertNull(DungeonWaypointFormat.summarize("{\"a\":1}", CurrentRoom::shapeOf));
	}
}
