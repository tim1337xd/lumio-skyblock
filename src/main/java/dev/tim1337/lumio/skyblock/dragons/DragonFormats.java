package dev.tim1337.lumio.skyblock.dragons;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the server says about a dragon fight, without colors. The forms are the ones SkyHanni and SkyblockAddons read (their test strings); they were not captured here, so a line with the dragon's sign that fits none is logged under the
 * debug switch. The server does not say a weight: it follows from the eyes, the place and the damage, by the rule those mods use. Plain text, so it is unit tested.
 */
public final class DragonFormats {
	/** The place and damage a player had. */
	public record Standing(int place, double damage) {
	}

	/** The sign in front of the fight's messages. */
	public static final String SIGN = "☬";
	/** A share of the top damage counts this much towards the weight, and so does an eye. */
	private static final double EYE_WEIGHT = 100.0;
	private static final int NO_DAMAGE_WEIGHT = 10;

	private static final Pattern PLACED = Pattern.compile("^☬ You placed a Summoning Eye!.{0,40}$");
	private static final String RECOVERED = "You recovered a Summoning Eye!";
	private static final Pattern SPAWNED = Pattern.compile("^☬ The ([A-Za-z]{1,12}) Dragon has spawned!$");
	private static final Pattern DOWN = Pattern.compile("^\\s*([A-Z]{1,12}) DRAGON DOWN!$");
	private static final Pattern POSITION = Pattern.compile("^\\s*Your Damage: ([\\d.,]{1,16}) (?:\\(NEW RECORD!\\) )?\\(Position #(\\d{1,4})\\)$");
	private static final Pattern TOP = Pattern.compile("^\\s*1st Damager - .{1,40} - ([\\d.,]{1,16})$");

	private DragonFormats() {
	}

	public static boolean eyePlaced(String line) {
		return PLACED.matcher(line.strip()).matches();
	}

	public static boolean eyeRecovered(String line) {
		return line.strip().equals(RECOVERED);
	}

	/** @return the kind of a dragon that has spawned, as "Superior", or null */
	public static String spawned(String line) {
		Matcher matcher = SPAWNED.matcher(line.strip());
		return matcher.matches() ? matcher.group(1) : null;
	}

	/** @return the kind of a dragon that went down, as "Superior", or null */
	public static String down(String line) {
		Matcher matcher = DOWN.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		String word = matcher.group(1);
		return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT);
	}

	/** @return the player's place and damage from the summary's line, or null */
	public static Standing standing(String line) {
		Matcher matcher = POSITION.matcher(line);
		return matcher.matches() ? new Standing(Integer.parseInt(matcher.group(2)), number(matcher.group(1))) : null;
	}

	/** @return the first place's damage from the summary's line, or -1 */
	public static double topDamage(String line) {
		Matcher matcher = TOP.matcher(line);
		return matcher.matches() ? number(matcher.group(1)) : -1.0;
	}

	private static double number(String text) {
		try {
			return Double.parseDouble(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	/** @return what a place is worth towards the weight */
	static int placeWeight(int place) {
		return switch (place) {
			case 1 -> 200;
			case 2 -> 175;
			case 3 -> 150;
			case 4 -> 125;
			case 5 -> 110;
			case 6, 7, 8 -> 100;
			case 9, 10 -> 90;
			case 11, 12 -> 80;
			default -> 70;
		};
	}

	/** @return the weight of a fight: its place, and 100 per eye and for the whole of the first place's damage */
	public static long weight(int eyes, int place, double top, double damage) {
		int forPlace = damage <= 0.0 ? NO_DAMAGE_WEIGHT : placeWeight(place);
		return Math.round(forPlace + EYE_WEIGHT * (eyes + damage / (top > 0.0 ? top : 1.0)));
	}

	/** @return the color of a kind of dragon as 0xRRGGBB, as the game's colors for it */
	public static int rgb(String kind) {
		return switch (kind) {
			case "Protector" -> 0xAAAAAA;
			case "Old" -> 0xFFFF55;
			case "Unstable" -> 0xAA00AA;
			case "Young" -> 0xFFFFFF;
			case "Strong" -> 0xFF5555;
			case "Wise" -> 0x55FFFF;
			case "Superior" -> 0xFFAA00;
			default -> 0xCCCCCC;
		};
	}
}
