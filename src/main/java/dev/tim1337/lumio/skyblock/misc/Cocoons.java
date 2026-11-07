package dev.tim1337.lumio.skyblock.misc;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules of the cocoon display: the chat line that names the mob, how long a cocoon takes to hatch, and which belts can cocoon. Plain text and numbers, so it is
 * unit tested.
 */
final class Cocoons {
	/** About how long a cocoon takes to hatch, until enough have been measured; it can be off, so what is measured takes over. */
	static final double FIRST_HATCH_SECONDS = 6.4;
	/** A measured time outside this range was not a hatch: the cocoon left the view, or the player left it. */
	static final double LEAST_HATCH_SECONDS = 3.0;
	static final double MOST_HATCH_SECONDS = 12.0;
	/** How much a new measurement counts: the estimate follows slowly, as one can be off by a server lag. */
	private static final double LEARN_SHARE = 0.25;

	private static final Pattern CAUGHT = Pattern.compile("CAUGHT! You cocooned an? ([A-Za-z0-9'\\- ]{1,40})!");

	private double hatchSeconds = FIRST_HATCH_SECONDS;

	/** @return the mob named by a chat line without colors, or null when the line is not about a cocoon */
	static String mobOf(String line) {
		Matcher matcher = CAUGHT.matcher(line);
		return matcher.find() ? matcher.group(1).strip() : null;
	}

	/** @return whether a belt with this name can cocoon: The Primordial, or a belt with the Blood Shot reforge */
	static boolean beltCanCocoon(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.contains("the primordial") || lower.contains("blood shot");
	}

	/** @return whether a place is the Rift, where larvae look the same and are no cocoons */
	static boolean inRift(String island) {
		return island != null && island.toLowerCase(Locale.ROOT).contains("rift");
	}

	/** A cocoon hatched after this long; a time that cannot be a hatch is ignored. */
	void measured(double seconds) {
		if (seconds >= LEAST_HATCH_SECONDS && seconds <= MOST_HATCH_SECONDS) {
			hatchSeconds += (seconds - hatchSeconds) * LEARN_SHARE;
		}
	}

	double hatchSeconds() {
		return hatchSeconds;
	}

	/** @return the seconds left of a cocoon that has been up this long; never below zero */
	double secondsLeft(double age) {
		return Math.max(0.0, hatchSeconds - age);
	}

	/** @return how much of its time is left, from 1 to 0, for its bar */
	float left(double age) {
		return (float) Math.max(0.0, Math.min(1.0, secondsLeft(age) / hatchSeconds));
	}
}
