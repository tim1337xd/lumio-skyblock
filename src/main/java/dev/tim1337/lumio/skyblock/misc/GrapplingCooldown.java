package dev.tim1337.lumio.skyblock.misc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Grappling Hook's cooldown: how long it is by the item's lore ("2 Second Cooldown", as in the NEU repository), and when it runs. It starts only when the hook really pulls the player, seen as a sudden change of the player's speed shortly
 * after a reel-in; a use during the cooldown does not pull, and changes nothing. Plain numbers and text, so it is unit tested. The wording of the game's message and the size of a pull are from the request and from the game's own jump, not from
 * a capture on the server.
 */
final class GrapplingCooldown {
	static final String HOOK_ID = "GRAPPLING_HOOK";
	static final double DEFAULT_SECONDS = 2.0;
	private static final double MOST_SECONDS = 60.0;
	private static final Pattern LORE = Pattern.compile("(\\d{1,2}(?:\\.\\d{1,2})?) Second Cooldown");
	private static final Pattern TOO_SOON = Pattern.compile("(?i)\\bslow down\\b");
	/** How long after the reel-in the server's pull can arrive: it takes a round trip. */
	static final int PULL_WINDOW_TICKS = 10;
	/** The speed change of a pull, in blocks per tick: above what the player's own sprint jump does (about 0.47). */
	private static final double PULL_BLOCKS_PER_TICK = 0.6;
	/** What the timer shows when the game says the hook is not ready while it has run out: the time left is not known, so only a moment. */
	static final double SHORT_SECONDS = 0.5;

	private double seconds = DEFAULT_SECONDS;
	private double left;
	private int windowTicks;
	private double reeledLength = DEFAULT_SECONDS;
	private double lastX;
	private double lastY;
	private double lastZ;

	/** @return the length of the cooldown a lore says, or the usual one if it says none */
	static double fromLore(List<String> lore) {
		for (String line : lore) {
			Matcher matcher = LORE.matcher(line);
			if (matcher.find()) {
				double found = Double.parseDouble(matcher.group(1));
				return found > 0.0 && found <= MOST_SECONDS ? found : DEFAULT_SECONDS;
			}
		}
		return DEFAULT_SECONDS;
	}

	/** @return whether a message from the server says the hook was used too soon */
	static boolean tooSoon(String line) {
		return TOO_SOON.matcher(line).find();
	}

	/** The player reeled the hook in: whether that pulls shows in the player's speed during the next ticks. It changes nothing by itself. */
	void reeled(double lengthSeconds) {
		reeledLength = lengthSeconds;
		windowTicks = PULL_WINDOW_TICKS;
	}

	/**
	 * The player's speed, once per tick.
	 *
	 * @return whether this was the hook's pull, which starts the cooldown
	 */
	boolean moved(double x, double y, double z) {
		boolean pull = false;
		if (windowTicks > 0) {
			windowTicks--;
			double changeX = x - lastX;
			double changeY = y - lastY;
			double changeZ = z - lastZ;
			boolean sudden = changeX * changeX + changeY * changeY + changeZ * changeZ >= PULL_BLOCKS_PER_TICK * PULL_BLOCKS_PER_TICK;
			// landing changes the speed as suddenly: but it only takes the fall away, nothing throws the player up or forward
			pull = sudden && (y > 0.0 || Math.hypot(x, z) - Math.hypot(lastX, lastZ) >= PULL_BLOCKS_PER_TICK);
			if (pull) {
				pulled(reeledLength);
				windowTicks = 0;
			}
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		return pull;
	}

	/** The hook pulled the player. */
	void pulled(double lengthSeconds) {
		seconds = lengthSeconds;
		left = lengthSeconds;
	}

	/** The game says the hook was used too soon, so that use pulled nothing. A running timer goes on as it is; one that has run out was early, and shows a moment until the next real pull. */
	void resync() {
		windowTicks = 0;
		if (left <= 0.0) {
			left = SHORT_SECONDS;
		}
	}

	void advance(double passedSeconds) {
		left = Math.max(0.0, left - passedSeconds);
	}

	boolean running() {
		return left > 0.0;
	}

	double left() {
		return left;
	}

	/** @return how much of the bar is left, from 1 to 0 */
	float share() {
		return seconds <= 0.0 ? 0f : (float) Math.min(1.0, left / seconds);
	}
}
