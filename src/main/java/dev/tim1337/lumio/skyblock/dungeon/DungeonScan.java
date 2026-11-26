package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom.RoomData;

import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;

/**
 * The rooms of a dungeon told from the chunks the client has loaded, in the background: once per tile, by the fingerprint of the tile's middle
 * column ({@link RoomCore}), a tile or two per tick, never while drawing. This is the one exception to "no chunk scanning", allowed by the
 * player for dungeons only, with these limits, which {@link Rooms} holds:
 * <ul>
 * <li>What the scan knows of a room is given out only once the room counts as discovered: the game's own map item no longer marks it as
 * undiscovered, or the player has walked into it. Until then nothing of it is shown, highlighted, used for waypoints, door outlines or hints,
 * or handed to the API.</li>
 * <li>The only use before that: totals the game reveals anyway (the dungeon's secrets, for the score).</li>
 * <li>Nothing of it is shared with other players, no room is guessed to hold the Mimic, and no kind is guessed for a room the map marks as
 * unknown.</li>
 * </ul>
 */
public final class DungeonScan {
	private static final int TILES = CurrentRoom.GRID * CurrentRoom.GRID;
	/** How many tiles are told per tick at most. */
	private static final int PER_TICK = 2;

	/**
	 * What the scan knows, and what of it may be given out. Plain data, tested without the game.
	 */
	static final class Rooms {
		private final RoomData[] scanned = new RoomData[TILES];
		private final boolean[] done = new boolean[TILES];
		private final boolean[] discovered = new boolean[TILES];
		private final boolean[] walked = new boolean[TILES];

		/** A tile was told: its room, or null for a tile without a room (or one the room data does not know). */
		void scanned(int tile, RoomData room) {
			scanned[tile] = room;
			done[tile] = true;
		}

		boolean done(int tile) {
			return done[tile];
		}

		/** The game's map item shows the tile's room as discovered (it has it, and not as unexplored). */
		void mapShows(int tile) {
			discovered[tile] = true;
		}

		/** The player stands in the tile. */
		void walkedInto(int tile) {
			discovered[tile] = true;
			walked[tile] = true;
		}

		boolean discovered(int tile) {
			return tile >= 0 && tile < TILES && discovered[tile];
		}

		/** @return whether the player has been in the tile, so its room may be drawn before the game's map has it */
		boolean walked(int tile) {
			return tile >= 0 && tile < TILES && walked[tile];
		}

		/** @return what the scan knows of a tile's room, only when that room counts as discovered; null otherwise, whatever the scan knows */
		RoomData room(int tile) {
			return discovered(tile) ? scanned[tile] : null;
		}

		/** @return whether every tile was told, so the totals are those of the whole dungeon */
		boolean complete() {
			for (boolean each : done) {
				if (!each) {
					return false;
				}
			}
			return true;
		}

		/**
		 * @return the secrets of all rooms of the dungeon, each room once, or -1 while not every tile was told: a total the game shows anyway
		 *         (the tab list's "Secrets Found" percentage), which is all that is used of rooms not discovered yet
		 */
		int totalSecrets() {
			if (!complete()) {
				return -1;
			}
			Set<String> counted = new HashSet<>();
			int secrets = 0;
			for (RoomData room : scanned) {
				if (room != null && counted.add(room.name())) {
					secrets += room.secrets();
				}
			}
			return secrets;
		}

		void reset() {
			java.util.Arrays.fill(scanned, null);
			java.util.Arrays.fill(done, false);
			java.util.Arrays.fill(discovered, false);
			java.util.Arrays.fill(walked, false);
		}
	}

	private static final Rooms ROOMS = new Rooms();
	private static int next;

	private DungeonScan() {
	}

	/** Every tick while clearing: a tile or two whose chunk is loaded and that was not told yet. */
	static void tick(Minecraft client) {
		if (client.level == null || client.player == null || !Dungeon.inClear()) {
			return;
		}
		int told = 0;
		for (int i = 0; i < TILES && told < PER_TICK; i++) {
			int tile = (next + i) % TILES;
			if (ROOMS.done(tile)) {
				continue;
			}
			int x = tile % CurrentRoom.GRID;
			int z = tile / CurrentRoom.GRID;
			if (CurrentRoom.chunkLoaded(client.level, x, z)) {
				ROOMS.scanned(tile, CurrentRoom.roomAt(client.level, x, z));
				told++;
				next = tile + 1;
			}
		}
	}

	/** The game's map item shows a tile's room as discovered. */
	static void mapShows(int tile) {
		ROOMS.mapShows(tile);
	}

	/** The player stands in a tile. */
	static void walkedInto(int tile) {
		if (tile >= 0 && tile < TILES) {
			ROOMS.walkedInto(tile);
		}
	}

	static boolean walked(int tile) {
		return ROOMS.walked(tile);
	}

	/** @return a discovered room as the scan knows it, or null: never anything of a room that is not discovered */
	public static RoomData room(int tile) {
		return ROOMS.room(tile);
	}

	/** @return the secrets of the whole dungeon, or -1 while not all of it was told */
	static int totalSecrets() {
		return ROOMS.totalSecrets();
	}

	static void reset() {
		ROOMS.reset();
		next = 0;
	}
}
