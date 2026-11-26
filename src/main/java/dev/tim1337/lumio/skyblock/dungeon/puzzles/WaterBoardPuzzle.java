package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Water Board: which lever to flick when, from the board's layout when the player comes in (the block over the gate and which wool walls are
 * out), using the solutions bundled from Odin (BSD-3-Clause). Each lever shows what is left for it: "Click" or the seconds until its next flick,
 * counted in server ticks from the moment the water was let out; the next lever to flick is boxed. Flicks are counted from the player's own use.
 */
final class WaterBoardPuzzle implements RoomPuzzle {
	/** The levers, where they are in the room, and their names in the solutions. */
	enum Lever {
		COAL(20, 61, 10, "coal_block"), GOLD(20, 61, 15, "gold_block"), QUARTZ(20, 61, 20, "quartz_block"), DIAMOND(10, 61, 20, "diamond_block"),
		EMERALD(10, 61, 15, "emerald_block"), CLAY(10, 61, 10, "hardened_clay"), WATER(15, 60, 5, "water");

		final int x;
		final int y;
		final int z;
		final String key;

		Lever(int x, int y, int z, String key) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.key = key;
		}
	}

	/** The wool walls (purple, orange, blue, green, red: the digits of a layout), in a row in the room, each a block further. */
	private static final int WALLS = 5;
	private static final int WALL_X = 15;
	private static final int WALL_Y = 56;
	private static final int FIRST_WALL_Z = 19;
	private static final int WALLS_OUT = 3;
	private static final int TICKS_PER_SECOND = 20;
	private static final int NEXT_RGB = 0x34C759;
	private static final int TIMER_RGB = 0xFFD60A;
	private static final double LABEL_LIFT = 1.5;
	private static final double LABEL_STEP = 0.5;
	private static JsonObject solutions;

	/** The flick times left for each lever, in seconds after the water is let out; null until the board was read. */
	private Map<Lever, List<Double>> left;
	private long now;
	private long waterAt = -1;
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	@Override
	public String room() {
		return "Water Board";
	}

	@Override
	public void serverTick() {
		now++;
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		if (left == null) {
			left = read(client);
			if (left == null) {
				return;
			}
		}
		Lever next = nextLever(left, waterAt >= 0 ? (now - waterAt) / (double) TICKS_PER_SECOND : -1);
		for (Map.Entry<Lever, List<Double>> entry : left.entrySet()) {
			Lever lever = entry.getKey();
			int[] world = CurrentRoom.toWorld(lever.x, lever.y, lever.z);
			if (lever == next) {
				marks.block(world, NEXT_RGB, true);
			}
			List<Double> times = entry.getValue();
			for (int i = 0; i < times.size(); i++) {
				marks.label(world[0] + 0.5, world[1] + LABEL_LIFT + i * LABEL_STEP, world[2] + 0.5, labelOf(times.get(i)), i == 0 && lever == next ? NEXT_RGB
					: TIMER_RGB);
			}
		}
	}

	/** @return "Click" when the time has come, else the seconds still to wait */
	private String labelOf(double seconds) {
		if (waterAt < 0) {
			return seconds <= 0 ? "Click" : seconds + " s";
		}
		long ticksLeft = waterAt + Math.round(seconds * TICKS_PER_SECOND) - now;
		if (ticksLeft <= 0) {
			return "Click";
		}
		long tenths = Math.round(ticksLeft / 2.0);
		return tenths / 10 + "." + tenths % 10 + " s";
	}

	/**
	 * @param elapsed seconds since the water was let out, or -1 before
	 * @return the lever to flick next: one due now first (in the levers' order), else the soonest; null when none is left
	 */
	static Lever nextLever(Map<Lever, List<Double>> left, double elapsed) {
		return left.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
			.min(Comparator.comparingDouble((Map.Entry<Lever, List<Double>> entry) -> {
				double first = entry.getValue().getFirst();
				return first <= Math.max(0, elapsed) ? -1 : first;
			}).thenComparing(entry -> entry.getKey().ordinal())).map(Map.Entry::getKey).orElse(null);
	}

	@Override
	public void used(BlockPos pos) {
		if (left == null) {
			return;
		}
		for (Lever lever : Lever.values()) {
			int[] world = CurrentRoom.toWorld(lever.x, lever.y, lever.z);
			if (world[0] == pos.getX() && world[1] == pos.getY() && world[2] == pos.getZ()) {
				if (lever == Lever.WATER && waterAt < 0) {
					waterAt = now;
				}
				List<Double> times = left.get(lever);
				if (times != null && !times.isEmpty()) {
					times.removeFirst();
				}
				return;
			}
		}
	}

	/** Reads the board: the block over the gate tells the pattern, the wool walls that are out its variant. */
	private Map<Lever, List<Double>> read(Minecraft client) {
		int pattern = patternOf(client);
		StringBuilder walls = new StringBuilder();
		for (int i = 0; i < WALLS; i++) {
			if (!blockAt(client, WALL_X, WALL_Y, FIRST_WALL_Z - i).defaultBlockState().isAir()) {
				walls.append(i);
			}
		}
		if (pattern < 0 || walls.length() != WALLS_OUT) {
			return null;
		}
		return solution(String.valueOf(pattern), walls.toString());
	}

	/** @return the board's pattern, from the blocks over its gate, or -1 while it cannot be told (the puzzle already started) */
	private int patternOf(Minecraft client) {
		if (blockAt(client, 14, 77, 27) == Blocks.TERRACOTTA) {
			return 0;
		}
		if (blockAt(client, 16, 78, 27) == Blocks.EMERALD_BLOCK) {
			return 1;
		}
		Block right = blockAt(client, 14, 78, 27);
		return right == Blocks.DIAMOND_BLOCK ? 2 : right == Blocks.QUARTZ_BLOCK ? 3 : -1;
	}

	private Block blockAt(Minecraft client, int x, int y, int z) {
		int[] world = CurrentRoom.toWorld(x, y, z);
		at.set(world[0], world[1], world[2]);
		return client.level.getBlockState(at).getBlock();
	}

	/** @return the flick times of every lever for a pattern and its walls, or null where the solutions have none */
	static Map<Lever, List<Double>> solution(String pattern, String walls) {
		if (solutions == null) {
			solutions = PuzzleData.read("water-solutions.json") instanceof JsonObject json ? json : new JsonObject();
		}
		if (!(solutions.get("false") instanceof JsonObject standard) || !(standard.get(pattern) instanceof JsonObject byWalls)
			|| !(byWalls.get(walls) instanceof JsonObject levers)) {
			return null;
		}
		Map<Lever, List<Double>> times = new EnumMap<>(Lever.class);
		for (Lever lever : Lever.values()) {
			if (levers.get(lever.key) instanceof JsonArray array) {
				List<Double> list = new ArrayList<>();
				for (JsonElement time : array) {
					list.add(time.getAsDouble());
				}
				times.put(lever, list);
			}
		}
		return times;
	}

	@Override
	public void reset() {
		left = null;
		waterAt = -1;
	}
}
