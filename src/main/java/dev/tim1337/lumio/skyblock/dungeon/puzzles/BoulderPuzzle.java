package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boulder: the boxes to push, in order, from the layout of the boulders when the player comes in (Odin's solutions, BSD-3-Clause). The next box is
 * marked strongest; a box is done once the player used the block it is pushed from.
 */
final class BoulderPuzzle implements RoomPuzzle {
	private static final int BOX_Y = 65;
	private static final int LAYOUT_Y = 66;
	private static final int NEXT_RGB = 0x34C759;
	private static final int LATER_RGB = 0x9BE15D;

	/** The solutions by layout: each step is where the box is (x, z) and where it is pushed from (x, z), in the room. */
	private static Map<String, List<int[]>> solutions;
	private final List<int[][]> steps = new ArrayList<>();
	private boolean scanned;

	@Override
	public String room() {
		return "Boulder";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		if (!scanned) {
			scanned = true;
			List<int[]> solution = solutions().get(layout(client));
			if (solution != null) {
				for (int[] step : solution) {
					steps.add(new int[][] {CurrentRoom.toWorld(step[0], BOX_Y, step[1]), CurrentRoom.toWorld(step[2], BOX_Y, step[3])});
				}
			}
		}
		for (int i = 0; i < steps.size(); i++) {
			marks.block(steps.get(i)[0], i == 0 ? NEXT_RGB : LATER_RGB, i == 0);
		}
	}

	/** @return the layout of the boulders as Odin keys it: one digit a spot, 1 where a boulder is */
	private static String layout(Minecraft client) {
		StringBuilder key = new StringBuilder();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) {
				int[] world = CurrentRoom.toWorld(x, LAYOUT_Y, z);
				at.set(world[0], world[1], world[2]);
				key.append(client.level.getBlockState(at).isAir() ? '0' : '1');
			}
		}
		return key.toString();
	}

	@Override
	public void used(BlockPos pos) {
		for (int i = 0; i < steps.size(); i++) {
			int[] from = steps.get(i)[1];
			if (from[0] == pos.getX() && from[1] == pos.getY() && from[2] == pos.getZ()) {
				steps.remove(i);
				return;
			}
		}
	}

	@Override
	public void reset() {
		steps.clear();
		scanned = false;
	}

	static Map<String, List<int[]>> solutions() {
		if (solutions == null) {
			solutions = new HashMap<>();
			if (PuzzleData.read("boulder-solutions.json") instanceof JsonObject json) {
				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					List<int[]> list = new ArrayList<>();
					for (JsonElement step : entry.getValue().getAsJsonArray()) {
						var array = step.getAsJsonArray();
						list.add(new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(), array.get(3).getAsInt()});
					}
					solutions.put(entry.getKey(), list);
				}
			}
		}
		return solutions;
	}
}
