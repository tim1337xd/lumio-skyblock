package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import java.util.ArrayList;
import java.util.List;

/**
 * What the puzzle solvers mark in the world this moment: boxes around blocks, lines between places and short labels, each in a color. Filled
 * a few times a second by the solvers of the room the player is in, and drawn every frame from what is here.
 */
public final class PuzzleMarks {
	/**
	 * A box from one corner to the other, in world coordinates, as 0xRRGGBB.
	 *
	 * @param weight how strongly it is drawn, 1 for the full mark; less for a weaker one (a thinner outline, a lighter fill), such as a move to come
	 */
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, boolean filled, float weight) {
		public Box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, boolean filled) {
			this(x0, y0, z0, x1, y1, z1, rgb, filled, 1f);
		}
	}

	/** A line between two places in the world. */
	public record Line(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
	}

	/** A label over a place in the world. */
	public record Label(double x, double y, double z, String text, int rgb) {
	}

	/** A beam standing on a block, seen across the room: the block's lowest corner and the color. */
	public record Beam(int x, int y, int z, int rgb) {
	}

	private final List<Box> boxes = new ArrayList<>();
	private final List<Line> lines = new ArrayList<>();
	private final List<Label> labels = new ArrayList<>();
	private final List<Beam> beams = new ArrayList<>();

	public void clear() {
		boxes.clear();
		lines.clear();
		labels.clear();
		beams.clear();
	}

	/** Marks a whole block at a place of the world. */
	public void block(int[] at, int rgb, boolean filled) {
		boxes.add(new Box(at[0], at[1], at[2], at[0] + 1, at[1] + 1, at[2] + 1, rgb, filled));
	}

	public void box(Box box) {
		boxes.add(box);
	}

	/** A line between the middles of two blocks, lifted by the given height. */
	public void line(int[] from, int[] to, double lift, int rgb) {
		lines.add(new Line(from[0] + 0.5, from[1] + lift, from[2] + 0.5, to[0] + 0.5, to[1] + lift, to[2] + 0.5, rgb));
	}

	public void label(double x, double y, double z, String text, int rgb) {
		labels.add(new Label(x, y, z, text, rgb));
	}

	public List<Box> boxes() {
		return boxes;
	}

	public List<Line> lines() {
		return lines;
	}

	public List<Label> labels() {
		return labels;
	}

	/** A beam standing on a block at a place of the world. */
	public void beam(int[] at, int rgb) {
		beams.add(new Beam(at[0], at[1], at[2], rgb));
	}

	public List<Beam> beams() {
		return beams;
	}
}
