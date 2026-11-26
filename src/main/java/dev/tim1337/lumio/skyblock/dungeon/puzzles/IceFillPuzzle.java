package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Ice Fill: the way over each of the three floors that covers every ice block once, drawn as a line on the floor. Which pattern a floor has is
 * told by two blocks of it, one air and one not, as Odin reads it (Odin's floors and ways, BSD-3-Clause).
 */
final class IceFillPuzzle implements RoomPuzzle {
	private static final int FLOORS = 3;
	private static final int LINE_RGB = 0x5AC8FA;
	private static final double LIFT = 0.1;

	/** By floor: each pattern's two telling blocks, and each pattern's way, as room places. */
	private static List<List<int[][]>> identifiers;
	private static List<List<List<int[]>>> ways;
	/** The way found, in the world, all floors one after another; null until the room was read. */
	private List<int[]> path;
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	@Override
	public String room() {
		return "Ice Fill";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		if (path == null) {
			load();
			path = new ArrayList<>();
			for (int floor = 0; floor < FLOORS && floor < identifiers.size(); floor++) {
				List<int[][]> options = identifiers.get(floor);
				for (int pattern = 0; pattern < options.size(); pattern++) {
					int[][] telling = options.get(pattern);
					if (air(client, telling[0]) && !air(client, telling[1])) {
						for (int[] step : ways.get(floor).get(pattern)) {
							path.add(CurrentRoom.toWorld(step[0], step[1], step[2]));
						}
						break;
					}
				}
			}
		}
		for (int i = 0; i + 1 < path.size(); i++) {
			marks.line(path.get(i), path.get(i + 1), LIFT, LINE_RGB);
		}
	}

	private boolean air(Minecraft client, int[] roomPlace) {
		int[] world = CurrentRoom.toWorld(roomPlace[0], roomPlace[1], roomPlace[2]);
		at.set(world[0], world[1], world[2]);
		return client.level.getBlockState(at).isAir();
	}

	@Override
	public void reset() {
		path = null;
	}

	private static void load() {
		if (identifiers != null) {
			return;
		}
		identifiers = new ArrayList<>();
		ways = new ArrayList<>();
		if (!(PuzzleData.read("ice-fill-floors.json") instanceof JsonObject json)) {
			return;
		}
		for (JsonElement floor : json.getAsJsonArray("identifier")) {
			List<int[][]> options = new ArrayList<>();
			for (JsonElement pair : floor.getAsJsonArray()) {
				JsonArray two = pair.getAsJsonArray();
				options.add(new int[][] {place(two.get(0)), place(two.get(1))});
			}
			identifiers.add(options);
		}
		for (JsonElement floor : json.getAsJsonArray("easy")) {
			List<List<int[]>> patterns = new ArrayList<>();
			for (JsonElement pattern : floor.getAsJsonArray()) {
				List<int[]> steps = new ArrayList<>();
				for (JsonElement step : pattern.getAsJsonArray()) {
					steps.add(place(step));
				}
				patterns.add(steps);
			}
			ways.add(patterns);
		}
	}

	private static int[] place(JsonElement element) {
		JsonObject json = element.getAsJsonObject();
		return new int[] {json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()};
	}
}
