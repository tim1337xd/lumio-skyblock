package dev.tim1337.lumio.skyblock.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pet's type from however the game writes its name: without color codes, level, favorite star, skin marker, skin counter or anything after the name, so that a pet is told by what it is and never by how it looks. Plain text, so it is unit tested.
 */
public final class PetNames {
	private static final Pattern COLOR = Pattern.compile("(?i)§[0-9a-fk-or]");
	private static final Pattern LEVEL = Pattern.compile("(?i)\\[\\s*Lvl\\s*\\d{1,4}\\s*]");
	/** A skin's counter before the name: "[324✦] Golden Dragon". */
	private static final Pattern SKIN_COUNTER = Pattern.compile("\\[\\s*\\d{1,9}\\s*[✦⭐★]\\s*]");
	private static final Pattern MARKS = Pattern.compile("[✦⭐★]");
	/** The name itself: letters, spaces and apostrophes, up to anything else. */
	private static final Pattern NAME = Pattern.compile("^[\\p{L}' -]*");

	private PetNames() {
	}

	/** @return the pet's type as the game names it ("Guardian" of "§6[Lvl 100] Guardian ✦"), empty if there is none */
	public static String type(String text) {
		if (text == null) {
			return "";
		}
		String plain = COLOR.matcher(text).replaceAll("");
		plain = LEVEL.matcher(plain).replaceAll(" ");
		plain = SKIN_COUNTER.matcher(plain).replaceAll(" ");
		plain = MARKS.matcher(plain).replaceAll(" ").strip();
		Matcher matcher = NAME.matcher(plain);
		return matcher.find() ? matcher.group().strip() : "";
	}

	/** @return whether a name as the game writes it is of a type of pet */
	public static boolean isType(String text, String type) {
		return type(text).equalsIgnoreCase(type);
	}
}
