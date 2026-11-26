package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.util.Logs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The room of the Catacombs the player stands in: its name and kind, and how it is turned, so points can be kept relative to it. A room is known by
 * the fingerprint of the middle of its tile ({@link RoomCore}), looked up in the bundled room data, and turned: a room of one tile by its blue clay
 * corner, a bigger one by the way its tiles lie ({@link RoomLayout}). Places in a room are counted from that corner as if the room lay the way of
 * {@link Rotation#SOUTH}. Here only the room the player is in is read; the other tiles are told by {@link DungeonScan}, which gives nothing of
 * a room out before it is discovered.
 */
public final class CurrentRoom {
	/** The dungeon is a grid of 6 by 6 tiles of 32 blocks; the middle column of a tile lies at tile * 32 - 185. */
	static final int GRID = 6;
	static final int TILE = 32;
	static final int TILE_ORIGIN = -185;
	private static final int CHECK_TICKS = 5;
	/** The order the corners of a room of one tile are looked at, always the same, so a room with more than one clay block turns the same way every run. */
	private static final Rotation[] CLAY_ORDER = {Rotation.NORTH, Rotation.SOUTH, Rotation.WEST, Rotation.EAST};
	private static final String DATA = "/assets/lumio-skyblock/skyblock/dungeon/rooms.json";

	/** What a room is, from the room data. */
	public record RoomData(String name, String type, String shape, int tiles, int secrets, int crypts) {
	}

	/**
	 * How a room is turned: how many quarter turns its places are from the way the room lies unturned ({@link #SOUTH}), and on which corner of a tile its
	 * clay lies (one step across and one down, each -1 or 1).
	 */
	public enum Rotation {
		SOUTH(0, -1, -1), WEST(1, 1, -1), NORTH(2, 1, 1), EAST(3, -1, 1);

		private final int quarterTurns;
		final int cornerX;
		final int cornerZ;

		Rotation(int quarterTurns, int cornerX, int cornerZ) {
			this.quarterTurns = quarterTurns;
			this.cornerX = cornerX;
			this.cornerZ = cornerZ;
		}

		/** @return a step across and down in the world, turned into the room's own count */
		int[] intoRoom(int x, int z) {
			return turn(x, z, quarterTurns);
		}

		/** @return a step across and down in the room's own count, turned into the world */
		int[] intoWorld(int x, int z) {
			return turn(x, z, (4 - quarterTurns) % 4);
		}

		/** The same for a step within a block (a corner of a box), measured from the block's middle. */
		public float[] intoWorld(float x, float z) {
			float across = x;
			float down = z;
			for (int i = 0; i < (4 - quarterTurns) % 4; i++) {
				float turned = down;
				down = -across;
				across = turned;
			}
			return new float[] {across, down};
		}

		private static int[] turn(int x, int z, int times) {
			int across = x;
			int down = z;
			for (int i = 0; i < times; i++) {
				int turned = down;
				down = -across;
				across = turned;
			}
			return new int[] {across, down};
		}
	}

	private static volatile Map<Integer, RoomData> byCore;
	private static volatile Map<String, String> shapeByName;
	private static volatile List<RoomData> allRooms;
	private static RoomData data;
	private static Rotation rotation;
	private static int clayX;
	private static int clayZ;
	private static int tileX = -1;
	private static int tileZ = -1;
	private static int ticks;
	/** The bounds of the room the player is in, in blocks, and whether they are known. */
	private static int minX;
	private static int maxX;
	private static int minZ;
	private static int maxZ;
	private static boolean boundsKnown;
	private static final StringBuilder FINGERPRINT = new StringBuilder(4096);
	private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();

	private CurrentRoom() {
	}

	/** @return the room the player is in, or null outside a known room */
	public static RoomData data() {
		return rotation == null ? null : data;
	}

	/** @return the room's name, or "" */
	public static String name() {
		RoomData room = data();
		return room == null ? "" : room.name();
	}

	public static Rotation rotation() {
		return rotation;
	}

	/** Turns a place in the world into one relative to the room; the height stays. */
	public static int[] toRoom(int x, int y, int z) {
		return toRoom(x, y, z, clayX, clayZ, rotation);
	}

	/** Turns a place relative to the room back into the world. */
	public static int[] toWorld(int x, int y, int z) {
		return toWorld(x, y, z, clayX, clayZ, rotation);
	}

	/** Turns a place in the world into Lumio's room model ({@link RoomModel}: from the room's middle). */
	public static int[] toModel(int x, int y, int z) {
		int[] fromCorner = toRoom(x, y, z);
		return RoomModel.fromCorner(data.shape(), fromCorner[0], fromCorner[1], fromCorner[2]);
	}

	/** Turns a place of Lumio's room model back into the world. */
	public static int[] modelToWorld(int x, int y, int z) {
		return modelToWorld(x, y, z, data.shape(), clayX, clayZ, rotation);
	}

	static int[] modelToWorld(int x, int y, int z, String shape, int cornerX, int cornerZ, Rotation turned) {
		int[] fromCorner = RoomModel.toCorner(shape, x, y, z);
		return toWorld(fromCorner[0], fromCorner[1], fromCorner[2], cornerX, cornerZ, turned);
	}

	/** @return every room the room data knows, one per name, in name order */
	public static List<RoomData> allRooms() {
		if (allRooms != null) {
			return allRooms;
		}
		if (byCore == null) {
			byCore = load();
		}
		Map<String, RoomData> byName = new java.util.TreeMap<>();
		for (RoomData room : byCore.values()) {
			byName.putIfAbsent(room.name(), room);
		}
		allRooms = List.copyOf(byName.values());
		return allRooms;
	}

	/** @return the shape of a room by its name ("1x1", "L"...), or null for a name not in the room data */
	public static String shapeOf(String roomName) {
		if (shapeByName == null) {
			if (byCore == null) {
				byCore = load();
			}
			Map<String, String> shapes = new HashMap<>();
			for (RoomData room : byCore.values()) {
				shapes.put(room.name(), room.shape());
			}
			shapeByName = shapes;
		}
		return shapeByName.get(roomName);
	}

	static int[] toRoom(int x, int y, int z, int cornerX, int cornerZ, Rotation turned) {
		int[] step = turned.intoRoom(x - cornerX, z - cornerZ);
		return new int[] {step[0], y, step[1]};
	}

	static int[] toWorld(int x, int y, int z, int cornerX, int cornerZ, Rotation turned) {
		int[] step = turned.intoWorld(x, z);
		return new int[] {step[0] + cornerX, y, step[1] + cornerZ};
	}

	/** @return the tile a block coordinate lies in, across or down, which may be outside the grid */
	static int tileOf(int coordinate) {
		return Math.floorDiv(coordinate - TILE_ORIGIN + TILE / 2, TILE);
	}

	/** @return the middle column of a tile, across or down */
	static int middleOf(int tile) {
		return TILE_ORIGIN + tile * TILE;
	}

	static void tick(Minecraft client) {
		if (++ticks % CHECK_TICKS != 0 || client.player == null || client.level == null || Dungeon.inBoss()) {
			return;
		}
		int x = tileOf(client.player.getBlockX());
		int z = tileOf(client.player.getBlockZ());
		if (x < 0 || x >= GRID || z < 0 || z >= GRID) {
			reset();
			return;
		}
		if (x == tileX && z == tileZ && rotation != null) {
			return;
		}
		tileX = x;
		tileZ = z;
		identify(client.level, x, z);
	}

	static void reset() {
		data = null;
		boundsKnown = false;
		rotation = null;
		tileX = -1;
		tileZ = -1;
	}

	/** Keeps the room's bounds in blocks, from its tiles. */
	private static void bounds(int left, int right, int top, int bottom) {
		minX = middleOf(left) - RoomLayout.CORNER;
		maxX = middleOf(right) + RoomLayout.CORNER + 1;
		minZ = middleOf(top) - RoomLayout.CORNER;
		maxZ = middleOf(bottom) + RoomLayout.CORNER + 1;
		boundsKnown = true;
	}

	/**
	 * @return whether a place is in the room the player is in, or within a margin of it (its doorways); everywhere while the room is not known, so
	 *         nothing is left out then
	 */
	public static boolean inRoom(double x, double z, double margin) {
		return !boundsKnown || x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
	}

	/**
	 * @return whether a doorway at this column is one of the room the player is in: on the room's edge, where its doors lie between its tiles and the
	 *         next. Never while the room is not known, so no door of another room is ever taken for one of this room.
	 */
	public static boolean ownsDoor(int x, int z) {
		return boundsKnown && doorOf(x, z, minX, maxX, minZ, maxZ);
	}

	/** @return whether a doorway at this column lies on the edge of a room of these bounds (a block beyond them at most) */
	static boolean doorOf(int x, int z, int minX, int maxX, int minZ, int maxZ) {
		return x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1;
	}

	private static void identify(ClientLevel level, int x, int z) {
		int[] roof = new int[1];
		RoomData found = lookup(fingerprint(level, x, z, roof));
		data = found;
		rotation = null;
		boundsKnown = false;
		if (found == null) {
			return;
		}
		bounds(x, x, z, z);
		if ("Fairy".equals(found.name())) {
			// the Fairy room has no clay: it always lies unturned
			place(RoomLayout.Placement.onTile(Rotation.SOUTH, x, z));
			return;
		}
		if (found.tiles() == 1) {
			findClay(level, x, z, roof[0]);
			return;
		}
		// a bigger room is turned by the way its tiles lie, the same whatever order its tiles were found in ({@link RoomLayout})
		List<int[]> tiles = tilesOfRoom(level, found, x, z);
		if (tiles.size() == found.tiles()) {
			int left = x;
			int right = x;
			int top = z;
			int bottom = z;
			for (int[] tile : tiles) {
				left = Math.min(left, tile[0]);
				right = Math.max(right, tile[0]);
				top = Math.min(top, tile[1]);
				bottom = Math.max(bottom, tile[1]);
			}
			bounds(left, right, top, bottom);
			place(RoomLayout.of(found.shape(), tiles));
		}
	}

	/** @return the tiles of the room the player stands in: the neighbors with the same room, as far as the room reaches */
	private static List<int[]> tilesOfRoom(ClientLevel level, RoomData room, int x, int z) {
		List<int[]> tiles = new ArrayList<>();
		tiles.add(new int[] {x, z});
		for (int i = 0; i < tiles.size() && tiles.size() < room.tiles(); i++) {
			int[] tile = tiles.get(i);
			for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nextX = tile[0] + step[0];
				int nextZ = tile[1] + step[1];
				if (nextX >= 0 && nextZ >= 0 && nextX < GRID && nextZ < GRID && !contains(tiles, nextX, nextZ)
					&& lookup(fingerprint(level, nextX, nextZ, new int[1])) == room) {
					tiles.add(new int[] {nextX, nextZ});
				}
			}
		}
		return tiles;
	}

	/** Turns a room of one tile by the blue clay on one of its corners, at the height of its roof. */
	private static void findClay(ClientLevel level, int x, int z, int height) {
		Block clay = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE);
		for (Rotation each : CLAY_ORDER) {
			RoomLayout.Placement candidate = RoomLayout.Placement.onTile(each, x, z);
			if (level.getBlockState(AT.set(candidate.clayX(), height, candidate.clayZ())).getBlock() == clay) {
				place(candidate);
				return;
			}
		}
	}

	private static void place(RoomLayout.Placement placement) {
		if (placement != null) {
			rotation = placement.rotation();
			clayX = placement.clayX();
			clayZ = placement.clayZ();
		}
	}

	private static boolean contains(List<int[]> tiles, int x, int z) {
		for (int[] tile : tiles) {
			if (tile[0] == x && tile[1] == z) {
				return true;
			}
		}
		return false;
	}

	/** @return the hash of the fingerprint of a tile's middle, or 0 where its chunk is not there */
	private static int fingerprint(ClientLevel level, int x, int z, int[] roofOut) {
		int middleX = middleOf(x);
		int middleZ = middleOf(z);
		AT.set(middleX, RoomCore.TOP, middleZ);
		if (!level.isLoaded(AT)) {
			return 0;
		}
		RoomCore.Column column = new RoomCore.Column() {
			@Override
			public RoomCore.Layer layer(int y) {
				BlockState state = level.getBlockState(AT.set(middleX, y, middleZ));
				Block block = state.getBlock();
				if (state.isAir()) {
					return RoomCore.Layer.EMPTY;
				}
				if (block == Blocks.GOLD_BLOCK) {
					return RoomCore.Layer.GOLD;
				}
				if (block == Blocks.BEDROCK) {
					return RoomCore.Layer.BEDROCK;
				}
				return block == Blocks.OAK_PLANKS || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST ? RoomCore.Layer.LEFT_OUT : RoomCore.Layer.BLOCK;
			}

			@Override
			public String name(int y) {
				return level.getBlockState(AT.set(middleX, y, middleZ)).getBlock().toString();
			}
		};
		return RoomCore.text(column, roofOut, FINGERPRINT).hashCode();
	}

	/** @return whether the chunk of a tile's middle is loaded, so the tile can be told */
	static boolean chunkLoaded(ClientLevel level, int x, int z) {
		return level.isLoaded(AT.set(middleOf(x), RoomCore.TOP, middleOf(z)));
	}

	/** @return the room a tile's middle column is the fingerprint of, or null: no room there, or one the room data does not know. For {@link DungeonScan}. */
	static RoomData roomAt(ClientLevel level, int x, int z) {
		return lookup(fingerprint(level, x, z, new int[1]));
	}

	private static RoomData lookup(int fingerprint) {
		if (fingerprint == 0) {
			return null;
		}
		if (byCore == null) {
			byCore = load();
		}
		return byCore.get(fingerprint);
	}

	/** Reads the room data shipped with the add-on: every fingerprint of every room. */
	static Map<Integer, RoomData> load() {
		Map<Integer, RoomData> map = new HashMap<>();
		try (InputStream in = CurrentRoom.class.getResourceAsStream(DATA)) {
			if (in == null) {
				return map;
			}
			JsonArray rooms = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement element : rooms) {
				JsonObject room = element.getAsJsonObject();
				String shape = room.get("shape").getAsString();
				RoomData read = new RoomData(room.get("name").getAsString(), room.get("type").getAsString(), shape, tilesOf(shape),
					room.has("maxSecrets") ? room.get("maxSecrets").getAsInt() : 0, room.has("crypts") ? room.get("crypts").getAsInt() : 0);
				for (JsonElement fingerprint : room.getAsJsonArray("cores")) {
					map.put(fingerprint.getAsInt(), read);
				}
			}
		} catch (Exception e) {
			Logs.warn("Could not read the dungeon rooms", e);
		}
		return map;
	}

	static int tilesOf(String shape) {
		return switch (shape) {
			case "1x2" -> 2;
			case "1x3", "L" -> 3;
			case "1x4", "2x2" -> 4;
			default -> 1;
		};
	}
}
