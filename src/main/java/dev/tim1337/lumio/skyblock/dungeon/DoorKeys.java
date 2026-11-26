package dev.tim1337.lumio.skyblock.dungeon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The party's door keys in a run, as the game shows them: the scoreboard's key line ("Keys: ■ ✓ ■ 1x": the blood key, then how many wither keys)
 * and Hypixel's chat lines when a key is picked up or a door opened. Keys belong to the whole party; each line changes the state at once.
 */
final class DoorKeys {
	/** The scoreboard's line, without colors: the blood key's mark, then the wither keys' count. */
	private static final Pattern SCOREBOARD = Pattern.compile("Keys:\\s*■\\s*([✗✓])\\s*■\\s*(\\d{1,2})x");
	private static final String HAVE = "✓";

	private int witherKeys;
	private boolean bloodKey;

	/** A key was picked up ("… has obtained Wither Key!", "A Blood Key was picked up!"). */
	void picked(boolean wither) {
		if (wither) {
			witherKeys++;
		} else {
			bloodKey = true;
		}
	}

	/** A door was opened with its key ("… opened a WITHER door!", "The BLOOD DOOR has been opened!"). */
	void opened(boolean wither) {
		if (wither) {
			witherKeys = Math.max(0, witherKeys - 1);
		} else {
			bloodKey = false;
		}
	}

	/** @return whether a scoreboard line was the key line; it then sets both keys as the game counts them */
	boolean scoreboard(String line) {
		Matcher matcher = SCOREBOARD.matcher(line);
		if (!matcher.find()) {
			return false;
		}
		bloodKey = HAVE.equals(matcher.group(1));
		witherKeys = Integer.parseInt(matcher.group(2));
		return true;
	}

	boolean wither() {
		return witherKeys > 0;
	}

	boolean blood() {
		return bloodKey;
	}

	void reset() {
		witherKeys = 0;
		bloodKey = false;
	}
}
