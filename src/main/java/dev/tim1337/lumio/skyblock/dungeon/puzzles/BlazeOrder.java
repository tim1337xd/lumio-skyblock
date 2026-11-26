package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Higher or Lower: the order to shoot the blazes in. The blazes are found by their name tags ("[Lv15] Blaze 1,234/1,500❤", with or without the
 * symbol Hypixel puts before a mob's name), told apart by their full health, and ordered by the room: in "Lower Blaze" from the highest health
 * down, in "Higher Blaze" from the lowest up (as Odin orders them). While the room's name is not known the room is told by where the blazes
 * stand against the player's floor; where that is not clear either, both orders' first blaze are named. Plain logic, tested without the game.
 */
public final class BlazeOrder {
	/** The two rooms of the puzzle, by their names in the room data. */
	public static final String LOWER_ROOM = "Lower Blaze";
	public static final String HIGHER_ROOM = "Higher Blaze";
	/**
	 * A blaze's name tag: its level, then anything short before the name (a symbol of its kind, such as ♨), "Blaze", its health now and its full
	 * health, each with thousands separators or a k/M ending, and the heart.
	 */
	private static final Pattern TAG = Pattern.compile("^\\[Lv\\d{1,3}]\\s+(?:\\S{1,3}\\s+)?Blaze\\s+([\\d,.]+[kKmM]?)\\s*/\\s*([\\d,.]+[kKmM]?)\\s*❤$");
	/** Blazes whose middle stands this far above or below the player's floor tell the room. */
	private static final double CLEAR_HEIGHT_BLOCKS = 6.0;
	private static final long THOUSAND = 1_000L;
	private static final long MILLION = 1_000_000L;

	/** A blaze of the room: the entity of its name tag, its full health and how high it stands. */
	public record Blaze(int id, long health, double y) {
	}

	/** How the order was decided. */
	public enum By {
		/** By the room's name. */
		ROOM,
		/** The room's name is not known: by where the blazes stand, above or below the player. */
		HEIGHT,
		/** Not known: the first blaze of both orders is named. */
		UNKNOWN
	}

	/**
	 * The blazes in the order to shoot them.
	 *
	 * @param highestFirst whether the highest health goes first (the lower room)
	 * @param other        with {@link By#UNKNOWN}: the first blaze of the other order, else null
	 */
	public record Order(List<Blaze> blazes, boolean highestFirst, By by, Blaze other) {
	}

	private BlazeOrder() {
	}

	/** @return a blaze's full health from its name tag, or -1 when the name is no blaze's tag */
	public static long health(String name) {
		if (name == null) {
			return -1;
		}
		Matcher matcher = TAG.matcher(plain(name));
		return matcher.matches() ? number(matcher.group(2)) : -1;
	}

	/** @return a number as a name tag writes it ("1,500", "1.5k", "2M"), or -1 */
	static long number(String text) {
		String digits = text.replace(",", "");
		long scale = 1;
		char last = Character.toLowerCase(digits.charAt(digits.length() - 1));
		if (last == 'k' || last == 'm') {
			scale = last == 'k' ? THOUSAND : MILLION;
			digits = digits.substring(0, digits.length() - 1);
		}
		try {
			double value = Double.parseDouble(digits);
			return value < 0 || value > Long.MAX_VALUE / (double) MILLION ? -1 : Math.round(value * scale);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** @return a name without the game's color codes, stripped */
	private static String plain(String name) {
		return TextColors.noColors(name).strip();
	}

	/** @return whether a room is one of the puzzle's two */
	public static boolean puzzleRoom(String room) {
		return LOWER_ROOM.equals(room) || HIGHER_ROOM.equals(room);
	}

	/**
	 * @param room    the room's name, or null or anything else while it is not known
	 * @param playerY the height of the player's feet
	 * @return the blazes in the order to shoot them, the next one first; null without any
	 */
	public static Order order(List<Blaze> found, String room, double playerY) {
		if (found.isEmpty()) {
			return null;
		}
		List<Blaze> blazes = new ArrayList<>(found);
		blazes.sort((a, b) -> Long.compare(a.health(), b.health()));
		By by = By.ROOM;
		boolean highestFirst;
		if (LOWER_ROOM.equals(room)) {
			highestFirst = true;
		} else if (HIGHER_ROOM.equals(room)) {
			highestFirst = false;
		} else {
			// the lower room's blazes stand in the pit under the way in, the higher room's above it
			double middle = 0;
			for (Blaze blaze : blazes) {
				middle += blaze.y();
			}
			middle /= blazes.size();
			highestFirst = middle < playerY;
			by = Math.abs(middle - playerY) >= CLEAR_HEIGHT_BLOCKS ? By.HEIGHT : By.UNKNOWN;
		}
		Blaze other = by == By.UNKNOWN && blazes.size() > 1 ? (highestFirst ? blazes.getFirst() : blazes.getLast()) : null;
		if (highestFirst) {
			java.util.Collections.reverse(blazes);
		}
		return new Order(List.copyOf(blazes), highestFirst, by, other);
	}
}
