package dev.tim1337.lumio.skyblock.slayer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who a slayer boss or miniboss belongs to, read from the name tag the server puts under it for everyone to see: "Spawned by: Steve123", or the
 * newer "Steve123's SLAYER BOSS". Names match as whole Minecraft names, ignoring case, so "Steve" is not "Steve123". Plain strings, so it is unit
 * tested with recorded name tags.
 */
public final class SlayerOwners {
	/** The longest Minecraft name. */
	public static final int MOST_NAME_LENGTH = 16;
	private static final String SPAWNED_BY = "Spawned by";
	private static final String SLAYER_BOSS = "SLAYER BOSS";
	/** The owner's name in either kind of tag: "Spawned by: Steve123", "Steve123's SLAYER BOSS". */
	private static final Pattern SPAWNED_BY_NAME = Pattern.compile("Spawned by:?\\s*([A-Za-z0-9_]{1,16})");
	private static final Pattern BOSS_OF_NAME = Pattern.compile("([A-Za-z0-9_]{1,16})'s SLAYER BOSS");

	private SlayerOwners() {
	}

	/** @return whether a name tag line says who spawned the mob above it */
	public static boolean isOwnerLine(String line) {
		return line.contains(SPAWNED_BY) || line.contains(SLAYER_BOSS);
	}

	/**
	 * @param line a name tag line without color codes
	 * @param name a player's name; empty never matches
	 * @return whether the line says the mob above it was spawned by that player
	 */
	public static boolean ownedBy(String line, String name) {
		if (name == null || name.isEmpty() || !isOwnerLine(line)) {
			return false;
		}
		String lowerLine = line.toLowerCase(Locale.ROOT);
		String lowerName = name.toLowerCase(Locale.ROOT);
		int from = 0;
		while (true) {
			int at = lowerLine.indexOf(lowerName, from);
			if (at < 0) {
				return false;
			}
			int end = at + lowerName.length();
			// a whole name: no name character right before or after it
			if ((at == 0 || !nameChar(lowerLine.charAt(at - 1))) && (end >= lowerLine.length() || !nameChar(lowerLine.charAt(end)))) {
				return true;
			}
			from = at + 1;
		}
	}

	/** @return the name of the player a name tag line says spawned the mob above it, or null if the line says none */
	public static String ownerOf(String line) {
		if (!isOwnerLine(line)) {
			return null;
		}
		Matcher spawned = SPAWNED_BY_NAME.matcher(line);
		if (spawned.find()) {
			return spawned.group(1);
		}
		Matcher boss = BOSS_OF_NAME.matcher(line);
		return boss.find() ? boss.group(1) : null;
	}

	/** @return a typed name with only the characters a Minecraft name can have, at most 16 of them */
	public static String clean(String typed) {
		if (typed == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(Math.min(typed.length(), MOST_NAME_LENGTH));
		for (int i = 0; i < typed.length() && out.length() < MOST_NAME_LENGTH; i++) {
			char c = typed.charAt(i);
			if (nameChar(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** @return whether a character can be part of a Minecraft name: a letter or digit (ASCII) or the underscore */
	static boolean nameChar(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
	}
}
