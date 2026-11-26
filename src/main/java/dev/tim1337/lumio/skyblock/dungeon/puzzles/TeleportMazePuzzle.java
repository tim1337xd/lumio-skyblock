package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Teleport Maze: after each teleport the player faces the right pad of the next cell, so the pads the view points at (across the floor) narrow
 * down the right one; pads already used are marked as such. One pad left is green, several yellow, used ones red. Only the player's own
 * teleports are watched, from how their place jumps between two ticks.
 */
final class TeleportMazePuzzle implements RoomPuzzle {
	/** The pads of the maze, across and down in the room; the floor they stand on is the same for all. */
	private static final int[][] PADS = {{4, 12}, {4, 6}, {10, 12}, {10, 6}, {4, 20}, {4, 14}, {10, 20}, {10, 14}, {4, 28}, {4, 22}, {10, 28}, {10, 22},
		{12, 28}, {12, 22}, {18, 28}, {18, 22}, {20, 28}, {20, 22}, {26, 28}, {26, 22}, {26, 20}, {26, 14}, {20, 20}, {20, 14}, {26, 12}, {26, 6},
		{20, 12}, {20, 6}, {15, 14}, {15, 12}};
	private static final int PAD_Y = 69;
	/** A teleport moves the player at least this far between two ticks, in blocks. */
	private static final double JUMP_BLOCKS = 2.5;
	/** How far the view is followed across the floor, in blocks, and how much wider than a pad it may pass. */
	private static final double VIEW_BLOCKS = 32;
	private static final double PAD_MARGIN = 0.75;
	private static final double LANDED_BLOCKS = 1.6;
	private static final int ONE_RGB = 0x34C759;
	private static final int SOME_RGB = 0xFFD60A;
	private static final int USED_RGB = 0xFF453A;

	private final Set<Integer> used = new HashSet<>();
	/** The pads still possible as the right one; null before the first teleport. */
	private Set<Integer> possible;
	private double lastX = Double.NaN;
	private double lastZ = Double.NaN;

	@Override
	public String room() {
		return "Teleport Maze";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		LocalPlayer player = client.player;
		if (!Double.isNaN(lastX)) {
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			if (dx * dx + dz * dz > JUMP_BLOCKS * JUMP_BLOCKS) {
				teleported(lastX, lastZ, player.getX(), player.getZ(), player.getYRot());
			}
		}
		lastX = player.getX();
		lastZ = player.getZ();
		for (int i = 0; i < PADS.length; i++) {
			int[] world = CurrentRoom.toWorld(PADS[i][0], PAD_Y, PADS[i][1]);
			if (used.contains(i)) {
				marks.block(world, USED_RGB, false);
			} else if (possible != null && possible.contains(i)) {
				marks.block(world, possible.size() == 1 ? ONE_RGB : SOME_RGB, true);
			}
		}
	}

	/** A teleport: the pads left and landed on are used, and the pads the view points at stay possible. */
	private void teleported(double fromX, double fromZ, double toX, double toZ, float yaw) {
		List<double[]> centers = new ArrayList<>(PADS.length);
		for (int[] pad : PADS) {
			int[] world = CurrentRoom.toWorld(pad[0], PAD_Y, pad[1]);
			centers.add(new double[] {world[0] + 0.5, world[2] + 0.5});
		}
		Set<Integer> ahead = new HashSet<>();
		for (int i = 0; i < centers.size(); i++) {
			double[] center = centers.get(i);
			if (near(center, fromX, fromZ) || near(center, toX, toZ)) {
				used.add(i);
			} else if (inView(center[0], center[1], toX, toZ, yaw)) {
				ahead.add(i);
			}
		}
		ahead.removeAll(used);
		if (possible == null) {
			possible = ahead;
		} else {
			possible.retainAll(ahead);
			if (possible.isEmpty()) {
				possible = ahead;
			}
		}
		possible.removeAll(used);
	}

	private static boolean near(double[] center, double x, double z) {
		double dx = center[0] - x;
		double dz = center[1] - z;
		return dx * dx + dz * dz < LANDED_BLOCKS * LANDED_BLOCKS;
	}

	/** @return whether a pad lies in the way the player faces, across the floor and close enough: the line of view passes over it */
	static boolean inView(double padX, double padZ, double x, double z, float yaw) {
		double radians = Math.toRadians(yaw);
		double lookX = -Math.sin(radians);
		double lookZ = Math.cos(radians);
		double toX = padX - x;
		double toZ = padZ - z;
		double along = toX * lookX + toZ * lookZ;
		if (along <= 0 || along > VIEW_BLOCKS) {
			return false;
		}
		double aside = Math.abs(toX * lookZ - toZ * lookX);
		return aside <= 0.5 + PAD_MARGIN;
	}

	@Override
	public void reset() {
		used.clear();
		possible = null;
		lastX = Double.NaN;
		lastZ = Double.NaN;
	}
}
