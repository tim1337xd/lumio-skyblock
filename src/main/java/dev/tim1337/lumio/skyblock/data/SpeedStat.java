package dev.tim1337.lumio.skyblock.data;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SkyBlock's Speed stat from the player's movement speed attribute: 0.1 is 100. Plain numbers and text, so it is unit tested. */
public final class SpeedStat {
	/** The usual cap of Speed, and the one when the server has raised it. */
	public static final int CAP = 400;
	public static final int RAISED_CAP = 500;
	private static final double PER_ATTRIBUTE = 1000.0;
	/** What the game's sprinting multiplies the attribute by: Speed itself is without it. */
	private static final double SPRINT_FACTOR = 1.3;
	/** No Speed is anywhere near this: a value from the server stays bounded. */
	private static final double MOST_ATTRIBUTE = 100.0;
	private static final Pattern TAB_LINE = Pattern.compile("\\bSpeed: ?\\u2726 ?(\\d{1,4})\\b");

	private SpeedStat() {
	}

	/**
	 * @param attribute the attribute's value
	 * @param sprinting whether the game's sprint bonus is in it
	 * @return the Speed stat
	 */
	public static int fromAttribute(double attribute, boolean sprinting) {
		if (!(attribute > 0.0) || attribute > MOST_ATTRIBUTE) {
			return 0;
		}
		return (int) Math.round((sprinting ? attribute / SPRINT_FACTOR : attribute) * PER_ATTRIBUTE);
	}

	/** @return what the bar is full at: above the usual cap the server has raised it */
	public static int cap(int speed) {
		return speed > CAP ? RAISED_CAP : CAP;
	}

	/** @return the Speed the stats widget of the player list shows ("Speed: ✦400"), or -1 when it is not there */
	public static int fromTab(List<String> lines) {
		for (String line : lines) {
			Matcher matcher = TAB_LINE.matcher(line);
			if (matcher.find()) {
				return Integer.parseInt(matcher.group(1));
			}
		}
		return -1;
	}
}
