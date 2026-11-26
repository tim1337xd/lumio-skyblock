package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Creeper Beams: the sea lanterns to connect, each pair boxed in a color of its own with a line between them, as long as both are still lit
 * (Odin's pairs, BSD-3-Clause). A pair the player already joined is gone, as its lanterns are prismarine then.
 */
final class CreeperBeamsPuzzle implements RoomPuzzle {
	private static final int[] COLORS = {0xFFAA00, 0x55FF55, 0xFF55FF, 0x00AAAA, 0xFFFF55, 0xAA0000, 0xFFFFFF, 0xAA00AA};
	private static List<int[]> pairs;
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	@Override
	public String room() {
		return "Creeper Beams";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		List<int[]> all = pairs();
		for (int i = 0; i < all.size(); i++) {
			int[] pair = all.get(i);
			int[] from = CurrentRoom.toWorld(pair[0], pair[1], pair[2]);
			int[] to = CurrentRoom.toWorld(pair[3], pair[4], pair[5]);
			if (lit(client, from) && lit(client, to)) {
				int rgb = COLORS[i % COLORS.length];
				marks.block(from, rgb, true);
				marks.block(to, rgb, true);
				marks.line(from, to, 0.5, rgb);
			}
		}
	}

	private boolean lit(Minecraft client, int[] world) {
		at.set(world[0], world[1], world[2]);
		return client.level.getBlockState(at).is(Blocks.SEA_LANTERN);
	}

	@Override
	public void reset() {
	}

	static List<int[]> pairs() {
		if (pairs == null) {
			pairs = new ArrayList<>();
			if (PuzzleData.read("creeper-beams-solutions.json") instanceof JsonArray array) {
				for (JsonElement element : array) {
					JsonArray pair = element.getAsJsonArray();
					int[] values = new int[6];
					for (int i = 0; i < 6; i++) {
						values[i] = pair.get(i).getAsInt();
					}
					pairs.add(values);
				}
			}
		}
		return pairs;
	}
}
