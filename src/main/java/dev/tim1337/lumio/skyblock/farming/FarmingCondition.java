package dev.tim1337.lumio.skyblock.farming;

import java.util.Locale;

/**
 * When the player wants to farm, for the Mouse lock and the Farming Keys alike, the one automatic mode of both: in the Garden, a farming tool
 * held, not flying (on the ground; a short time in the air, a step down or a jump, still counts, so nothing flickers), not on the Barn, and no
 * screen open. Plain logic without the game, so it is unit tested.
 * <p>
 * Where the player is, is read so that nothing the Garden shows while farming can end it: "in the Garden" comes from the island, and from the
 * scoreboard's place only while the island is not known; the Barn is told only from where the player stands (the plot in the Garden's middle),
 * never from a line of text. With pests alive the scoreboard writes "The Garden ൠ x3" on every plot that has none itself, which once read as
 * the Barn and switched both features off. A place that cannot be read for a moment keeps what was known.
 */
final class FarmingCondition {
	/** This many ticks off the ground still count as standing on it: a jump on the spot takes about as long. */
	static final int AIR_GRACE_TICKS = 12;
	/** The Garden's plots are squares of this many blocks; the Barn is the one around the Garden's middle (0, 0). */
	static final int PLOT_BLOCKS = 96;

	/**
	 * What is looked at in a tick.
	 *
	 * @param inGarden   the player is in the Garden
	 * @param tool       a farming tool is held
	 * @param onGround   the player stands on the ground
	 * @param flying     the player flies (the Garden lets everyone)
	 * @param barn       the player is on the Barn plot
	 * @param screenOpen a screen is open
	 */
	record Input(boolean inGarden, boolean tool, boolean onGround, boolean flying, boolean barn, boolean screenOpen) {
	}

	private int airTicks;
	private boolean farming;

	/** One tick. @return whether the player is farming now */
	boolean tick(Input input) {
		airTicks = input.onGround() ? 0 : Math.min(airTicks + 1, AIR_GRACE_TICKS + 1);
		boolean grounded = !input.flying() && airTicks <= AIR_GRACE_TICKS;
		farming = input.inGarden() && input.tool() && grounded && !input.barn() && !input.screenOpen();
		return farming;
	}

	/** @return whether the player is farming, as of the last tick */
	boolean farming() {
		return farming;
	}

	/** @return whether a place in the Garden is on the Barn plot: the plot around the Garden's middle. A positive match only. */
	static boolean barn(double x, double z) {
		return Math.abs(x) < PLOT_BLOCKS / 2.0 && Math.abs(z) < PLOT_BLOCKS / 2.0;
	}

	/**
	 * @param island the island as the player list names it ("Garden"), or "" while it is not known
	 * @param place  the scoreboard's place line, with whatever the game adds to it ("⏣ Plot - 7 ൠ x2", "⏣ The Garden ൠ x3")
	 * @param before what was known before
	 * @return whether the player is in the Garden: by the island where it is known, else by the place line, else as before
	 */
	static boolean inGarden(String island, String place, boolean before) {
		String area = words(island);
		if (!area.isEmpty()) {
			return area.contains("garden");
		}
		String line = words(place);
		if (line.isEmpty()) {
			return before;
		}
		return line.contains("garden") || line.contains("plot");
	}

	/** @return a line's words alone, in lower case: without color codes, symbols and counts */
	static String words(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§') {
				i++;
			} else if (c < 128 && (Character.isLetter(c) || c == ' ' || c == '-')) {
				out.append(c);
			}
		}
		return out.toString().toLowerCase(Locale.ROOT).strip();
	}
}
