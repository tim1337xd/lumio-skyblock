package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.ActionBarParts;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SkyBlock health and mana, parsed from the action bar. Hypixel writes stat symbols as resource pack glyphs (a heart is U+E010, mana U+E003) as
 * well as with regular font characters, and the text has the form {@code 1,234/1,500<symbol>}, so the symbol determines what a number is. Unknown
 * symbols are classified by their color.
 */
public final class SkyBlockStats {
	/** How long a value stays displayed after the server last reported it. */
	private static final long FRESH_MS = 6000L;
	/** A value pair followed by its symbol: {@code 1,234/1,500❤}. */
	private static final Pattern PAIR = Pattern.compile("([\\d,]+)/([\\d,]+)\\s*([\\uE000-\\uF8FF❤✎]) ?(?:Mana)?\\s*");
	/** Mana above the maximum, written as a number with its own symbol: {@code 12ʬ}. */
	private static final Pattern OVERFLOW = Pattern.compile("([\\d,]+)\\s*([\\uE017ʬ])\\s*");

	private static volatile int healthCurrent;
	private static volatile int healthMaximum;
	private static volatile long healthSeen;
	private static volatile int manaCurrent;
	private static volatile int manaMaximum;
	private static volatile int overflow;
	private static volatile int vitalityCurrent;
	private static volatile int vitalityMaximum;
	private static volatile long vitalitySeen;
	private static volatile int fuelCurrent;
	private static volatile int fuelMaximum;
	private static volatile long fuelSeen;
	private static volatile int defenseCurrent;
	private static volatile long defenseSeen;
	/** The defense in the action bar: the first number of its part, at most nine digits long. */
	private static final Pattern DEFENSE_NUMBER = Pattern.compile("\\d[\\d,]{0,10}");
	/** Fuel of the held drill, shown while mining: {@code 91,118/100k Drill Fuel}. */
	private static final Pattern FUEL = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)\\s*Drill Fuel");
	private static volatile String lastRaw = "";
	private static int loggedRaw;
	/** The reverse order, symbol first: {@code ❤ 1,234/1,500}. */
	private static final Pattern SIGN_FIRST = Pattern.compile("([\\uE000-\\uF8FF❤✎])\\s*([\\d,]+)/([\\d,]+)");

	private SkyBlockStats() {
	}

	/** @return the last seen action bar with every non-plain character written as a code point, for diagnosing what a server sends */
	public static String lastRaw() {
		return lastRaw.isEmpty() ? "none yet" : lastRaw;
	}

	private static String escaped(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < 127 && c >= 32) {
				out.append(c);
			} else {
				out.append(String.format("\\u%04X", (int) c));
			}
		}
		return out.toString();
	}

	/** @return whether a text contains both the health and mana symbols, which no other server's text has together */
	public static boolean looksLikeSkyBlock(String text) {
		boolean health = text.indexOf('❤') >= 0 || text.indexOf('\uE010') >= 0;
		boolean mana = text.indexOf('✎') >= 0 || text.indexOf('\uE003') >= 0;
		return health && mana;
	}

	public static boolean healthFresh() {
		return healthMaximum > 0 && System.currentTimeMillis() - healthSeen < FRESH_MS;
	}

	/** @return whether the vitality (a pair with its own symbol: {@code 126/126}) was reported recently */
	public static boolean vitalityFresh() {
		return vitalityMaximum > 0 && System.currentTimeMillis() - vitalitySeen < FRESH_MS;
	}

	public static int vitality() {
		return vitalityCurrent;
	}

	public static int maxVitality() {
		return vitalityMaximum;
	}

	/** @return whether the defense was reported recently */
	public static boolean defenseFresh() {
		return System.currentTimeMillis() - defenseSeen < FRESH_MS;
	}

	public static int defense() {
		return defenseCurrent;
	}

	public static boolean fuelFresh() {
		return fuelMaximum > 0 && System.currentTimeMillis() - fuelSeen < FRESH_MS;
	}

	public static int fuel() {
		return fuelCurrent;
	}

	public static int maxFuel() {
		return fuelMaximum;
	}

	/** @return a number as SkyBlock abbreviates it: 91,118, 100k, 1.5m */
	private static int shortNumber(String text) {
		String plain = text.replace(",", "").strip();
		double factor = 1;
		char last = Character.toLowerCase(plain.charAt(plain.length() - 1));
		if (last == 'k' || last == 'm') {
			factor = last == 'k' ? 1_000 : 1_000_000;
			plain = plain.substring(0, plain.length() - 1);
		}
		return (int) Math.round(Double.parseDouble(plain) * factor);
	}

	/**
	 * @return whether the mana bar shows: from the first mana seen for as long as the player stays on SkyBlock. The server often shows other
	 * things in the mana slot (skill XP while mining, drill fuel, an ability, "NOT ENOUGH MANA"), for minutes while mining: mana is then only not
	 * shown, not gone, so the last value stays until it shows again. Leaving SkyBlock forgets it.
	 */
	public static boolean manaFresh() {
		if (manaMaximum <= 0) {
			return false;
		}
		if (!SkyBlock.inSkyBlock()) {
			manaMaximum = 0;
			return false;
		}
		return true;
	}

	public static int health() {
		return healthCurrent;
	}

	public static int maxHealth() {
		return healthMaximum;
	}

	public static int mana() {
		return manaCurrent;
	}

	public static int maxMana() {
		return manaMaximum;
	}

	/** @return the overflow mana, or 0 */
	public static int overflowMana() {
		return manaFresh() ? overflow : 0;
	}

	/** Reads the defense from its part of the action bar ("132❈ Defense"); a part without a sane number is ignored. */
	private static void readDefense(String part, long now) {
		Matcher digits = DEFENSE_NUMBER.matcher(part);
		if (digits.find()) {
			String plain = digits.group().replace(",", "");
			if (plain.length() <= 9) {
				defenseCurrent = Integer.parseInt(plain);
				defenseSeen = now;
			}
		}
	}

	private static int number(String text) {
		return Integer.parseInt(text.replace(",", ""));
	}

	/** @return 1 for health, 2 for mana, 3 for vitality, 0 for anything else; unknown symbols are classified by the color of their text */
	private static int kind(char sign, Style style) {
		if (sign == (char) 0xE028) {
			return 3; // vitality
		}
		if (sign == '❤' || sign == '') {
			return 1;
		}
		if (sign == '✎' || sign == '') {
			return 2;
		}
		if (sign >= '' && style.getColor() != null) {
			int rgb = style.getColor().getValue();
			// red and gold are health (gold is health with absorption), aqua is mana
			if (rgb == 0xFF5555 || rgb == 0xFFAA00) {
				return 1;
			}
			if (rgb == 0x55FFFF) {
				return 2;
			}
		}
		return 0;
	}

	/** Bit flags for what is removed from the action bar. */
	public static final int REMOVE_HEALTH = ActionBarParts.HEALTH;
	public static final int REMOVE_MANA = ActionBarParts.MANA;
	public static final int REMOVE_DEFENSE = ActionBarParts.DEFENSE;
	public static final int REMOVE_OVERFLOW = ActionBarParts.OVERFLOW;
	/** Every other number with a symbol (soulflow, drill fuel, whatever SkyBlock adds): one number, or two with a slash, then a symbol. */
	public static final int REMOVE_OTHER = ActionBarParts.OTHER;
	/** The stat with symbol U+E028 (126/126) that the server writes between mana and the end: vitality. */
	public static final int REMOVE_VITALITY = ActionBarParts.VITALITY;
	/** The player's location: a location symbol and its name, shown in place of the defense while it changes. */
	public static final int REMOVE_LOCATION = ActionBarParts.LOCATION;
	/** Drill fuel: {@code 91,118/100k Drill Fuel}. */
	public static final int REMOVE_FUEL = ActionBarParts.FUEL;
	public static final int REMOVE_USUAL = REMOVE_HEALTH | REMOVE_MANA | REMOVE_DEFENSE | REMOVE_OVERFLOW;

	private static volatile String lastParts = "";

	/** @return the current action bar split into its parts, each with its kind, so the menu can offer which parts to remove */
	public static String lastParts() {
		return lastParts.isEmpty() ? "nothing yet" : lastParts;
	}

	/** @return the text without color codes (§ plus a letter) and resource pack glyphs: only the written content */
	private static String plainOf(String part) {
		return PACK_SIGNS.matcher(TextColors.noColors(part)).replaceAll("").strip();
	}

	/** The server's private-use font glyphs, compiled once: the action bar arrives several times per second, in many parts. */
	private static final Pattern PACK_SIGNS = Pattern.compile("[\\uE000-\\uF8FF]");

	private static String nameOf(int kind) {
		return switch (kind) {
			case REMOVE_HEALTH -> "Health";
			case REMOVE_MANA -> "Mana";
			case REMOVE_DEFENSE -> "Defense";
			case REMOVE_OVERFLOW -> "Overflow";
			case REMOVE_OTHER -> "Other";
			case REMOVE_VITALITY -> "Vitality";
			case REMOVE_LOCATION -> "Location";
			case REMOVE_FUEL -> "Drill fuel";
			default -> "Text";
		};
	}

	/** @return the message with inline color codes (§ plus a letter) converted into the colors and styles they represent */
	public static Component restyled(Component message) {
		if (message == null || message.getString().indexOf('\u00A7') < 0) {
			return message;
		}
		MutableComponent out = Component.empty();
		message.visit((style, text) -> {
			Style current = style;
			StringBuilder piece = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if (c == '\u00A7' && i + 1 < text.length()) {
					if (!piece.isEmpty()) {
						out.append(Component.literal(piece.toString()).withStyle(current));
						piece.setLength(0);
					}
					ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
					if (format != null) {
						current = format == ChatFormatting.RESET ? style : current.applyFormat(format);
					}
					i++;
				} else {
					piece.append(c);
				}
			}
			if (!piece.isEmpty()) {
				out.append(Component.literal(piece.toString()).withStyle(current));
			}
			return Optional.<Object>empty();
		}, Style.EMPTY);
		return out;
	}

	/** Patterns for {@link #classify} and {@link #read}, compiled once: every action bar is parsed twice, several times per second. */
	private static final Pattern WORD = Pattern.compile("[A-Za-z]{4,}");
	private static final Pattern DIGIT = Pattern.compile("\\d");
	private static final Pattern LONE_NUMBER = Pattern.compile("^\\W*[\\uE000-\\uF8FF]?\\s*[\\d,.]+\\s*\\S?$");
	private static final Pattern TOKEN = Pattern.compile("\\S+");

	/** @return the kind of an action bar part as one of the {@code REMOVE_} bits, or 0 for words, which are never removed */
	private static int classify(String part, Style firstStyle) {
		boolean words = WORD.matcher(part.replace("Mana", "").replace("Defense", "")).find();
		if (part.contains("Drill Fuel")) {
			return REMOVE_FUEL; // words, but a stat with its own bar
		}
		if (part.indexOf('\uE067') >= 0) {
			return REMOVE_LOCATION; // a location symbol and its name
		}
		if (part.indexOf('\uE028') >= 0) {
			return REMOVE_VITALITY;
		}
		if (part.indexOf('\u2764') >= 0 || part.indexOf('\uE010') >= 0) {
			return REMOVE_HEALTH;
		}
		if (part.indexOf('\u02AC') >= 0 || part.indexOf('\uE017') >= 0) {
			return REMOVE_OVERFLOW;
		}
		if (part.indexOf('\u270E') >= 0 || part.indexOf('\uE003') >= 0 || part.contains("Mana")) {
			return REMOVE_MANA;
		}
		if (part.indexOf('\u2748') >= 0 || part.indexOf('\uE008') >= 0 || part.contains("Defense")) {
			return REMOVE_DEFENSE;
		}
		if (words || !DIGIT.matcher(part).find()) {
			return 0;
		}
		int rgb = firstStyle != null && firstStyle.getColor() != null ? firstStyle.getColor().getValue() : -1;
		if (rgb == 0x55FFFF || rgb == 0x00AAAA) {
			return REMOVE_MANA; // aqua is the mana color: whatever remains of it goes too
		}
		if (rgb == 0xFF5555 || rgb == 0xFFAA00) {
			return REMOVE_HEALTH;
		}
		if (rgb == 0x55FF55 && part.indexOf('/') < 0) {
			return REMOVE_DEFENSE; // green number: defense
		}
		// a number with a symbol and no slash is the defense; a pair or anything else with numbers is another stat
		return part.indexOf('/') < 0 && LONE_NUMBER.matcher(part).find() ? REMOVE_DEFENSE : REMOVE_OTHER;
	}

	private static boolean overlaps(List<int[]> ranges, int start, int end) {
		for (int[] range : ranges) {
			if (start < range[1] && end > range[0]) {
				return true;
			}
		}
		return false;
	}

	public static Component read(Component message, boolean wantHealth, boolean wantMana, boolean takeOut) {
		return read(message, wantHealth, wantMana, takeOut, REMOVE_USUAL);
	}

	private static Integer colorAt(Style[] styleAt, int index) {
		if (index < 0 || index >= styleAt.length || styleAt[index] == null || styleAt[index].getColor() == null) {
			return null;
		}
		return styleAt[index].getColor().getValue();
	}

	/**
	 * Parses the values from the text and removes the parsed parts if requested.
	 *
	 * @return the text to display
	 */
	public static Component read(Component original, boolean wantHealth, boolean wantMana, boolean takeOut, int remove) {
		// convert the server's inline color codes into styles first, so no code letters remain in the displayed text
		Component message = restyled(original);
		List<Style> styles = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		StringBuilder all = new StringBuilder();
		message.visit((style, text) -> {
			styles.add(style);
			texts.add(text);
			all.append(text);
			return Optional.<Object>empty();
		}, Style.EMPTY);
		String escapedNow = escaped(all.toString());
		if (!escapedNow.equals(lastRaw)) {
			// log the raw action bar when it changes, so unrecognized parts can be found in the log
			if (Debug.on() && ++loggedRaw <= 400) {
				Debug.log("Text above the hotbar: {}", escapedNow);
			}
		}
		lastRaw = escapedNow;
		// the style of every character, since symbols are classified by their text color
		Style[] styleAt = new Style[all.length()];
		int position = 0;
		for (int i = 0; i < texts.size(); i++) {
			for (int c = 0; c < texts.get(i).length(); c++) {
				styleAt[position++] = styles.get(i);
			}
		}

		List<int[]> taken = new ArrayList<>();
		long now = System.currentTimeMillis();
		boolean healthRead = false;
		boolean manaRead = false;
		boolean vitalityRead = false;
		Matcher matcher = PAIR.matcher(all);
		while (matcher.find()) {
			int kind = kind(matcher.group(3).charAt(0), styleAt[matcher.start(3)]);
			try {
				if (kind == 3 && !vitalityRead) {
					vitalityCurrent = number(matcher.group(1));
					vitalityMaximum = number(matcher.group(2));
					vitalitySeen = now;
					vitalityRead = true;
				} else if (kind == 1 && wantHealth && !healthRead) {
					healthCurrent = number(matcher.group(1));
					healthMaximum = number(matcher.group(2));
					healthSeen = now;
					healthRead = true;
					if ((remove & REMOVE_HEALTH) != 0) { taken.add(new int[]{matcher.start(), matcher.end()}); }
				} else if (kind == 2 && wantMana && !manaRead) {
					manaCurrent = number(matcher.group(1));
					manaMaximum = number(matcher.group(2));
					manaRead = true;
					if ((remove & REMOVE_MANA) != 0) { taken.add(new int[]{matcher.start(), matcher.end()}); }
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (!healthRead && !manaRead) {
			// the reverse order: symbol, then numbers
			Matcher first = SIGN_FIRST.matcher(all);
			while (first.find()) {
				int kind = kind(first.group(1).charAt(0), styleAt[first.start(1)]);
				try {
					if (kind == 1 && wantHealth && !healthRead) {
						healthCurrent = number(first.group(2));
						healthMaximum = number(first.group(3));
						healthSeen = now;
						healthRead = true;
						if ((remove & REMOVE_HEALTH) != 0) { taken.add(new int[]{first.start(), first.end()}); }
					} else if (kind == 2 && wantMana && !manaRead) {
						manaCurrent = number(first.group(2));
						manaMaximum = number(first.group(3));
						manaRead = true;
						if ((remove & REMOVE_MANA) != 0) { taken.add(new int[]{first.start(), first.end()}); }
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		if (healthRead || manaRead) {
			SkyBlock.evidence(30_000);
		}
		Matcher fuel = FUEL.matcher(all);
		if (fuel.find()) {
			try {
				fuelCurrent = shortNumber(fuel.group(1));
				fuelMaximum = shortNumber(fuel.group(2));
				fuelSeen = now;
			} catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {
			}
		}
		if (wantMana && manaRead) {
			Matcher over = OVERFLOW.matcher(all);
			int found = 0;
			while (over.find()) {
				try {
					found = number(over.group(1));
					if ((remove & REMOVE_OVERFLOW) != 0) { taken.add(new int[]{over.start(), over.end()}); }
				} catch (NumberFormatException ignored) {
				}
			}
			overflow = found;
		}
		{
			// Split the text into parts: separated by two or more spaces, or by a color change. Every part has a kind (from its symbol, words and color),
			// and a part of a kind to remove is removed entirely, so nothing of it (a symbol, a slash) is left behind.
			Matcher token = TOKEN.matcher(all);
			StringBuilder parts = new StringBuilder();
			int groupStart = -1;
			int groupEnd = -1;
			int previousEnd = -1;
			List<int[]> groups = new ArrayList<>();
			while (token.find()) {
				boolean joins = groupStart >= 0 && token.start() - previousEnd < 2
					&& Objects.equals(colorAt(styleAt, previousEnd - 1), colorAt(styleAt, token.start()));
				if (!joins) {
					if (groupStart >= 0) {
						groups.add(new int[]{groupStart, groupEnd});
					}
					groupStart = token.start();
				}
				groupEnd = token.end();
				previousEnd = token.end();
			}
			if (groupStart >= 0) {
				groups.add(new int[]{groupStart, groupEnd});
			}
			for (int[] group : groups) {
				String part = all.substring(group[0], group[1]);
				int kind = classify(part, styleAt[group[0]]);
				if (kind == REMOVE_DEFENSE) {
					readDefense(part, now);
				}
				String plain = plainOf(part);
				if (!plain.isEmpty()) {
					parts.append(parts.isEmpty() ? "" : "   |   ").append(nameOf(kind)).append(": ").append(plain);
				}
				if (takeOut && kind != 0 && (remove & kind) != 0 && !overlaps(taken, group[0], group[1])) {
					taken.add(new int[]{group[0], group[1]});
				}
			}
			lastParts = parts.toString();
		}
		if (taken.isEmpty() || !takeOut) {
			return message;
		}
		MutableComponent rebuilt = Component.empty();
		position = 0;
		// Tidy up the rest: spaces that separated stats and now border nothing are dropped at the start and end, and runs of spaces are capped at
		// three, the usual separator.
		int pending = 0;
		Style pendingStyle = Style.EMPTY;
		boolean any = false;
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			StringBuilder kept = new StringBuilder();
			for (int c = 0; c < text.length(); c++) {
				int at = position + c;
				boolean inside = false;
				for (int[] range : taken) {
					if (at >= range[0] && at < range[1]) {
						inside = true;
						break;
					}
				}
				if (inside) {
					continue;
				}
				char ch = text.charAt(c);
				if (ch == ' ') {
					pending++;
					pendingStyle = styles.get(i);
				} else {
					if (any && pending > 0) {
						if (!kept.isEmpty()) {
							rebuilt.append(Component.literal(kept.toString()).withStyle(styles.get(i)));
							kept.setLength(0);
						}
						rebuilt.append(Component.literal(" ".repeat(Math.min(pending, 3))).withStyle(pendingStyle));
					}
					pending = 0;
					any = true;
					kept.append(ch);
				}
			}
			position += text.length();
			if (!kept.isEmpty()) {
				rebuilt.append(Component.literal(kept.toString()).withStyle(styles.get(i)));
			}
		}
		return rebuilt;
	}
}
