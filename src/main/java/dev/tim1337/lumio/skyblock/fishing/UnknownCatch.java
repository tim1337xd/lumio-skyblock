package dev.tim1337.lumio.skyblock.fishing;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A sea creature the bundled list does not know yet (a new island's, a new event's): told from the catch itself rather than dropped. The server's
 * line for a creature that surfaces has no fixed form, so it is recognized by how it arrives: right after the player's own bobber was out, all in
 * the green the server writes these lines in, without numbers, and none of the lines that also come while fishing. The creature's name is read
 * from the line where it has one. Plain logic, tested without the game.
 */
public final class UnknownCatch {
	/** What a creature is counted as when its line names none. */
	public static final String NAMELESS = "Unknown creature";
	/** A catch line comes at most this long after the player's bobber was last out, in milliseconds. */
	static final long AFTER_HOOK_MILLIS = 1500L;
	static final int MOST_LETTERS = 120;
	/** Words that start a sentence of a catch line or stand in it with a capital, and are no part of a name. */
	private static final Set<String> NO_NAME = Set.of("A", "An", "The", "You", "Your", "You've", "It", "It's", "Its", "What", "Phew", "Is", "This", "That",
		"Looks", "Something", "From", "Wait", "Was", "Oh", "Uh", "Hmm", "I");
	/** Lines that come while fishing and are no creature. */
	private static final String[] NO_CATCH = {"Double Hook", "CATCH!", "TROPHY FISH", "You caught", "You received", "You found", "Sacks", "Profile", "Welcome", "Warping", "Sending"};

	/** What stands around a word and is no letter of it. */
	private static final Pattern EDGES = Pattern.compile("^[^\\p{L}]+|[^\\p{L}']+$");

	private UnknownCatch() {
	}

	/**
	 * @param line            a line of the server, without colors
	 * @param green           whether all of it was written in the green of catch lines
	 * @param sinceHookMillis how long ago the player's own bobber was last out; negative when it never was
	 * @return the name the creature is counted under ({@link #NAMELESS} where the line names none), or null when the line is no catch
	 */
	public static String of(String line, boolean green, long sinceHookMillis) {
		if (!green || sinceHookMillis < 0 || sinceHookMillis > AFTER_HOOK_MILLIS || line.isBlank() || line.length() > MOST_LETTERS) {
			return null;
		}
		for (int i = 0; i < line.length(); i++) {
			if (Character.isDigit(line.charAt(i)) || line.charAt(i) == ':') {
				return null;
			}
		}
		for (String other : NO_CATCH) {
			if (line.contains(other)) {
				return null;
			}
		}
		String name = nameIn(line);
		return name.isEmpty() ? NAMELESS : name;
	}

	/** @return the creature's name in a catch line: its longest run of words with a capital that are no sentence words ("An inquisitive Atoll Croaker takes the bait!" names the Atoll Croaker), or nothing */
	static String nameIn(String line) {
		String best = "";
		StringBuilder run = new StringBuilder();
		for (String raw : line.split("\\s+")) {
			String word = EDGES.matcher(raw).replaceAll("");
			boolean ends = !raw.isEmpty() && !Character.isLetter(raw.charAt(raw.length() - 1)) && raw.charAt(raw.length() - 1) != '\'';
			boolean named = !word.isEmpty() && Character.isUpperCase(word.charAt(0)) && !(run.isEmpty() && NO_NAME.contains(word));
			if (named) {
				run.append(run.isEmpty() ? "" : " ").append(word);
			}
			if (!named || ends) {
				if (run.length() > best.length()) {
					best = run.toString();
				}
				run.setLength(0);
			}
		}
		return run.length() > best.length() ? run.toString() : best;
	}
}
